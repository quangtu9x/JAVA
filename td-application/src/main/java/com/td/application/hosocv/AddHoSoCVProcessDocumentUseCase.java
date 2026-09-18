package com.td.application.hosocv;

import com.td.application.common.models.Result;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.hosocv.HoSoCVProcessDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mở rộng BL-240 (HoSoCV Core) — tương đương {@link AddHoSoCVDynamicDocumentUseCase} nhưng lưu vào
 * bảng vật lý ĐỘC LẬP {@code hosocv_process_documents} (xem {@link HoSoCVProcessDocument}) — writer
 * DUY NHẤT của vết xử lý, dùng bởi {@code TrinhDuyetHoSoUseCase}/{@code DuyetHoSoUseCase}/
 * {@code ChuyenLuuTruHoSoUseCase}/{@code GopYPhoiHopUseCase}. Đúng tiền lệ
 * {@code AddDraftProcessDocumentUseCase}.
 *
 * <p>Ép cứng {@code document_type='hosocv_process'} ở TẦNG NÀY (caller không tự đặt được) —
 * phân biệt loại vết cụ thể (trình duyệt/duyệt/chuyển lưu trữ/phối hợp/góp ý) qua field động
 * {@code process_type} do caller truyền trong {@code fields}, không phải qua {@code document_type}.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AddHoSoCVProcessDocumentUseCase {

    /** Loại document DUY NHẤT được phép tồn tại trong bảng hosocv_process_documents. */
    public static final String DOCUMENT_TYPE = "hosocv_process";

    private static final String DEFAULT_TITLE = "Quá trình xử lý hồ sơ công việc";

    private final HoSoCVProcessDocumentRepository hoSoCVProcessDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentLogService documentLogService;

    public Result<UUID> execute(AddDynamicDocumentRequest request) {
        try {
            String title = (request.getTitle() == null || request.getTitle().isBlank())
                ? DEFAULT_TITLE
                : request.getTitle().trim();

            String dynamicFieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(
                request.getFields(), request.getDateFields(), request.getNumberFields(), request.getRichtextFields());

            var document = new HoSoCVProcessDocument(title, DOCUMENT_TYPE, null, null, null, null, null)
                .replaceDynamicFields(dynamicFieldsJson);

            var saved = hoSoCVProcessDocumentRepository.save(document);
            documentLogService.logCreate(saved.getId(), buildSnapshot(saved));
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Tạo vết xử lý hồ sơ công việc (hosocv_process) thất bại", ex);
            return Result.failure("Tạo vết xử lý hồ sơ công việc thất bại: " + ex.getMessage());
        }
    }

    private Map<String, Object> buildSnapshot(HoSoCVProcessDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }
}
