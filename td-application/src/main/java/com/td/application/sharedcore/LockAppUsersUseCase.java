package com.td.application.sharedcore;

import com.td.application.common.models.Result;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

// BL-108: khoá tài khoản hàng loạt (tương đương field `status`="0" của Domino — field `locked` cũ
// chỉ là cờ phụ hiển thị UI, không có ý nghĩa nghiệp vụ riêng nên không port).
@Service
@RequiredArgsConstructor
@Transactional("identityTransactionManager")
@Slf4j
public class LockAppUsersUseCase {

    private final AppUserRepository appUserRepository;
    private final AppUserDeactivationGuard deactivationGuard;

    public Result<List<UUID>> execute(List<UUID> userIds) {
        try {
            if (userIds == null || userIds.isEmpty()) {
                return Result.failure("Danh sách người dùng không được để trống");
            }

            List<AppUser> users = new ArrayList<>();
            for (UUID id : userIds) {
                appUserRepository.findByIdAndDeletedOnIsNull(id).ifPresent(users::add);
            }
            // 08/10/2026: người đại diện nhận văn bản / tài khoản chính còn kiêm nhiệm -> từ chối
            // CẢ lượt (kiểm tra hết trước khi ghi), không khoá 1 phần.
            List<String> blocked = users.stream()
                .map(user -> deactivationGuard.checkLock(user, userIds))
                .filter(Objects::nonNull)
                .toList();
            if (!blocked.isEmpty()) {
                return Result.failure(String.join(" ", blocked));
            }

            List<UUID> updated = new ArrayList<>();
            for (AppUser user : users) {
                user.update(null, null, null, null, null, false);
                appUserRepository.save(user);
                updated.add(user.getId());
            }
            // Trước đây trả success ngay cả khi không id nào khớp (vd id sai/đã bị xoá/lệch schema
            // tenant) — FE nhận "Thao tác thành công!" dù KHÔNG có gì thay đổi thật, gây cảm giác
            // "khoá không được" dù request đã đi qua. Báo lỗi rõ ràng để phân biệt 2 trường hợp.
            if (updated.isEmpty()) {
                return Result.failure("Không khoá được người dùng nào (id không tồn tại hoặc đã bị xoá)");
            }
            if (updated.size() < userIds.size()) {
                log.warn("Khoá người dùng: chỉ {}/{} id khớp được bản ghi thật", updated.size(), userIds.size());
            }
            return Result.success(updated);
        } catch (Exception ex) {
            log.error("Khóa người dùng thất bại", ex);
            return Result.failure("Khóa người dùng thất bại: " + ex.getMessage());
        }
    }
}
