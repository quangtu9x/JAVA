package com.td.application.sharedcore;

import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Yêu cầu người dùng 08/10/2026 (danh mục người dùng): trước khi XOÁ hoặc KHOÁ 1 người dùng phải
 * kiểm tra 2 cấu hình còn trỏ tới họ, có thì TỪ CHỐI kèm cảnh báo:
 * <ol>
 *   <li><b>Người đại diện nhận văn bản</b> ({@code Organization.receiverId} = UUID của AppUser, xem
 *   {@code GetLegacyOrganizationTreeUseCase#resolveReceiverUsername}) — CHỈ xét tổ chức trực thuộc
 *   của người dùng ({@code AppUser.organizationId}), không quét cả cây cơ quan/đơn vị.</li>
 *   <li><b>Kiêm nhiệm</b> — tài khoản chính còn tài khoản kiêm nhiệm trỏ {@code parentUserId} về nó.</li>
 * </ol>
 * Dùng chung cho {@link DeleteAppUserUseCase}, {@link LockAppUsersUseCase} và
 * {@link UpdateAppUserUseCase} (form sửa đổi trạng thái sang Khoá).
 */
@Service
@RequiredArgsConstructor
public class AppUserDeactivationGuard {

    private final AppUserRepository appUserRepository;
    private final OrganizationRepository organizationRepository;

    /** Trả thông báo lỗi nếu KHÔNG được khoá; null = được phép. */
    public String checkLock(AppUser user, Collection<UUID> lockedTogetherIds) {
        String representative = checkRepresentative(user, "khoá");
        if (representative != null) {
            return representative;
        }
        // Tài khoản kiêm nhiệm đã khoá sẵn, hoặc được khoá CÙNG lượt này, không còn dùng được nên
        // không cản việc khoá tài khoản chính.
        Set<UUID> together = lockedTogetherIds == null ? Set.of() : Set.copyOf(lockedTogetherIds);
        List<AppUser> blocking = appUserRepository.findByParentUserIdAndDeletedOnIsNull(user.getId()).stream()
            .filter(AppUser::isActive)
            .filter(child -> !together.contains(child.getId()))
            .toList();
        return blocking.isEmpty() ? null : kiemNhiemMessage(user, blocking, "khoá");
    }

    /** Trả thông báo lỗi nếu KHÔNG được xoá; null = được phép. */
    public String checkDelete(AppUser user) {
        String representative = checkRepresentative(user, "xoá");
        if (representative != null) {
            return representative;
        }
        // Xoá tài khoản chính khi còn tài khoản kiêm nhiệm (kể cả đang khoá) sẽ để lại parentUserId mồ côi.
        List<AppUser> children = appUserRepository.findByParentUserIdAndDeletedOnIsNull(user.getId());
        return children.isEmpty() ? null : kiemNhiemMessage(user, children, "xoá");
    }

    /**
     * 09/10/2026: chuyển người dùng sang tổ chức khác (TransferAppUsers / form sửa đổi tổ chức) —
     * người đại diện nhận văn bản của tổ chức hiện tại phải được bỏ/thay trước, không thì tổ chức cũ
     * còn trỏ receiver_id tới người không còn thuộc về nó. Trả null = được phép.
     */
    public String checkTransfer(AppUser user, UUID targetOrganizationId) {
        if (targetOrganizationId == null || targetOrganizationId.equals(user.getOrganizationId())) {
            return null;
        }
        return checkRepresentative(user, "chuyển đơn vị");
    }

    private String checkRepresentative(AppUser user, String action) {
        if (user.getOrganizationId() == null) {
            return null;
        }
        return organizationRepository.findByIdAndDeletedOnIsNull(user.getOrganizationId())
            .filter(org -> isRepresentative(org.getReceiverId(), user))
            .map(org -> "Không thể " + action + " người dùng \"" + displayName(user)
                + "\": đang là người đại diện nhận văn bản của \"" + org.getName()
                + "\". Vui lòng bỏ hoặc thay người đại diện ở Cơ cấu tổ chức trước khi " + action + ".")
            .orElse(null);
    }

    private static boolean isRepresentative(String receiverId, AppUser user) {
        if (receiverId == null || receiverId.isBlank()) {
            return false;
        }
        String value = receiverId.trim();
        return value.equalsIgnoreCase(user.getId().toString())
            || (user.getUsername() != null && value.equalsIgnoreCase(user.getUsername()));
    }

    private static String kiemNhiemMessage(AppUser user, List<AppUser> children, String action) {
        String names = children.stream().map(AppUserDeactivationGuard::displayName).collect(Collectors.joining(", "));
        return "Không thể " + action + " tài khoản chính \"" + displayName(user)
            + "\": đang có tài khoản kiêm nhiệm (" + names + "). Vui lòng " + action
            + " hoặc bỏ cấu hình kiêm nhiệm trước khi " + action + " tài khoản chính.";
    }

    private static String displayName(AppUser user) {
        String fullName = user.getFullName();
        if (fullName == null || fullName.isBlank()) {
            return user.getUsername();
        }
        return fullName + " (" + user.getUsername() + ")";
    }
}
