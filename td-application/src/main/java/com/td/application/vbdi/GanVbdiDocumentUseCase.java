package com.td.application.vbdi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.vbdi.VbdiDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 29/08/2026 (yêu cầu người dùng "gửi văn bản thay thế ... phân tích và thực hiện port luôn", tham
 * khảo {@code docs/analysis/07-phan-tich-port-senddocument-vbdi-qlvb.md} addendum 29/08/2026 (4)):
 * "Gắn VB đi" — liên kết văn bản B (đang soạn, thường đánh dấu {@code document_replace_retrieve="4"}
 * "Thay thế" hoặc {@code "1"} "Thu hồi" trên chính field động của B, KHÔNG xử lý ở use case này —
 * xem javadoc {@link SoanThaoVbdiUseCase}/{@link HieuChinhVbdiUseCase}, field này đã tự động được
 * Dynamic Data Engine BL-101 lưu chung {@code dynamic_fields} như mọi field khác, không cần code
 * riêng) tới văn bản A (văn bản BỊ thay thế/thu hồi) — port {@code DocumentOutProcesses.java#
 * add_Document_Revoke()} (Domino, {@code qlvb/vbdi/adddocrevoke}).
 *
 * <p><b>Khác biệt CHỦ ĐÍCH với Domino gốc</b>: Domino ghi 1 JSON array (field {@code documentOutRevoke})
 * chứa UNID của A KÈM snapshot metadata (số hiệu, trích yếu, ngày ban hành, đơn vị ban hành, cả 4
 * nhóm log nơi nhận) — dữ liệu SAO CHÉP có thể lạc hậu nếu A bị sửa sau khi liên kết. Ở đây CHỈ lưu
 * {@value #REPLACES_FIELD} = UUID của A (string) trong {@code dynamic_fields} của B (field động
 * bình thường, không entity/cột riêng — 1 tham chiếu scalar không cần schema riêng) — khi cần hiển
 * thị, FE tự gọi lại {@code XemChiTiet} của A để lấy dữ liệu MỚI NHẤT (qua {@code xemChiTietVbdi()}
 * đã có sẵn), không có nguy cơ lạc hậu như snapshot Domino.</p>
 *
 * <p><b>Giới hạn</b>: CHỈ 1 liên kết/văn bản B (đúng giới hạn FE "Chỉ được phép gắn 1 văn bản") — vì
 * lưu 1 field scalar (không phải danh sách), gọi lại API sẽ THAY liên kết cũ. A KHÔNG bị ghi ngược
 * field nào (đúng Domino gốc — liên kết chỉ 1 chiều, lưu trên B).</p>
 *
 * <p><b>Guard</b>: B và A (nếu có) phải tồn tại, đúng {@code documentType="vbdi"}, chưa xoá mềm; B
 * không được tự gắn với chính nó. KHÔNG guard trạng thái ISSUED (Domino gốc không yêu cầu — có thể
 * gắn khi B còn đang soạn thảo, trước khi ban hành).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GanVbdiDocumentUseCase {

    /** Tên field động lưu UUID (string) của văn bản A trên văn bản B — xem javadoc lớp. */
    public static final String REPLACES_FIELD = "replaces_document_id";

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đi";
    private static final String NOT_FOUND_LINKED_MESSAGE = "Không tìm thấy văn bản đi cần gắn";
    private static final String SELF_LINK_MESSAGE = "Không thể gắn văn bản với chính nó";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;

    public Result<UUID> execute(GanVbdiDocumentRequest request) {
        if (request == null || request.getDocumentId() == null) {
            return Result.failure("Thiếu documentId cần gắn văn bản");
        }
        var documentOptional = vbdiDocumentRepository.findById(request.getDocumentId());
        if (documentOptional.isEmpty() || !isUsableVbdiDocument(documentOptional.get())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var document = documentOptional.get();

        UUID replacesDocumentId = request.getReplacesDocumentId();
        if (replacesDocumentId != null) {
            if (replacesDocumentId.equals(document.getId())) {
                return Result.failure(SELF_LINK_MESSAGE);
            }
            var linkedOptional = vbdiDocumentRepository.findById(replacesDocumentId);
            if (linkedOptional.isEmpty() || !isUsableVbdiDocument(linkedOptional.get())) {
                return Result.failure(NOT_FOUND_LINKED_MESSAGE);
            }
        }

        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        Object previousLinkedId = fields.get(REPLACES_FIELD);
        if (replacesDocumentId == null) {
            fields.remove(REPLACES_FIELD);
        } else {
            fields.put(REPLACES_FIELD, replacesDocumentId.toString());
        }
        document.replaceDynamicFields(toJson(fields));

        var saved = vbdiDocumentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(),
            Map.of(REPLACES_FIELD, previousLinkedId == null ? "" : previousLinkedId),
            Map.of(REPLACES_FIELD, replacesDocumentId == null ? "" : replacesDocumentId.toString()));
        log.info("Gan van ban di {} -> {}", saved.getId(), replacesDocumentId);
        return Result.success(saved.getId());
    }

    private boolean isUsableVbdiDocument(VbdiDocument document) {
        return !document.isDeleted()
            && SoanThaoVbdiUseCase.DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType());
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
