package com.td.application.hosoluutru;

import com.td.application.common.models.Result;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.hosoluutru.HoSoLuuTruDanhMucDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Port module "Hồ sơ lưu trữ" — tạo/sửa 1 bản ghi trong 3 danh mục dùng chung
 * ({@code kyhieuhoso}/{@code chatluong}/{@code loaihinhtailieu}), xem
 * {@code docs/analysis/16-...md} mục 6.5/8.2. Tạo/sửa gộp 1 use case (đúng
 * {@code danhmuc/ChiTietModal.jsx} generic theo {@code nameform}).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class LuuDanhMucLuuTruUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy danh mục";
    private static final String DEFAULT_TITLE = "Danh mục hồ sơ lưu trữ";

    private final HoSoLuuTruDanhMucDocumentRepository hoSoLuuTruDanhMucDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<UUID> execute(LuuDanhMucLuuTruRequest request) {
        if (request == null || request.getDocumentType() == null || request.getDocumentType().isBlank()) {
            return Result.failure("Thiếu documentType (nameform)");
        }
        String title = request.getFields() == null ? "" : request.getFields().getOrDefault("name", "");
        if (title.isBlank()) {
            title = DEFAULT_TITLE;
        }
        String dynamicFieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(
            request.getFields(), null, null, null);

        if (request.getId() == null) {
            var document = new HoSoLuuTruDanhMucDocument(
                title, request.getDocumentType().trim().toLowerCase(), null, null, null, null, null)
                .replaceDynamicFields(dynamicFieldsJson);
            var saved = hoSoLuuTruDanhMucDocumentRepository.save(document);
            return Result.success(saved.getId());
        }

        HoSoLuuTruDanhMucDocument document = hoSoLuuTruDanhMucDocumentRepository.findById(request.getId()).orElse(null);
        if (document == null || document.isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        document.update(title, null, null, null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        var saved = hoSoLuuTruDanhMucDocumentRepository.save(document);
        return Result.success(saved.getId());
    }
}
