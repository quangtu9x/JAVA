package com.td.application.sharedcore;

import com.td.domain.sharedcore.UserDelegation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 09/10/2026 — ủy quyền ĐANG HIỆU LỰC (bảng {@code user_delegations}) theo username người ủy quyền, trả về
 * đúng dạng {@code delegation_obj} Domino mà FE đang đọc ({@code global.jsx#isValidDelegation},
 * {@code CayChonNoiXuLyCapN}, {@code ChiTietPage} dự thảo…):
 * {@code {id, start_date, end_date (dd/MM/yyyy), data:[vbden|duthao], name, username, position, subject}}
 * — {@code name/username/position} là NGƯỜI NHẬN ủy quyền.
 *
 * <p>Thay cơ chế Domino chép {@code delegation_obj} sang tổ chức/nhóm nhận/sổ văn bản: các API đọc (cây
 * tổ chức, danh sách người dùng/lãnh đạo, nhóm, sổ) gọi lớp này lúc đọc. Ngày tính theo giờ Việt Nam,
 * hiệu lực tính CẢ ngày bắt đầu và ngày kết thúc.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(value = "identityTransactionManager", readOnly = true)
public class UserDelegationResolver {

    public static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final String SCOPE_VBDEN = "vbden";
    public static final String SCOPE_DUTHAO = "duthao";

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    private final UserDelegationRepository userDelegationRepository;

    public static LocalDate today() {
        return LocalDate.now(VN_ZONE);
    }

    /** username người ủy quyền (viết thường) → delegation_obj đang hiệu lực hôm nay. */
    public Map<String, Map<String, Object>> activeByDelegator(Collection<String> usernames) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (usernames == null || usernames.isEmpty()) {
            return result;
        }
        // Repository trả mới nhất trước — giữ bản đầu tiên cho mỗi người.
        for (UserDelegation delegation : userDelegationRepository.findActiveByDelegatorUsernamesOn(usernames, today())) {
            result.putIfAbsent(delegation.getDelegatorUsername().toLowerCase(Locale.ROOT), toLegacyObj(delegation));
        }
        return result;
    }

    /** Toàn bộ ủy quyền đang hiệu lực hôm nay — username người ủy quyền (viết thường) → delegation_obj. */
    public Map<String, Map<String, Object>> allActiveToday() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (UserDelegation delegation : userDelegationRepository.findActiveOn(today())) {
            result.putIfAbsent(delegation.getDelegatorUsername().toLowerCase(Locale.ROOT), toLegacyObj(delegation));
        }
        return result;
    }

    /** Như {@link #activeObjOrEmpty} nhưng trả chuỗi JSON (cho node.data kiểu Map<String,String>). */
    public String activeJsonOrEmpty(Map<String, Map<String, Object>> active, String username) {
        Object obj = activeObjOrEmpty(active, username);
        if (!(obj instanceof Map<?, ?>)) {
            return "";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (Exception ex) {
            return "";
        }
    }

    public Optional<Map<String, Object>> activeFor(String delegatorUsername) {
        if (delegatorUsername == null || delegatorUsername.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(activeByDelegator(List.of(delegatorUsername))
            .get(delegatorUsername.trim().toLowerCase(Locale.ROOT)));
    }

    /** {@code delegation_obj} đang hiệu lực của {@code username}, hoặc "" (giữ đúng mặc định cũ của các API). */
    public Object activeObjOrEmpty(Map<String, Map<String, Object>> active, String username) {
        if (username == null || active == null) {
            return "";
        }
        Map<String, Object> obj = active.get(username.trim().toLowerCase(Locale.ROOT));
        return obj == null ? "" : obj;
    }

    /**
     * Người ủy quyền {@code delegator} đang ủy quyền phạm vi {@code scope} cho đúng {@code delegate} — dùng
     * để server tự kiểm tra trước khi ghi người ủy quyền vào danh sách "đã chuyển" (Domino chỉ kiểm ở FE).
     */
    public boolean isActiveDelegation(String delegator, String delegate, String scope) {
        if (delegate == null || delegate.isBlank()) {
            return false;
        }
        return activeFor(delegator)
            .filter(obj -> delegate.trim().equalsIgnoreCase(String.valueOf(obj.get("username"))))
            .filter(obj -> obj.get("data") instanceof List<?> data && data.stream().anyMatch(s -> scope.equalsIgnoreCase(String.valueOf(s))))
            .isPresent();
    }

    public static Map<String, Object> toLegacyObj(UserDelegation delegation) {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("id", delegation.getId().toString());
        obj.put("start_date", delegation.getStartDate().format(DISPLAY_DATE));
        obj.put("end_date", delegation.getEndDate().format(DISPLAY_DATE));
        obj.put("data", splitScopes(delegation.getScopes()));
        obj.put("name", Objects.toString(delegation.getDelegateName(), ""));
        obj.put("username", delegation.getDelegateUsername());
        obj.put("position", Objects.toString(delegation.getDelegatePosition(), ""));
        obj.put("subject", Objects.toString(delegation.getSubject(), ""));
        return obj;
    }

    public static List<String> splitScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(scopes.split(";")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
