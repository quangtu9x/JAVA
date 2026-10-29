package com.td.application.sharedcore;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.realtime.RealtimeNotification;
import com.td.application.realtime.RealtimeNotifier;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.sharedcore.UserDelegation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 09/10/2026 — port màn "Ủy quyền" (Domino {@code qlvb/danhmuc/UyQuyen}, {@code UyQuyen.jsx}) sang bảng
 * {@code user_delegations}. CHỈ thao tác ủy quyền của CHÍNH người đăng nhập (Domino ghi theo {@code id}
 * client gửi nhưng mọi việc chép sang tổ chức/nhóm/sổ lại theo người đăng nhập; FE sau cutover còn gửi id
 * giả nên không đọc/lưu được).
 *
 * <p>Tốt hơn Domino: (1) mỗi lần lưu là 1 dòng mới, ủy quyền còn hiệu lực/sắp hiệu lực trước đó tự chuyển
 * {@code revoked} — có lịch sử; (2) huỷ được ủy quyền; (3) kiểm tra ở server: người nhận tồn tại, đang
 * hoạt động, khác chính mình, ngày kết thúc ≥ ngày bắt đầu và chưa qua, phạm vi hợp lệ; (4) không chép
 * sang tổ chức/nhóm/sổ — xem {@link UserDelegationResolver}.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional("identityTransactionManager")
public class ManageMyDelegationUseCase {

    private static final Set<String> ALLOWED_SCOPES = Set.of(UserDelegationResolver.SCOPE_VBDEN, UserDelegationResolver.SCOPE_DUTHAO);

    private final UserDelegationRepository userDelegationRepository;
    private final AppUserRepository appUserRepository;
    private final CurrentUserProvider currentUserProvider;
    private final RealtimeNotifier realtimeNotifier;

    /** {@code current}: ủy quyền đang/sắp hiệu lực (dạng delegation_obj) hoặc null; {@code history}: mọi lần ủy quyền. */
    @Transactional(value = "identityTransactionManager", readOnly = true)
    public Result<Map<String, Object>> getMine() {
        Optional<AppUser> me = currentAppUser();
        if (me.isEmpty()) {
            return Result.failure("Không xác định được người dùng hiện tại");
        }
        LocalDate today = UserDelegationResolver.today();
        List<UserDelegation> all = userDelegationRepository.findByDelegatorUserId(me.get().getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("current", all.stream().filter(d -> d.isCurrentOrUpcoming(today)).findFirst()
            .map(UserDelegationResolver::toLegacyObj).orElse(null));
        result.put("history", all.stream().map(d -> toHistoryRow(d, today)).toList());
        return Result.success(result);
    }

    public Result<UUID> saveMine(SaveMyDelegationRequest request) {
        Optional<AppUser> meOpt = currentAppUser();
        if (meOpt.isEmpty()) {
            return Result.failure("Không xác định được người dùng hiện tại");
        }
        AppUser me = meOpt.get();
        if (request == null || isBlank(request.getDelegateUsername())) {
            return Result.failure("Vui lòng chọn người nhận ủy quyền");
        }
        Optional<AppUser> delegateOpt = appUserRepository.findByUsernameAndDeletedOnIsNull(request.getDelegateUsername().trim());
        if (delegateOpt.isEmpty() || !delegateOpt.get().isActive()) {
            return Result.failure("Người nhận ủy quyền không tồn tại hoặc đang bị khoá");
        }
        AppUser delegate = delegateOpt.get();
        if (delegate.getId().equals(me.getId())) {
            return Result.failure("Không thể ủy quyền cho chính mình");
        }
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(request.getStartDate().trim(), UserDelegationResolver.DISPLAY_DATE);
            end = LocalDate.parse(request.getEndDate().trim(), UserDelegationResolver.DISPLAY_DATE);
        } catch (DateTimeParseException | NullPointerException ex) {
            return Result.failure("Ngày bắt đầu/kết thúc ủy quyền không hợp lệ (dd/MM/yyyy)");
        }
        if (end.isBefore(start)) {
            return Result.failure("Ngày kết thúc ủy quyền phải từ ngày bắt đầu trở đi");
        }
        if (end.isBefore(UserDelegationResolver.today())) {
            return Result.failure("Ngày kết thúc ủy quyền đã qua");
        }
        List<String> scopes = request.getScopes() == null ? List.of() : request.getScopes().stream()
            .filter(s -> s != null && ALLOWED_SCOPES.contains(s.trim().toLowerCase()))
            .map(s -> s.trim().toLowerCase()).distinct().toList();
        if (scopes.isEmpty()) {
            return Result.failure("Vui lòng chọn phạm vi ủy quyền xử lý thay (Văn bản đến / Dự thảo)");
        }

        LocalDate today = UserDelegationResolver.today();
        List<String> previousDelegates = new java.util.ArrayList<>();
        for (UserDelegation existing : userDelegationRepository.findByDelegatorUserId(me.getId())) {
            if (existing.isCurrentOrUpcoming(today)) {
                previousDelegates.add(existing.getDelegateUsername());
                existing.revoke(me.getId());
                userDelegationRepository.save(existing);
            }
        }
        UserDelegation delegation = new UserDelegation(me.getId(), me.getUsername(),
            delegate.getId(), delegate.getUsername(), delegate.getFullName(), resolvePosition(delegate),
            start, end, String.join(";", scopes), trimToNull(request.getSubject()));
        UUID savedId = userDelegationRepository.save(delegation).getId();
        // UyQuyen.java dong 141-143: báo người được ủy quyền mới + người được ủy quyền cũ (bị thay).
        realtimeNotifier.notify(RealtimeNotification.of((String) null, "capnhatuyquyen", (String) null, null)
            .to(previousDelegates)
            .to(delegate.getUsername())
            .exclude(me.getUsername())
            .message("Cập nhật ủy quyền xử lý thay từ " + request.getStartDate().trim() + " đến "
                + request.getEndDate().trim()));
        return Result.success(savedId);
    }

