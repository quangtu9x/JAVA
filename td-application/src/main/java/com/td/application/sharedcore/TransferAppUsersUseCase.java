package com.td.application.sharedcore;

import com.td.application.common.models.Result;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// BL-108: chuyển đơn vị hàng loạt — 1 transaction rõ ràng (tất cả thành công hoặc rollback), thay
// cho cách FE cũ (fire hàng loạt request qua forEach không await/Promise.all, không rollback —
// xác nhận qua code là chưa hoàn thiện, còn sót console.log "chưa làm gì hết").
@Service
@RequiredArgsConstructor
@Transactional("identityTransactionManager")
@Slf4j
public class TransferAppUsersUseCase {

    private final AppUserRepository appUserRepository;
    private final OrganizationRepository organizationRepository;

    public Result<List<UUID>> execute(List<UUID> userIds, UUID organizationId) {
        try {
            if (userIds == null || userIds.isEmpty()) {
                return Result.failure("Danh sách người dùng không được để trống");
            }
            if (organizationId == null) {
                return Result.failure("Tổ chức đích không được để trống");
            }

            var orgOpt = organizationRepository.findByIdAndDeletedOnIsNull(organizationId);
            if (orgOpt.isEmpty()) {
                return Result.failure("Không tìm thấy tổ chức với ID: " + organizationId);
            }
            if (!orgOpt.get().isActive()) {
                return Result.failure("Tổ chức đích đang bị vô hiệu hóa, không thể chuyển người dùng đến");
            }

            // Xác thực TOÀN BỘ id tồn tại trước khi ghi bất kỳ thay đổi nào — đảm bảo tất cả
            // thành công hoặc không ai bị chuyển (Result.failure không tự rollback transaction,
            // khác exception, nên phải chặn ghi trước thay vì ghi rồi mới phát hiện lỗi).
            List<UUID> notFound = new ArrayList<>();
            var users = new ArrayList<AppUser>();
            for (UUID id : userIds) {
                var userOpt = appUserRepository.findByIdAndDeletedOnIsNull(id);
                if (userOpt.isEmpty()) {
                    notFound.add(id);
                } else {
                    users.add(userOpt.get());
                }
            }
            if (!notFound.isEmpty()) {
                return Result.failure("Không tìm thấy người dùng với ID: " + notFound);
            }

            List<UUID> updated = new ArrayList<>();
            for (var user : users) {
                user.update(null, null, null, organizationId, null, null);
                appUserRepository.save(user);
                updated.add(user.getId());
            }

            return Result.success(updated);
        } catch (Exception ex) {
            log.error("Chuyển đơn vị người dùng thất bại", ex);
            return Result.failure("Chuyển đơn vị người dùng thất bại: " + ex.getMessage());
        }
    }
}
