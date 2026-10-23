package com.td.application.duthao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.sharedcore.UserDelegationResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 19/10/2026 (ủy quyền ở "Người nhận phối hợp" dự thảo — cùng cách Chuyển xử lý vbden, xem
 * {@code ChuyenXuLyVbdenUseCase}: user_coordinate_delegation → user_coordinated_movement).
 *
 * <p>FE ({@code ItemsActiveList} với {@code delegationScope="duthao"}) chọn người A đang ủy quyền cho B thì
 * phần tử {@code coordinator_obj} có {@code username=B} + {@code delegated_from_username=A}; danh sách chờ
 * {@code user_coordinator} chứa B. Lớp này (gọi khi Lưu/Tạo dự thảo) XÁC MINH từng cặp (A→B) bằng
 * {@link UserDelegationResolver#isActiveDelegation} phạm vi Dự thảo rồi thêm A vào
 * {@code user_coordinated_movement} ("đã chuyển phối hợp") — để A vẫn thấy dự thảo ở tab "Đã phối hợp".
 * Không tin FE: cặp không khớp ủy quyền đang hiệu lực bị bỏ qua. Chỉ THÊM (không xoá) — giống vbden.</p>
 */
@Component
@RequiredArgsConstructor
public class DuThaoCoordinatorDelegations {

    static final String COORDINATOR_OBJ_FIELD = "coordinator_obj";
    static final String USER_COORDINATED_MOVEMENT_FIELD = "user_coordinated_movement";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final UserDelegationResolver userDelegationResolver;

    /** Bản cho map field đã parse (LuuDuThaoUseCase — sau khi merge). */
    public void apply(Map<String, Object> fields) {
        String merged = mergedMovement(fields.get(COORDINATOR_OBJ_FIELD), fields.get(USER_COORDINATED_MOVEMENT_FIELD));
        if (merged != null) {
            fields.put(USER_COORDINATED_MOVEMENT_FIELD, merged);
        }
    }

    /** Bản cho map field chuỗi (TaoDuThaoUseCase — trước khi tạo). */
    public void applyToStringFields(Map<String, String> fields) {
        String merged = mergedMovement(fields.get(COORDINATOR_OBJ_FIELD), fields.get(USER_COORDINATED_MOVEMENT_FIELD));
        if (merged != null) {
            fields.put(USER_COORDINATED_MOVEMENT_FIELD, merged);
        }
    }

    // null = không có gì để thêm (giữ nguyên field).
    private String mergedMovement(Object coordinatorObj, Object existingMovement) {
        List<String> verified = verifiedDelegators(coordinatorObj);
        if (verified.isEmpty()) {
            return null;
        }
        Set<String> members = new LinkedHashSet<>(splitMembers(existingMovement));
        boolean added = false;
        for (String delegator : verified) {
            if (members.stream().noneMatch(m -> m.equalsIgnoreCase(delegator))) {
                members.add(delegator);
                added = true;
            }
        }
        return added ? String.join(";", members) : null;
    }

    List<String> verifiedDelegators(Object coordinatorObj) {
        List<String> result = new ArrayList<>();
        for (Map<String, Object> entry : parseEntries(coordinatorObj)) {
            String delegator = text(entry.get("delegated_from_username"));
            String delegate = text(entry.get("username"));
            if (delegator.isEmpty() || delegate.isEmpty() || delegator.equalsIgnoreCase(delegate)) {
                continue;
            }
            if (userDelegationResolver.isActiveDelegation(delegator, delegate, UserDelegationResolver.SCOPE_DUTHAO)
                    && result.stream().noneMatch(d -> d.equalsIgnoreCase(delegator))) {
                result.add(delegator);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseEntries(Object coordinatorObj) {
        try {
            if (coordinatorObj instanceof List<?> list) {
                return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
            }
            String json = text(coordinatorObj);
            if (json.isEmpty() || !json.startsWith("[")) {
                return List.of();
            }
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() { });
        } catch (Exception ex) {
            return List.of(); // coordinator_obj hỏng -> không ghi gì, không làm hỏng lần Lưu
        }
    }

    // user_coordinated_movement: chuỗi "a;b" (cách FE/vbden ghi) hoặc mảng JSON.
    private List<String> splitMembers(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(this::text).filter(s -> !s.isEmpty()).toList();
        }
        String raw = text(value);
        if (raw.startsWith("[")) {
            try {
                return OBJECT_MAPPER.readValue(raw, new TypeReference<List<String>>() { });
            } catch (Exception ex) {
                return List.of();
            }
        }
        return Arrays.stream(raw.split(";")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
