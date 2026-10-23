package com.td.application.vbden;

import com.fasterxml.jackson.databind.JsonNode;
import com.td.application.sharedcore.GetLegacyOrgLookupForDungChungUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 11/10/2026 (báo lỗi demo9 skhcn "thu hồi" — đọc dữ liệu thật qlvbv6): quy tắc "người/đơn vị trong
 * snapshot lần chuyển còn chưa xử lý" dùng chung cho {@link GetNguoiThuHoiUseCase} (danh sách) và
 * {@link ThuHoiVanBanUseCase} (thu hồi thật).
 *
 * <p>Trước đây (đúng Domino getJsonArray) node ĐƠN VỊ khớp chỉ vì mã định danh còn trong
 * {@code unit_*} — nhưng mã đơn vị KHÔNG bị gỡ khi người trong đơn vị xử lý xong (KetThucXuLy chỉ đụng
 * user_*). Hậu quả thật: VB 33/BC-SKHCN demo15 (đại diện Phòng QTHT) đã kết thúc vẫn hiện "thu hồi được";
 * VB 10/BC-SKHCN demo15 đã chuyển tiếp demo17 cùng phòng → thu hồi phòng nhưng demo17 vẫn giữ văn bản.</p>
 *
 * <p>Nay node đơn vị (có {@code identifier}) chỉ "còn chờ" khi mã còn trong {@code unit_*} VÀ còn ít nhất
 * 1 thành viên của đơn vị (cây con theo {@code id} tổ chức trong snapshot, cộng {@code user_receiver})
 * trong {@code user_*} tương ứng; thu hồi đơn vị gỡ luôn các thành viên đó. Snapshot cũ không có
 * {@code id} tổ chức → chỉ dựa vào {@code user_receiver}; không có cả hai → giữ quy tắc cũ.</p>
 */
@Component
@RequiredArgsConstructor
public class VbdenRevokeRecipientMatcher {

    private final GetLegacyOrgLookupForDungChungUseCase orgLookup;

    /** Cache thành viên đơn vị theo id tổ chức — tạo mới mỗi lần thực thi use case. */
    public Map<String, Set<String>> newMemberCache() {
        return new HashMap<>();
    }

    public static String username(JsonNode node) {
        return node.hasNonNull("username") ? node.path("username").asText() : node.path("user_receiver").asText("");
    }

    public static boolean isUnitNode(JsonNode node) {
        return !node.path("identifier").asText("").isBlank();
    }

    /** Người dùng (cá nhân hoặc thành viên đơn vị) đang giữ văn bản thuộc node này; rỗng = không còn chờ. */
    public Set<String> pendingHolders(JsonNode node, List<String> pendingUsers, List<String> pendingUnits,
                                      Map<String, Set<String>> memberCache) {
        Set<String> holders = new LinkedHashSet<>();
        String username = username(node);
        if (!isUnitNode(node)) {
            if (!username.isBlank() && pendingUsers.contains(username)) {
                holders.add(username);
            }
            return holders;
        }
        String identifier = node.path("identifier").asText("");
        if (!pendingUnits.contains(identifier)) {
            if (!username.isBlank() && pendingUsers.contains(username)) {
                holders.add(username);
            }
            return holders;
        }
        Set<String> members = new LinkedHashSet<>(unitMembers(node.path("id").asText(""), memberCache));
        if (!username.isBlank()) {
            members.add(username);
        }
        if (members.isEmpty()) {
            // Snapshot cũ thiếu cả id tổ chức lẫn người đại diện: giữ quy tắc Domino (chỉ theo mã đơn vị).
            holders.add("");
            return holders;
        }
        for (String member : members) {
            if (pendingUsers.contains(member)) {
                holders.add(member);
            }
        }
        return holders;
    }

    private Set<String> unitMembers(String orgId, Map<String, Set<String>> memberCache) {
        if (orgId == null || orgId.isBlank()) {
            return Set.of();
        }
        return memberCache.computeIfAbsent(orgId, id -> {
            Set<String> usernames = new LinkedHashSet<>();
            try {
                for (Map<String, Object> row : orgLookup.getOrganizationMembers(id, true)) {
                    Object value = row.get("username");
                    if (value != null && !value.toString().isBlank()) {
                        usernames.add(value.toString());
                    }
                }
            } catch (RuntimeException ex) {
                // id không phải tổ chức (dữ liệu lạ) → coi như không biết thành viên.
            }
            return usernames;
        });
    }
}
