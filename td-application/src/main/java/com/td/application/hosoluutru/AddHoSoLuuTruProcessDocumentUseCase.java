package com.td.application.hosoluutru;

import com.td.application.common.models.Result;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.hosoluutru.HoSoLuuTruProcessDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Port module "Hồ sơ lưu trữ" — writer DUY NHẤT của bảng {@code hosoluutru_process_documents} (vết xử
 * lý LTLS nội bộ), đúng khuôn {@code AddHoSoCVProcessDocumentUseCase}. Ép cứng
 * {@code document_type='hosoluutru_process'} ở TẦNG NÀY.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AddHoSoLuuTruProcessDocumentUseCase {

    public static final String DOCUMENT_TYPE = "hosoluutru_process";
    private static final String DEFAULT_TITLE = "Quá trình xử lý hồ sơ lưu trữ";

    private final HoSoLuuTruProcessDocumentRepository hoSoLuuTruProcessDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<UUID> execute(AddDynamicDocumentRequest request) {
        try {
            String title = (request.getTitle() == null || request.getTitle().isBlank())
                ? DEFAULT_TITLE
                : request.getTitle().trim();

            String dynamicFieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(
                request.getFields(), request.getDateFields(), request.getNumberFields(), request.getRichtextFields());

            HoSoLuuTruProcessDocument document = new HoSoLuuTruProcessDocument(
                title, DOCUMENT_TYPE, null, null, null, null, null)
                .replaceDynamicFields(dynamicFieldsJson);

            var saved = hoSoLuuTruProcessDocumentRepository.save(document);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Tạo vết xử lý LTLS hồ sơ lưu trữ thất bại", ex);
            return Result.failure("Tạo vết xử lý LTLS thất bại: " + ex.getMessage());
        }
    }
}
