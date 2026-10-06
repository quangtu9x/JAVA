package com.td.application.sharedcore;

import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.sharedcore.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Port danhmuc (09/09/2026) — 3 nhánh "viewname" của {@code qlvb/danhmuc/LayDanhSachDungChung}
 * (Domino {@code LayDanhSachDungChung.java}, servlet query TUỲ Ý view theo {@code viewname}+
 * {@code databasetype}) mà {@code LegacyDanhMucController#layDanhSachDungChung}/
 * {@link com.td.application.categories.SearchCategoriesUseCase} KHÔNG phủ tới (nhánh đó CHỈ tra
 * {@code categories} theo form — xem javadoc {@code LayDanhSachDungChungRequest}) — khảo sát
 * 09/09/2026 toàn bộ FE còn gọi URL Domino xác nhận 3 viewname còn lại thực sự dùng:
 *
 * <ul>
 *   <li>{@code vwcocautochuc} — view Domino keyed theo PARENT id, trả các node CON TRỰC TIẾP (mọi
 *   form: agency_level/agency/unit/department) — tương đương
 *   {@link OrganizationRepository#findByParentIdAndDeletedOnIsNull}.</li>
 *   <li>{@code user_by_parentid} (số ít) — user THÀNH VIÊN TRỰC TIẾP của 1 tổ chức (organizationId
 *   == key).</li>
 *   <li>{@code users_by_parentids} (số nhiều) — user thuộc CẢ CÂY CON (tổ chức đó + mọi hậu duệ) —
 *   Domino khớp qua field multi-value {@code parentids} trên doc user (xem
 *   {@code LuuDanhMuc.java#processUserRole"}, cùng khái niệm "identifiers"/"parentids" tổ tiên đã
 *   port ở {@link GetUserOrganizationProfileUseCase}) — Java không lưu {@code parentids} trên
 *   {@link AppUser} nên tính THEO CHIỀU NGƯỢC: duyệt {@code Organization.parentId} XUỐNG từ key để
 *   gom mọi id hậu duệ, rồi tra user theo {@code organizationId IN (...)}.</li>
 * </ul>
 *
 * <p>Field trả về CHỈ đủ dùng cho các FE call site đã khảo sát (dropdown "Cấp ban hành"/"Đơn vị
 * soạn thảo"/"Người soạn thảo"/"Người phụ trách"...), KHÔNG cố tái tạo y hệt field Domino gốc (vd bỏ
 * qua {@code caplanhdao}/{@code clerical} — không nơi nào đọc từ đúng 3 viewname này, khác với
 * {@code vwcocautochuc}/{@code user_by_parentid} dùng ở NƠI KHÁC như {@link
 * GetLegacyOrganizationTreeUseCase}).
 */
@Service
@RequiredArgsConstructor
@Transactional(value = "identityTransactionManager", readOnly = true)
public class GetLegacyOrgLookupForDungChungUseCase {

    private final OrganizationRepository organizationRepository;
    private final AppUserRepository appUserRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    // viewname=vwcocautochuc: cac node CON TRUC TIEP cua key (org id).
    public List<Map<String, Object>> getOrganizationChildren(String parentIdRaw) {
        UUID parentId = parseUuid(parentIdRaw);
        if (parentId == null) {
            return List.of();
        }
        return organizationRepository.findByParentIdAndDeletedOnIsNull(parentId).stream()
            .map(this::toOrganizationRow)
            .toList();
    }

    // viewname=vwcocautochuc_cay (27/09/2026, yeu cau nguoi dung "van ban di -> don vi soan thao: van
    // thu co quan thay toan bo don vi trong co quan, van thu don vi thay don vi + cac phong"): CHINH
    // node key (depth=0) + MOI hau due, thu tu cay (pre-order), anh em sap theo sortOrder roi ten -
    // moi dong kem "depth" de FE thut le. Khac vwcocautochuc (chi con truc tiep, giu nguyen).
    public List<Map<String, Object>> getOrganizationSubtree(String rootIdRaw) {
        UUID rootId = parseUuid(rootIdRaw);
        if (rootId == null) {
            return List.of();
        }
        var root = organizationRepository.findByIdAndDeletedOnIsNull(rootId);
        if (root.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        appendSubtree(root.get(), 0, result, new HashSet<>());
        return result;
    }

    private void appendSubtree(Organization node, int depth, List<Map<String, Object>> result, Set<UUID> visited) {
        if (!visited.add(node.getId())) {
            return;
        }
        Map<String, Object> row = toOrganizationRow(node);
        row.put("depth", depth);
        result.add(row);
        organizationRepository.findByParentIdAndDeletedOnIsNull(node.getId()).stream()
            .sorted(java.util.Comparator.comparingInt(Organization::getSortOrder)
                .thenComparing(child -> safe(child.getName())))
            .forEach(child -> appendSubtree(child, depth + 1, result, visited));
    }

    // viewname=user_by_parentid (truc tiep) / users_by_parentids (ca cay con, de quy).
    public List<Map<String, Object>> getOrganizationMembers(String orgIdRaw, boolean recursive) {
        UUID orgId = parseUuid(orgIdRaw);
        if (orgId == null) {
            return List.of();
        }
        List<UUID> organizationIds = recursive ? collectDescendantIds(orgId) : List.of(orgId);
        // 06/10/2026: danh sach chon nguoi - bo nguoi dang bi Khoa.
        return appUserRepository.findByOrganizationIdInAndDeletedOnIsNull(organizationIds).stream()
            .filter(AppUser::isActive)
            .map(this::toUserRow)
            .toList();
    }

    // 06/10/2026 (port view Domino vwunitleaders - danh_muc_dung_chung.dxl dong 170, truoc goi
    // qlvb/danhmuc/GetLanhDao): SELECT Form="user" & caplanhdao!="" & caplanhdao!="khong" & status!="0",
    // khoa = @Explode(parentids) : level : nhom cap lanh dao (lanhdaocq/lanhdaodv/lanhdaopb -> "lanhdao",
    // con lai giu nguyen vd "lanhdaovp"). parentids = moi to tien -> key[0] khop ca CAY CON. key[1]/[2]
    // rong = khong loc (Domino khoa 1 phan). Chi lay nguoi dang hoat dong (status!="0" = isActive).
    private static final String NOT_A_LEADER = "khong";
    private static final Set<String> GROUPED_LEADER_LEVELS = Set.of("lanhdaocq", "lanhdaodv", "lanhdaopb");
    private static final String GROUPED_LEADER_KEY = "lanhdao";

    public List<Map<String, Object>> getUnitLeaders(String orgIdRaw, String levelRaw, String leaderGroupRaw) {
        UUID orgId = parseUuid(orgIdRaw);
        if (orgId == null) {
            return List.of();
        }
        String level = levelRaw == null ? "" : levelRaw.trim();
        String leaderGroup = leaderGroupRaw == null ? "" : leaderGroupRaw.trim();
        return appUserRepository.findByOrganizationIdInAndDeletedOnIsNull(collectDescendantIds(orgId)).stream()
            .filter(AppUser::isActive)
            .filter(user -> {
                String cap = safe(user.getCapLanhDao()).trim();
                if (cap.isEmpty() || NOT_A_LEADER.equalsIgnoreCase(cap)) {
                    return false;
                }
                if (!level.isEmpty() && !level.equals(user.getLevel() == null ? "" : String.valueOf(user.getLevel()))) {
                    return false;
                }
                String group = GROUPED_LEADER_LEVELS.contains(cap.toLowerCase()) ? GROUPED_LEADER_KEY : cap;
                return leaderGroup.isEmpty() || leaderGroup.equalsIgnoreCase(group);
            })
            .sorted(java.util.Comparator.comparingInt(AppUser::getSortOrder)
                .thenComparing(user -> safe(user.getFullName())))
            .map(this::toLeaderRow)
            .toList();
    }

    private Map<String, Object> toLeaderRow(AppUser user) {
        Map<String, Object> dynamicFields = dynamicDocumentFieldsService.parseDynamicFields(user.getDynamicFieldsJson());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getId() == null ? null : user.getId().toString());
        row.put("name", user.getFullName());
        row.put("username", user.getUsername());
        row.put("level", user.getLevel() == null ? "" : String.valueOf(user.getLevel()));
        row.put("position", dynamicFields.getOrDefault("position", ""));
        row.put("sort_order", user.getSortOrder());
        row.put("caplanhdao", user.getCapLanhDao());
        row.put("delegation_obj", dynamicFields.getOrDefault("delegation_obj", ""));
        return row;
    }

    // BFS xuong Organization.parentId, gom CA key goc - tranh vong lap bang visited set (phong du
    // lieu loi, ve ly thuyet cay khong nen co vong).
    private List<UUID> collectDescendantIds(UUID rootId) {
        List<UUID> result = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            result.add(current);
            for (Organization child : organizationRepository.findByParentIdAndDeletedOnIsNull(current)) {
                queue.add(child.getId());
            }
        }
        return result;
    }

    private Map<String, Object> toOrganizationRow(Organization organization) {
        Map<String, Object> dynamicFields =
            dynamicDocumentFieldsService.parseDynamicFields(organization.getDynamicFieldsJson());

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", organization.getId().toString());
        row.put("name", safe(organization.getName()));
        row.put("identifier", safe(organization.getIdentifier()));
        row.put("form", OrganizationHierarchyRules.resolveNodeType(organization));
        row.put("dbpath", safe(organization.getDbpath()));
        row.put("mailin", asString(dynamicFields.get("mailin")));
        row.put("identifier_other", asString(dynamicFields.get("identifier_other")));
        return row;
    }

    private Map<String, Object> toUserRow(AppUser user) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getId().toString());
        row.put("name", safe(user.getFullName()));
        row.put("username", safe(user.getUsername()));
        return row;
    }

    private UUID parseUuid(String value) {
        try {
            return (value == null || value.isBlank()) ? null : UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
