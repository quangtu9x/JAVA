package com.td.application.hosoluutru;

import com.td.application.common.models.Result;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.hosoluutru.HoSoLuuTruDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Port module "Hồ sơ lưu trữ" — tạo trực tiếp 1 hồ sơ lưu trữ (form Domino {@code hoso}, CRUD độc lập
 * — KHÔNG qua {@code hosocv}), tương đương {@link com.td.application.hosocv.AddHoSoCVDynamicDocumentUseCase}
 * nhưng lưu vào bảng {@code hosoluutru_documents}. Status khởi tạo mặc định
 * {@link HoSoLuuTruLifecycleStatus#FORM_DA_TIEP_NHAN} (tạo trực tiếp coi như đã "đã tiếp nhận" — khác
 * đường "Chuyển lưu trữ" từ {@code hosocv} qua {@link TaoHoSoLuuTruTuHoSoCVUseCase}, luôn bắt đầu
 * {@code hoso_chuaduyet}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AddHoSoLuuTruDynamicDocumentUseCase {

    private static final String DEFAULT_TITLE = "Hồ sơ lưu trữ";

    private final HoSoLuuTruDocumentRepository hoSoLuuTruDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final AuthorAclFieldsService authorAclFieldsService;

    public Result<UUID> execute(AddDynamicDocumentRequest request) {
        try {
            String title = (request.getTitle() == null || request.getTitle().isBlank())
                ? DEFAULT_TITLE
                : request.getTitle().trim();
            String status = (request.getStatus() == null || request.getStatus().isBlank())
                ? HoSoLuuTruLifecycleStatus.FORM_DA_TIEP_NHAN
                : request.getStatus().trim();

            authorAclFieldsService.applyOnCreate(request.getDocumentType(), request.getFields());

            String dynamicFieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(
                request.getFields(), request.getDateFields(), request.getNumberFields(), request.getRichtextFields());

            var document = new HoSoLuuTruDocument(
                title, TaoHoSoLuuTruTuHoSoCVUseCase.DOCUMENT_TYPE, status, null, null, null, null)
                .replaceDynamicFields(dynamicFieldsJson);

            var saved = hoSoLuuTruDocumentRepository.save(document);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Tạo hồ sơ lưu trữ (dynamic) thất bại", ex);
            return Result.failure("Tạo hồ sơ lưu trữ thất bại: " + ex.getMessage());
        }
    }
}
