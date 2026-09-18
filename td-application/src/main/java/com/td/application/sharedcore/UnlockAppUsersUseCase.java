package com.td.application.sharedcore;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// BL-108: mở khoá tài khoản hàng loạt (tương đương field `status`="1" của Domino).
@Service
@RequiredArgsConstructor
@Transactional("identityTransactionManager")
@Slf4j
public class UnlockAppUsersUseCase {

    private final AppUserRepository appUserRepository;

    public Result<List<UUID>> execute(List<UUID> userIds) {
        try {
            if (userIds == null || userIds.isEmpty()) {
                return Result.failure("Danh sách người dùng không được để trống");
            }

            List<UUID> updated = new ArrayList<>();
            for (UUID id : userIds) {
                appUserRepository.findByIdAndDeletedOnIsNull(id).ifPresent(user -> {
                    user.update(null, null, null, null, null, true);
                    appUserRepository.save(user);
                    updated.add(user.getId());
                });
            }
            // Trước đây trả success ngay cả khi không id nào khớp (vd id sai/đã bị xoá/lệch schema
            // tenant) — FE nhận "Thao tác thành công!" dù KHÔNG có gì thay đổi thật, gây cảm giác
            // "mở khoá không được" dù request đã đi qua. Báo lỗi rõ ràng để phân biệt 2 trường hợp.
            if (updated.isEmpty()) {
                return Result.failure("Không mở khoá được người dùng nào (id không tồn tại hoặc đã bị xoá)");
            }
            if (updated.size() < userIds.size()) {
                log.warn("Mở khoá người dùng: chỉ {}/{} id khớp được bản ghi thật", updated.size(), userIds.size());
            }
            return Result.success(updated);
        } catch (Exception ex) {
            log.error("Mở khóa người dùng thất bại", ex);
            return Result.failure("Mở khóa người dùng thất bại: " + ex.getMessage());
        }
    }
}
