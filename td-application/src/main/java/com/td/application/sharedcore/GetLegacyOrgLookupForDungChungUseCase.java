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

    // viewname=user_by_parentid (truc tiep) / users_by_parentids (ca cay con, de quy).
    public List<Map<String, Object>> getOrganizationMembers(String orgIdRaw, boolean recursive) {
        UUID orgId = parseUuid(orgIdRaw);
        if (orgId == null) {
            return List.of();
        }
        List<UUID> organizationIds = recursive ? collectDescendantIds(orgId) : List.of(orgId);
        return appUserRepository.findByOrganizationIdInAndDeletedOnIsNull(organizationIds).stream()
            .map(this::toUserRow)
            .toList();
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
