package com.td.application.hosoluutru;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 30/10/2026: thay {@code qlvb/delete?databasetype=hosoluutru} cho bản ghi CON của hồ sơ lưu trữ (văn bản
 * con, phiếu yêu cầu, vết quá trình) — CHỈ xoá mềm (markAsDeleted), đã xoá thì coi là thành công. Hồ sơ
 * dùng {@link DeleteHoSoLuuTruUseCase} (đệ quy văn bản con).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class XoaTaiLieuLuuTruUseCase {

    private final HoSoLuuTruVanBanDocumentRepository hoSoLuuTruVanBanDocumentRepository;
    private final HoSoLuuTruPhieuYeuCauDocumentRepository hoSoLuuTruPhieuYeuCauDocumentRepository;
    private final HoSoLuuTruProcessDocumentRepository hoSoLuuTruProcessDocumentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;

    public Result<List<UUID>> execute(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.failure("Danh sách tài liệu cần xoá không được để trống");
        }
        UUID deletedBy = resolveCurrentUserId();
        List<UUID> deleted = new ArrayList<>();
        for (UUID id : ids) {
            if (id == null) {
                continue;
            }
            var vanBan = hoSoLuuTruVanBanDocumentRepository.findById(id);
            if (vanBan.isPresent()) {
                if (!vanBan.get().isDeleted()) {
                    vanBan.get().markAsDeleted(deletedBy);
                    hoSoLuuTruVanBanDocumentRepository.save(vanBan.get());
                }
                deleted.add(id);
                continue;
            }
            var phieu = hoSoLuuTruPhieuYeuCauDocumentRepository.findById(id);
            if (phieu.isPresent()) {
                if (!phieu.get().isDeleted()) {
                    phieu.get().markAsDeleted(deletedBy);
                    hoSoLuuTruPhieuYeuCauDocumentRepository.save(phieu.get());
                }
                deleted.add(id);
                continue;
            }
            var process = hoSoLuuTruProcessDocumentRepository.findById(id);
            if (process.isPresent()) {
                if (!process.get().isDeleted()) {
                    process.get().markAsDeleted(deletedBy);
                    hoSoLuuTruProcessDocumentRepository.save(process.get());
                }
                deleted.add(id);
            }
        }
        if (deleted.isEmpty()) {
            return Result.failure("Không tìm thấy tài liệu nào để xoá");
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
