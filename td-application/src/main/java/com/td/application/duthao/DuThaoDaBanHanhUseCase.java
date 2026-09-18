package com.td.application.duthao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.duthao.DuThaoDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Yêu cầu người dùng 28/08/2026 ("port lại XuLyDuThao.java, class này có các chức năng tương ứng ở
 * button của văn bản đi"): "cập nhật trạng thái dự thảo" — đối chiếu trực tiếp
 * {@code vn.com.tandan.xsp.services.vbdi.XuLyDuThao#ChuyenTrangThaiDuThaoDaBanHanh()} (dòng
 * 120-175) — đánh dấu dự thảo cha ĐÃ HOÀN TẤT vì văn bản đi sinh ra từ nó đã được ban hành CHÍNH
 * THỨC với số/ký hiệu cụ thể. Trước bản vá này FE vẫn gọi Domino
 * {@code qlvb/vbdi/dabanhanhduthao?databasetype=duthao&id=...} — với dự thảo Java-native (UUID
 * Postgres), Domino không có bản ghi tương ứng nên gọi luôn thất bại âm thầm (dự thảo cha không
 * bao giờ được đánh dấu hoàn tất).
 *
 * <p><b>Mutation ĐÚNG theo Domino thật</b> (dòng 140-143): field động {@code documentout_id}
 * (id văn bản đi), {@code documentout_code} (số/ký hiệu), {@code status_pre} (backup trạng thái
 * trước khi ghi đè) — VÀ cột tĩnh {@code status} → {@link DuThaoStatus#DOCUMENT_OUT_FINALIZED}
 * ("8"). KHÔNG tạo vết {@code draft_process} (khác {@link ChuyenThanhVanBanDiUseCase}) — đúng bằng
 * chứng Domino: hàm gốc chỉ sửa document dự thảo, không tạo document con nào.</p>
 *
 * <p><b>CHƯA port</b> (có bằng chứng rõ, không phải bỏ sót — cùng nguyên tắc đã áp dụng cho
 * {@link ChuyenThanhVanBanDiUseCase}): {@code TDInit.setViewError}/{@code sendSocketNotSave} (làm
 * mới cache view + thông báo socket) — hạ tầng socket/realtime CHƯA tồn tại trong {@code JAVA/};
 * {@code elastic.CreateOrUpdateDocument(...)} (Elasticsearch) — chưa use case duthao/vbdi nào khác
 * gọi bước này.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DuThaoDaBanHanhUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản dự thảo";
    private static final String DOCUMENT_OUT_ID_FIELD = "documentout_id";
    private static final String DOCUMENT_OUT_CODE_FIELD = "documentout_code";
    private static final String STATUS_PRE_FIELD = "status_pre";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DuThaoDocumentRepository duThaoDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;

    public Result<UUID> execute(DuThaoDaBanHanhRequest request) {
        if (request == null || request.getId() == null) {
            return Result.failure("Thiếu id dự thảo cần cập nhật");
        }
        var draftOptional = duThaoDocumentRepository.findById(request.getId());
        if (draftOptional.isEmpty() || draftOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var draft = draftOptional.get();
        if (!ChuyenThanhVanBanDiUseCase.DOCUMENT_TYPE.equalsIgnoreCase(draft.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        var before = buildSnapshot(draft);
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson()));
        fields.put(STATUS_PRE_FIELD, draft.getStatus());
        fields.put(DOCUMENT_OUT_ID_FIELD, request.getVbdiId().toString());
        if (request.getVbdiCode() != null && !request.getVbdiCode().isBlank()) {
            fields.put(DOCUMENT_OUT_CODE_FIELD, request.getVbdiCode().trim());
        }

        draft.replaceDynamicFields(toJson(fields));
        draft.update(null, null, DuThaoStatus.DOCUMENT_OUT_FINALIZED, null, null, null, null);
        var saved = duThaoDocumentRepository.save(draft);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
        log.info("Danh dau du thao {} da ban hanh (vbdi {})", saved.getId(), request.getVbdiId());
        return Result.success(saved.getId());
    }

    private Map<String, Object> buildSnapshot(DuThaoDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
