package com.td.application.duthao;

import com.td.application.common.models.Result;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AuthorAclFieldsService;
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
 * BL-230 (DuThao Core), yêu cầu người dùng 24/08/2026 ("tạo bảng riêng, để độc lập dữ liệu"):
 * tương đương {@link com.td.application.documents.AddDynamicDocumentUseCase} (BL-101) nhưng lưu
 * vào bảng vật lý ĐỘC LẬP {@code duthao_documents} (xem {@link DuThaoDocument}) thay vì
 * {@code documents} dùng chung.
 *
 * <p><b>Cập nhật 25/08/2026 (yêu cầu người dùng: "tách draft_process sang bảng riêng")</b>: từ
 * trước đây dùng chung cho 2 nơi ({@code duthao} + {@code draft_process}), nay CHỈ còn phục vụ
 * {@link TaoDuThaoUseCase} (document chính, {@code document_type=duthao}) — vết xử lý
 * {@code draft_process} chuyển sang {@link AddDraftProcessDocumentUseCase}/bảng
 * {@code draft_process_documents} (migration V1.0.30); KHÔNG còn khái niệm document con
 * {@code duthao_res} (bản ghi ý kiến phối hợp thực chất là {@code draft_process} — xem
 * {@link XinYKienPhoiHopUseCase}).</p>
 *
 * <p>KHÔNG tích hợp {@code DocumentNumberingService} (khác bản BL-101 gốc) — chưa có bằng chứng
 * nào cho thấy {@code duthao} cần sinh số tự động (không giống {@code vbdi}), thêm vào sẽ là suy
 * diễn không cần thiết.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AddDuThaoDynamicDocumentUseCase {

    private static final String DEFAULT_TITLE = "Dự thảo";

    private final DuThaoDocumentRepository duThaoDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;
    private final AuthorAclFieldsService authorAclFieldsService;

    public Result<UUID> execute(AddDynamicDocumentRequest request) {
        try {
            String title = (request.getTitle() == null || request.getTitle().isBlank())
                ? DEFAULT_TITLE
                : request.getTitle().trim();
            String status = (request.getStatus() == null || request.getStatus().isBlank())
                ? DuThaoStatus.DRAFTING
                : request.getStatus().trim();

            authorAclFieldsService.applyOnCreate(request.getDocumentType(), request.getFields());

            String dynamicFieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(
                request.getFields(), request.getDateFields(), request.getNumberFields(), request.getRichtextFields());

            var document = new DuThaoDocument(title, request.getDocumentType(), status, null, null, null, null)
                .replaceDynamicFields(dynamicFieldsJson);

            var saved = duThaoDocumentRepository.save(document);
            documentCacheService.evictAllListCaches();
            documentLogService.logCreate(saved.getId(), buildSnapshot(saved));
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Tạo tài liệu dự thảo (dynamic) thất bại", ex);
            return Result.failure("Tạo tài liệu dự thảo thất bại: " + ex.getMessage());
        }
    }

    private Map<String, Object> buildSnapshot(DuThaoDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }
}
