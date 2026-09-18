package com.td.application.hosocv;

import com.td.application.common.models.Result;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.hosocv.HoSoCVDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mở rộng BL-240 (HoSoCV Core), yêu cầu người dùng ("tạo bảng mới, sử dụng jsonb (dynamic field)
 * như các module đã thực hiện"): tương đương {@link com.td.application.documents.AddDynamicDocumentUseCase}
 * (BL-101) nhưng lưu vào bảng vật lý ĐỘC LẬP {@code hosocv_documents} (xem {@link HoSoCVDocument})
 * thay vì {@code documents} dùng chung — đúng tiền lệ {@code AddDuThaoDynamicDocumentUseCase}.
 *
 * <p>KHÔNG tích hợp {@code DocumentNumberingService} — hosocv không có bằng chứng nào cho thấy cần
 * sinh số tự động (ký hiệu hồ sơ {@code name} do FE tự sinh, xem javadoc {@link TaoHoSoCVRequest}).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AddHoSoCVDynamicDocumentUseCase {

    private static final String DEFAULT_TITLE = "Hồ sơ công việc";

    private final HoSoCVDocumentRepository hoSoCVDocumentRepository;
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
                ? HoSoCVStatus.DANG_XU_LY
                : request.getStatus().trim();

            authorAclFieldsService.applyOnCreate(request.getDocumentType(), request.getFields());

            String dynamicFieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(
                request.getFields(), request.getDateFields(), request.getNumberFields(), request.getRichtextFields());

            var document = new HoSoCVDocument(title, request.getDocumentType(), status, null, null, null, null)
                .replaceDynamicFields(dynamicFieldsJson);

            var saved = hoSoCVDocumentRepository.save(document);
            documentCacheService.evictAllListCaches();
            documentLogService.logCreate(saved.getId(), buildSnapshot(saved));
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Tạo hồ sơ công việc (dynamic) thất bại", ex);
            return Result.failure("Tạo hồ sơ công việc thất bại: " + ex.getMessage());
        }
    }

    private Map<String, Object> buildSnapshot(HoSoCVDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }
}
