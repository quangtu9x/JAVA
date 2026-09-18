package com.td.application.vbcddh;

import com.td.application.common.models.Result;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.vbcddh.VbcddhDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BL-250 (VBCDDH Core), bổ sung 08/09/2026 (yêu cầu người dùng "tạo bảng riêng, sử dụng dynamic
 * fields"): tương đương {@code com.td.application.documents.AddDynamicDocumentUseCase} (BL-101)
 * nhưng lưu vào bảng vật lý ĐỘC LẬP {@code vbcddh_documents} (xem {@link VbcddhDocument}) thay vì
 * {@code documents} dùng chung — dùng CHUNG bởi mọi use case của "cụm vbcddh":
 * {@link PhanCongNhiemVuUseCase} (tạo {@code form=nhiemvu}) và {@link CapNhatTienDoUseCase} (tạo
 * {@code form=tiendonhiemvu}/{@code ketquaxulynhiemvu}, {@code document_type=vbcddh_res}) — tránh
 * viết 2 lần cùng 1 logic build {@code dynamic_fields}/cache/log. Đúng tiền lệ
 * {@code com.td.application.vbdi.AddVbdiDynamicDocumentUseCase} (BL-220) — KHÔNG tích hợp
 * {@code DocumentNumberingService} (khác vbdi): {@code nhiemvu}/{@code tiendonhiemvu} không có khái
 * niệm số văn bản tự sinh, BL-104 chưa cấu hình numbering cho {@code document_type=vbcddh}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AddVbcddhDynamicDocumentUseCase {

    private static final String DEFAULT_TITLE = "Vbcddh";
    private static final String DEFAULT_STATUS = "DRAFT";

    private final VbcddhDocumentRepository vbcddhDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;

    public Result<UUID> execute(AddDynamicDocumentRequest request) {
        try {
            String title = (request.getTitle() == null || request.getTitle().isBlank())
                ? DEFAULT_TITLE
                : request.getTitle().trim();
            String status = (request.getStatus() == null || request.getStatus().isBlank())
                ? DEFAULT_STATUS
                : request.getStatus().trim();

            String dynamicFieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(
                request.getFields(), request.getDateFields(), request.getNumberFields(), request.getRichtextFields());

            var document = new VbcddhDocument(title, request.getDocumentType(), status, null, null, null, null)
                .replaceDynamicFields(dynamicFieldsJson);

            var saved = vbcddhDocumentRepository.save(document);
            documentCacheService.evictAllListCaches();
            documentLogService.logCreate(saved.getId(), buildSnapshot(saved));
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Tạo tài liệu vbcddh (dynamic) thất bại", ex);
            return Result.failure("Tạo tài liệu vbcddh thất bại: " + ex.getMessage());
        }
    }

    private Map<String, Object> buildSnapshot(VbcddhDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }
}
