package com.td.application.hosoluutru;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Port module "Hồ sơ lưu trữ" — xoá 1 danh mục dùng chung. Đúng bằng chứng FE thật
 * ({@code danhmuc/ItemsList.jsx:157-163}, {@code deleteoption:"empty"}) — xoá VĨNH VIỄN (hard
 * delete), KHÁC hẳn xoá mềm của hồ sơ/văn bản/phiếu yêu cầu trong cùng module.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class XoaDanhMucLuuTruUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy danh mục";

    private final HoSoLuuTruDanhMucDocumentRepository hoSoLuuTruDanhMucDocumentRepository;

    public Result<UUID> execute(UUID id) {
        if (id == null) {
            return Result.failure("Thiếu id danh mục cần xoá");
        }
        var document = hoSoLuuTruDanhMucDocumentRepository.findById(id).orElse(null);
        if (document == null) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        hoSoLuuTruDanhMucDocumentRepository.delete(document);
        return Result.success(id);
    }
}
