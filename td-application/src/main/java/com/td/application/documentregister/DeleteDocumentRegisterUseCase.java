package com.td.application.documentregister;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// BL-116: FE hiện đang ẩn nút xoá sổ văn bản ("Tạm ẩn" trong ItemsList.jsx gốc) nhưng vẫn để backend
// có sẵn CRUD đầy đủ (giữ nguyên khả năng mở lại tính năng sau này mà không cần thêm việc).
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DeleteDocumentRegisterUseCase {

    private final DocumentRegisterRepository documentRegisterRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;

    public Result<UUID> execute(UUID id) {
        try {
            var opt = documentRegisterRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy sổ văn bản với ID: " + id);
            }
            var entity = opt.get();
            entity.markAsDeleted(resolveCurrentUserId());
            var saved = documentRegisterRepository.save(entity);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Xóa sổ văn bản thất bại", ex);
            return Result.failure("Xóa sổ văn bản thất bại: " + ex.getMessage());
        }
    }

    private UUID resolveCurrentUserId() {
        return currentUserProvider.currentUsername()
            .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull)
            .map(AppUser::getId)
            .orElse(null);
    }
}
