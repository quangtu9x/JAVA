package com.td.application.hosoluutru;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Port module "Hồ sơ lưu trữ" — xoá 1 danh mục dùng chung. FE gốc gửi {@code deleteoption:"empty"}
 * nhưng 30/10/2026 đổi sang XOÁ MỀM (markAsDeleted) theo luật chung "xoá = luôn xoá mềm";
 * {@code findByDocumentType} đã lọc {@code deleted_on IS NULL} nên danh mục biến khỏi danh sách.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class XoaDanhMucLuuTruUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy danh mục";

    private final HoSoLuuTruDanhMucDocumentRepository hoSoLuuTruDanhMucDocumentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;

    public Result<UUID> execute(UUID id) {
        if (id == null) {
            return Result.failure("Thiếu id danh mục cần xoá");
        }
        var document = hoSoLuuTruDanhMucDocumentRepository.findById(id).orElse(null);
        if (document == null) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        if (!document.isDeleted()) {
            document.markAsDeleted(currentUserProvider.currentUsername()
                .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull)
                .map(AppUser::getId)
                .orElse(null));
            hoSoLuuTruDanhMucDocumentRepository.save(document);
        }
        return Result.success(id);
    }
}
