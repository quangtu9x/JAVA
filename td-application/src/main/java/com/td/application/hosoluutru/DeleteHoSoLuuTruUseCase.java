package com.td.application.hosoluutru;

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
 * Port module "Hồ sơ lưu trữ" — xoá mềm hồ sơ lưu trữ, ĐỆ QUY xoá mềm toàn bộ văn bản con (đúng hành
 * vi thật {@code XuLyHoSoLuuTru.Xoa()} — xem {@code docs/analysis/16-...md} mục 5.4). Nhận DANH SÁCH
 * id, xoá được nhiều hồ sơ cùng lúc (đúng khuôn {@code DeleteHoSoUseCase} hosocv).
 *
 * <p>{@code deletedBy} lấy từ {@link CurrentUserProvider} + {@link AppUserRepository} (KHÔNG
 * {@code UUID.randomUUID()}, đúng {@code docs/architecture/04-quy-tac-crud-api-shared-core.md}).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DeleteHoSoLuuTruUseCase {

    private final HoSoLuuTruDocumentRepository hoSoLuuTruDocumentRepository;
    private final HoSoLuuTruVanBanDocumentRepository hoSoLuuTruVanBanDocumentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;

    public Result<List<UUID>> execute(List<UUID> hosoLuuTruIds) {
        if (hosoLuuTruIds == null || hosoLuuTruIds.isEmpty()) {
            return Result.failure("Danh sách hồ sơ lưu trữ không được để trống");
        }
        UUID deletedBy = resolveCurrentUserId();
        List<UUID> deleted = new ArrayList<>();

        for (UUID id : hosoLuuTruIds) {
            hoSoLuuTruDocumentRepository.findById(id).ifPresent(document -> {
                if (document.isDeleted()) {
                    deleted.add(document.getId());
                    return;
                }
                for (var vanBan : hoSoLuuTruVanBanDocumentRepository.findByHosoId(document.getId())) {
                    vanBan.markAsDeleted(deletedBy);
                    hoSoLuuTruVanBanDocumentRepository.save(vanBan);
                }
                document.markAsDeleted(deletedBy);
                var saved = hoSoLuuTruDocumentRepository.save(document);
                deleted.add(saved.getId());
            });
        }

        if (deleted.isEmpty()) {
            return Result.failure("Không tìm thấy hồ sơ lưu trữ nào để xoá");
        }
        if (deleted.size() < hosoLuuTruIds.size()) {
            log.warn("Xoá hồ sơ lưu trữ: chỉ {}/{} id tồn tại và được xoá", deleted.size(), hosoLuuTruIds.size());
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
