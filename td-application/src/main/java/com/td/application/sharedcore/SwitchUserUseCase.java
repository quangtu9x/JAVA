package com.td.application.sharedcore;

import com.td.application.common.models.Result;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Port TDSwitchUser (Domino, {@code services/danhmuc/TDSwitchUser.java}) — Manager chuyển sang thao
 * tác dưới danh tính người dùng khác mà không cần đăng xuất (kiểm tra dữ liệu/hỗ trợ kỹ thuật). Xem
 * thiết kế đầy đủ tại docs/analysis/19-checklist-port-tdswitchuser-domino-sang-java.md.
 *
 * <p>Chỉ xác thực TARGET user tồn tại + đang mở khoá — quyền của NGƯỜI GỌI (chỉ Manager/ADMIN mới
 * được switch) gate ở tầng controller (`@PreAuthorize`), không lặp lại ở đây.</p>
 *
 * <p><b>Khác biệt có chủ đích so với blueprint gốc (tài liệu 10 §5.8 SwitchUserServiceImpl)</b>: BỎ
 * nhánh "user đích không tồn tại → vẫn cấp token quyền Manager" — đây là lỗ hổng bảo mật trong thiết
 * kế gốc (không phải hành vi Domino thật, TDSwitchUser.java thật cũng trả lỗi khi không tìm thấy
 * user), trả lỗi rõ ràng thay vào đó.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(value = "identityTransactionManager", readOnly = true)
public class SwitchUserUseCase {

    private final AppUserRepository appUserRepository;

    public Result<AppUser> execute(String targetUsername) {
        if (targetUsername == null || targetUsername.isBlank()) {
            return Result.failure("Tên đăng nhập không được để trống");
        }

        var opt = appUserRepository.findByUsernameAndDeletedOnIsNull(targetUsername.trim());
        if (opt.isEmpty()) {
            return Result.failure("Không tìm thấy tài khoản: " + targetUsername.trim());
        }

        AppUser target = opt.get();
        // Khớp hành vi Domino gốc (TDAuthentication.CreateAdminSwitchToken chặn khi status != "1") —
        // cùng tinh thần gap #1 checklist kiêm nhiệm (khoá không được cấp token hợp lệ).
        if (!target.isActive()) {
            return Result.failure("Tài khoản đang bị khoá, không thể chuyển sang");
        }

        return Result.success(target);
    }
}
