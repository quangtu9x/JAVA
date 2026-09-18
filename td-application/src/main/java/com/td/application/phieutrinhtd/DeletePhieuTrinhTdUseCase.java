package com.td.application.phieutrinhtd;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Xoá mềm phiếu trình thẩm định (nhận danh sách id, xoá được nhiều bản ghi cùng lúc — đúng khuôn
 * {@code DeleteHoSoLuuTruUseCase}). Log {@code quatrinh} con KHÔNG bị xoá theo (giữ nguyên lịch sử,
 * khác {@code hosoluutru} không có khái niệm tương tự "văn bản con" ở đây).
 *
 * <p>{@code deletedBy} lấy từ {@link CurrentUserProvider} + {@link AppUserRepository} (KHÔNG
 * {@code UUID.randomUUID()}, đúng {@code docs/architecture/04-quy-tac-crud-api-shared-core.md}).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DeletePhieuTrinhTdUseCase {

    private final PhieuTrinhTdDocumentRepository phieuTrinhTdDocumentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;

    public Result<List<UUID>> execute(List<UUID> phieuTrinhIds) {
        if (phieuTrinhIds == null || phieuTrinhIds.isEmpty()) {
            return Result.failure("Danh sách phiếu trình không được để trống");
        }
        UUID deletedBy = resolveCurrentUserId();
        List<UUID> deleted = new ArrayList<>();

        for (UUID id : phieuTrinhIds) {
            phieuTrinhTdDocumentRepository.findById(id).ifPresent(document -> {
                if (document.isDeleted()) {
                    deleted.add(document.getId());
                    return;
                }
                document.markAsDeleted(deletedBy);
                var saved = phieuTrinhTdDocumentRepository.save(document);
                deleted.add(saved.getId());
            });
        }

        if (deleted.isEmpty()) {
            return Result.failure("Không tìm thấy phiếu trình nào để xoá");
        }
        if (deleted.size() < phieuTrinhIds.size()) {
            log.warn("Xoá phiếu trình: chỉ {}/{} id tồn tại và được xoá", deleted.size(), phieuTrinhIds.size());
        }
        return Result.success(deleted);
    }

    private UUID resolveCurrentUserId() {
        return currentUserProvider.currentUsername()
            .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull)
            .map(AppUser::getId)
            .orElse(null);
    }
}