    public Result<UUID> revokeMine(UUID delegationId) {
        Optional<AppUser> me = currentAppUser();
        if (me.isEmpty()) {
            return Result.failure("Không xác định được người dùng hiện tại");
        }
        Optional<UserDelegation> opt = delegationId == null ? Optional.empty()
            : userDelegationRepository.findByIdAndDeletedOnIsNull(delegationId);
        if (opt.isEmpty() || !opt.get().getDelegatorUserId().equals(me.get().getId())) {
            return Result.failure("Không tìm thấy ủy quyền của bạn");
        }
        UserDelegation delegation = opt.get();
        if (!UserDelegation.STATUS_ACTIVE.equals(delegation.getStatus())) {
            return Result.failure("Ủy quyền đã được huỷ trước đó");
        }
        delegation.revoke(me.get().getId());
        UUID savedId = userDelegationRepository.save(delegation).getId();
        realtimeNotifier.notify(RealtimeNotification.of((String) null, "capnhatuyquyen", (String) null, null)
            .to(delegation.getDelegateUsername())
            .message("Đã huỷ ủy quyền xử lý thay"));
        return Result.success(savedId);
    }

    private Map<String, Object> toHistoryRow(UserDelegation d, LocalDate today) {
        Map<String, Object> row = UserDelegationResolver.toLegacyObj(d);
        String state;
        if (UserDelegation.STATUS_REVOKED.equals(d.getStatus())) {
            state = "Đã huỷ";
        } else if (today.isAfter(d.getEndDate())) {
            state = "Hết hạn";
        } else if (today.isBefore(d.getStartDate())) {
            state = "Chưa đến ngày";
        } else {
            state = "Đang hiệu lực";
        }
        row.put("status", d.getStatus());
        row.put("state", state);
        row.put("created_on", d.getCreatedOn());
        row.put("revoked_on", d.getRevokedOn());
        return row;
    }

    // Chức vụ hiển thị — AppUser chỉ có positionId; dùng field động "position" (FE userApi tách riêng) nếu có.
    private String resolvePosition(AppUser user) {
        String json = user.getDynamicFieldsJson();
        if (json == null || !json.contains("\"position\"")) {
            return null;
        }
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get("position");
            return node == null || node.isNull() ? null : node.asText();
        } catch (Exception ex) {
            return null;
        }
    }

    private Optional<AppUser> currentAppUser() {
        return currentUserProvider.currentUsername().flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
