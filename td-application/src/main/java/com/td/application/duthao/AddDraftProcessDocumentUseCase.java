package com.td.application.duthao;

import com.td.application.common.models.Result;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.duthao.DraftProcessDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DT-007, yêu cầu người dùng 25/08/2026 ("tách draft_process sang bảng riêng"): tương đương
 * {@link AddDuThaoDynamicDocumentUseCase} nhưng lưu vào bảng vật lý ĐỘC LẬP
 * {@code draft_process_documents} (xem {@link DraftProcessDocument}) — writer DUY NHẤT của vết
 * xử lý, dùng bởi {@link XuLyDuThaoUseCase} cho mỗi action workflow.
 *
 * <p>Ép cứng {@code document_type='draft_process'} ở TẦNG NÀY (caller không tự đặt được): bảng
 * này chỉ chứa đúng 1 loại document, không cho phép ghi nhầm type khác (khác bản cũ lưu chung
 * duthao_documents vốn phân biệt bằng tham số caller).</p>
 *
 * <p>Khác bản cha 2 điểm có chủ đích: (1) KHÔNG đụng {@code DocumentCacheService} — vết xử lý là
 * append-only, chưa có view cache danh sách nào đọc nó (QTXL đọc trực tiếp qua repository khi
 * port); (2) KHÔNG tích hợp numbering (giữ nguyên lý do của bản cha).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AddDraftProcessDocumentUseCase {

    /** Loại document DUY NHẤT được phép tồn tại trong bảng draft_process_documents. */
    public static final String DOCUMENT_TYPE = "draft_process";

    private static final String DEFAULT_TITLE = "Quá trình xử lý dự thảo";

    private final DraftProcessDocumentRepository draftProcessDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentLogService documentLogService;

    public Result<UUID> execute(AddDynamicDocumentRequest request) {
        try {
            String title = (request.getTitle() == null || request.getTitle().isBlank())
                ? DEFAULT_TITLE
                : request.getTitle().trim();
            String status = (request.getStatus() == null || request.getStatus().isBlank())
                ? DuThaoStatus.DRAFTING
                : request.getStatus().trim();

            String dynamicFieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(
                request.getFields(), request.getDateFields(), request.getNumberFields(), request.getRichtextFields());

            var document = new DraftProcessDocument(title, DOCUMENT_TYPE, status, null, null, null, null)
                .replaceDynamicFields(dynamicFieldsJson);

            var saved = draftProcessDocumentRepository.save(document);
            documentLogService.logCreate(saved.getId(), buildSnapshot(saved));
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Tạo vết xử lý dự thảo (draft_process) thất bại", ex);
            return Result.failure("Tạo vết xử lý dự thảo thất bại: " + ex.getMessage());
        }
    }

    private Map<String, Object> buildSnapshot(DraftProcessDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }
}