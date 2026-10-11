package com.td.application.privatecatalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.sharedcore.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 06/10/2026 (yêu cầu người dùng "ở các modal chọn người nhớ lọc trạng thái khoá và xoá, tránh chọn
 * nhầm"): "nhóm người nhận" (PrivateCategory form=group_people) lưu ẢNH CHỤP thành viên trong
 * {@code receiver_obj} lúc tạo nhóm — không đối chiếu lại {@code app_users}, nên người đã bị Khoá/Xoá
 * sau đó vẫn chọn được qua nhóm. Lọc lại lúc ĐỌC cho các màn chọn người (không sửa dữ liệu nhóm đã
 * lưu — mở khoá lại thì thành viên tự xuất hiện lại). Phần tử không có {@code username} (không phải
 * người) giữ nguyên.
 */
@Component
@RequiredArgsConstructor
public class ActiveGroupMembersFilter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String USERNAME_KEY = "username";

    private final AppUserRepository appUserRepository;
    // 09/10/2026 (port Ủy quyền): thay Domino UyQuyen#capnhat_nhom_nhan_vanban (chép delegation_obj vào
    // từng thành viên receiver_obj) — gắn LÚC ĐỌC ủy quyền đang hiệu lực của mỗi thành viên còn hoạt động.
    private final com.td.application.sharedcore.UserDelegationResolver userDelegationResolver;

    /** Lọc 1 lô {@code receiver_obj} (List đã parse hoặc chuỗi JSON) bằng 1 query duy nhất; giữ nguyên dạng đầu vào. */
    public List<Object> filter(List<Object> receiverObjs) {
        Set<String> usernames = new LinkedHashSet<>();
        List<List<?>> parsed = new ArrayList<>();
        for (Object receiverObj : receiverObjs) {
            List<?> members = asList(receiverObj);
            parsed.add(members);
            if (members == null) {
                continue;
            }
            for (Object member : members) {
                String username = usernameOf(member);
                if (username != null) {
                    usernames.add(username);
                }
            }
        }
        Set<String> active = usernames.isEmpty() ? Set.of() : appUserRepository.findActiveUsernamesIn(usernames);
        Map<String, Map<String, Object>> delegations = usernames.isEmpty() ? Map.of()
            : userDelegationResolver.activeByDelegator(usernames);

        List<Object> result = new ArrayList<>();
        for (int i = 0; i < receiverObjs.size(); i++) {
            Object original = receiverObjs.get(i);
            List<?> members = parsed.get(i);
            if (members == null) {
                result.add(original);
                continue;
            }
            List<Object> kept = new ArrayList<>();
            for (Object member : members) {
                String username = usernameOf(member);
                if (username == null) {
                    kept.add(member);
                } else if (active.contains(username)) {
                    kept.add(withDelegation(member, userDelegationResolver.activeObjOrEmpty(delegations, username)));
                }
            }
            result.add(original instanceof String ? toJson(kept) : kept);
        }
        return result;
    }

    private Object withDelegation(Object member, Object delegationObj) {
        if (!(member instanceof Map<?, ?> map)) {
            return member;
        }
        Map<Object, Object> copy = new java.util.LinkedHashMap<>(map);
        copy.put("delegation_obj", delegationObj);
        return copy;
    }

    private List<?> asList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        if (value instanceof String str && !str.isBlank()) {
            try {
                Object parsed = OBJECT_MAPPER.readValue(str, Object.class);
                return parsed instanceof List<?> list ? list : null;
            } catch (Exception ex) {
                return null;
            }
        }
        return null;
    }

    private String usernameOf(Object member) {
        if (member instanceof Map<?, ?> map && map.get(USERNAME_KEY) != null) {
            String username = map.get(USERNAME_KEY).toString().trim();
            return username.isEmpty() ? null : username;
        }
        return null;
    }

    private String toJson(Collection<?> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }
}
