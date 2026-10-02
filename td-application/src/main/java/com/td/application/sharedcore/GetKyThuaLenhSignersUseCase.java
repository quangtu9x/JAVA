package com.td.application.sharedcore;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.sharedcore.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 01/10/2026 (người dùng báo "Hình thức ký → Ký thừa lệnh chưa thấy danh sách người ký (skhcn)", dự thảo
 * + văn bản đi): port {@code qlvb/danhmuc/publish?get=kythualenh} — Domino {@code publish.java#ky_thua_lenh}.
 * Trước đó Java chỉ hỗ trợ {@code get=capbanhanh} (controller trả 400) nên dropdown luôn trống.
 *
 * <p><b>Thuật toán Domino</b> (2 nhóm, nối tiếp):</p>
 * <ol>
 *   <li>View {@code lanhdao_ky_thu_lenh_by_level_parentids} khoá {@code capbanhanhid}: user
 *   {@code caplanhdao="lanhdaovp" & status!="0" & level!=""}.</li>
 *   <li>{@code thualenh_parentid} = tổ chức NGAY DƯỚI cấp ban hành trên chuỗi {@code parentids} của
 *   CHÍNH người đang soạn ({@code getPreviousId} — {@code parentids} lưu LÁ→GỐC nên phần tử đứng
 *   trước là con); nếu tổ chức đó là {@code agency_level} thì xuống thêm 1 bậc. View
 *   {@code lanhdao_by_level_parentids} khoá {@code thualenh_parentid}: user
 *   {@code caplanhdao != "" & != "khong" & status!="0"}.</li>
 * </ol>
 *
 * <p><b>Khoá của 2 view</b> ({@code parentid_level}): {@code parts[@Elements(parts) - level + 1]} với
 * {@code parts} = {@code parentids} LÁ→GỐC của user ứng viên — tức tổ tiên ở ĐỘ SÂU TUYỆT ĐỐI
 * {@code level} (gốc = 1); {@code level} ngoài khoảng → tổ chức của chính user. Java tính lại theo
 * chiều ngược (Organization.parentId), không lưu {@code parentids} trên AppUser — xem
 * {@link GetLegacyOrgLookupForDungChungUseCase} cùng cách làm.</p>
 *
 * <p>Domino khai báo {@code listUser} để loại trùng nhưng không bao giờ ghi vào — Java loại trùng thật
 * theo username (1 người không xuất hiện 2 lần trong dropdown).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(value = "identityTransactionManager", readOnly = true)
public class GetKyThuaLenhSignersUseCase {

    static final String CAP_LANH_DAO_VAN_PHONG = "lanhdaovp";
    private static final String NOT_A_LEADER_VALUE = "khong";

    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;
    private final OrganizationRepository organizationRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<List<Map<String, Object>>> execute(String capBanHanhIdRaw) {
        UUID capBanHanhId = parseUuid(capBanHanhIdRaw);
        if (capBanHanhId == null) {
            return Result.success(List.of());
        }
        Map<UUID, Organization> orgCache = new HashMap<>();
        Map<String, Object> seen = new LinkedHashMap<>();
        List<Map<String, Object>> result = new ArrayList<>();

        // 01/10/2026: cau hinh rieng tren to chuc cap ban hanh (thay nhanh HĐND gan cung o FE - UNID
        // Domino + so ten) - co cau hinh thi DUNG RIENG nguon do, khong cong them thuat toan mac dinh
        // (dung hanh vi nhanh HĐND cu: thay the, khong bo sung).
        Organization capBanHanh = loadOrganization(capBanHanhId, orgCache);
        if (capBanHanh != null) {
            Map<String, Object> cfg = dynamicDocumentFieldsService.parseDynamicFields(capBanHanh.getDynamicFieldsJson());
            String source = trim(asString(cfg.get(OrganizationConfigKeys.KY_THUA_LENH_SOURCE)));
            if (OrganizationConfigKeys.KY_THUA_LENH_SOURCE_LEADERS_OF_ORG.equals(source)) {
                UUID sourceOrgId = parseUuid(asString(cfg.get(OrganizationConfigKeys.KY_THUA_LENH_SOURCE_ORG_ID)));
                appendDirectMembers(result, seen, List.of(sourceOrgId != null ? sourceOrgId : capBanHanhId), this::isLeader);
                return Result.success(result);
            }
            if (OrganizationConfigKeys.KY_THUA_LENH_SOURCE_POSITION_IN_ORGS.equals(source)) {
                List<UUID> orgIds = parseUuidList(asString(cfg.get(OrganizationConfigKeys.KY_THUA_LENH_SOURCE_ORG_IDS)));
                String position = normalizePosition(asString(cfg.get(OrganizationConfigKeys.KY_THUA_LENH_POSITION)));
                if (!orgIds.isEmpty() && !position.isEmpty()) {
                    appendDirectMembers(result, seen, orgIds,
                        user -> position.equals(normalizePosition(asString(dynamicDocumentFieldsService
                            .parseDynamicFields(user.getDynamicFieldsJson()).get("position")))));
                }
                return Result.success(result);
            }
        }

        // Nhom 1: lanh dao van phong cua cap ban hanh.
        appendLeaders(result, seen, capBanHanhId, orgCache,
            user -> CAP_LANH_DAO_VAN_PHONG.equalsIgnoreCase(trim(user.getCapLanhDao())) && user.getLevel() != null);

        // Nhom 2: lanh dao cua to chuc ngay duoi cap ban hanh tren chuoi to chuc cua nguoi dang soan.
        UUID thuaLenhOrgId = resolveChildOnCurrentUserChain(capBanHanhId, orgCache);
        if (thuaLenhOrgId != null) {
            appendLeaders(result, seen, thuaLenhOrgId, orgCache, this::isLeader);
        }
        return Result.success(result);
    }

    private void appendLeaders(List<Map<String, Object>> result, Map<String, Object> seen, UUID keyOrgId,
                               Map<UUID, Organization> orgCache, java.util.function.Predicate<AppUser> roleFilter) {
        // Khoa la to tien cua to chuc user -> user ung vien chi co the nam trong CAY CON cua keyOrgId.
        List<AppUser> candidates = appUserRepository.findByOrganizationIdInAndDeletedOnIsNull(
            collectSubtreeIds(keyOrgId, orgCache));
        candidates.stream()
            .filter(AppUser::isActive)
            .filter(roleFilter)
            .filter(user -> keyOrgId.equals(levelKey(user, orgCache)))
            .sorted(Comparator.comparingInt(AppUser::getSortOrder)
                .thenComparing(user -> trim(user.getFullName())))
            .forEach(user -> {
                String username = trim(user.getUsername()).toLowerCase();
                if (!username.isEmpty() && seen.putIfAbsent(username, Boolean.TRUE) == null) {
                    result.add(toLeaderNode(user));
                }
            });
    }

    // Thanh vien TRUC TIEP cua cac to chuc (dung view Domino vwuserleaderbyparentid: khoa = to chuc cu the
    // nhat cua user), giu thu tu to chuc da cau hinh, trong moi to chuc sap sort_order roi ten.
    private void appendDirectMembers(List<Map<String, Object>> result, Map<String, Object> seen, List<UUID> orgIds,
                                     java.util.function.Predicate<AppUser> filter) {
        for (UUID orgId : orgIds) {
            appUserRepository.findByOrganizationIdInAndDeletedOnIsNull(List.of(orgId)).stream()
                .filter(AppUser::isActive)
                .filter(filter)
                .sorted(Comparator.comparingInt(AppUser::getSortOrder)
                    .thenComparing(user -> trim(user.getFullName())))
                .forEach(user -> {
                    String username = trim(user.getUsername()).toLowerCase();
                    if (!username.isEmpty() && seen.putIfAbsent(username, Boolean.TRUE) == null) {
                        result.add(toLeaderNode(user));
                    }
                });
        }
    }

    // So chuc vu khong phan biet hoa thuong/khoang trang thua ("Trưởng Phòng" == "trưởng phòng").
    private static String normalizePosition(String value) {
        return trim(value).replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    private static List<UUID> parseUuidList(String raw) {
        List<UUID> ids = new ArrayList<>();
        for (String part : trim(raw).split("[;,]")) {
            UUID id = parseUuid(part);
            if (id != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    // parentid_level cua view Domino: to tien o do sau tuyet doi = user.level (goc = 1); ngoai khoang ->
    // to chuc cua chinh user.
    UUID levelKey(AppUser user, Map<UUID, Organization> orgCache) {
        if (user.getOrganizationId() == null) {
            return null;
        }
        List<Organization> rootToLeaf = ancestryRootToLeaf(user.getOrganizationId(), orgCache);
        if (rootToLeaf.isEmpty()) {
            return null;
        }
        Integer level = user.getLevel();
        if (level != null && level > 0 && level <= rootToLeaf.size()) {
            return rootToLeaf.get(level - 1).getId();
        }
        return rootToLeaf.get(rootToLeaf.size() - 1).getId();
    }

    // getPreviousId(parentids LA->GOC, capbanhanhid) = to chuc CON cua cap ban hanh tren chuoi cua nguoi
    // dang dang nhap; neu la agency_level thi xuong them 1 bac (Domino docCC.Form=="agency_level").
    private UUID resolveChildOnCurrentUserChain(UUID capBanHanhId, Map<UUID, Organization> orgCache) {
        Optional<AppUser> me = currentUserProvider.currentUsername()
            .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull);
        if (me.isEmpty() || me.get().getOrganizationId() == null) {
            return null;
        }
        List<Organization> rootToLeaf = ancestryRootToLeaf(me.get().getOrganizationId(), orgCache);
        for (int index = 0; index < rootToLeaf.size(); index++) {
            if (!rootToLeaf.get(index).getId().equals(capBanHanhId)) {
                continue;
            }
            int childIndex = index + 1;
            if (childIndex < rootToLeaf.size()
                    && OrganizationHierarchyRules.AGENCY_LEVEL.equals(rootToLeaf.get(childIndex).getNodeType())) {
                childIndex++;
            }
            return childIndex < rootToLeaf.size() ? rootToLeaf.get(childIndex).getId() : null;
        }
        return null;
    }

    private List<Organization> ancestryRootToLeaf(UUID organizationId, Map<UUID, Organization> orgCache) {
        List<Organization> chain = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        UUID currentId = organizationId;
        while (currentId != null && visited.add(currentId)) {
            Organization org = loadOrganization(currentId, orgCache);
            if (org == null) {
                break;
            }
            chain.add(org);
            currentId = org.getParentId();
        }
        Collections.reverse(chain);
        return chain;
    }

    private List<UUID> collectSubtreeIds(UUID rootId, Map<UUID, Organization> orgCache) {
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
                orgCache.putIfAbsent(child.getId(), child);
                queue.add(child.getId());
            }
        }
        return result;
    }

    private Organization loadOrganization(UUID id, Map<UUID, Organization> orgCache) {
        if (orgCache.containsKey(id)) {
            return orgCache.get(id);
        }
        Organization org = organizationRepository.findByIdAndDeletedOnIsNull(id).orElse(null);
        orgCache.put(id, org);
        return org;
    }

    private boolean isLeader(AppUser user) {
        String capLanhDao = trim(user.getCapLanhDao());
        return !capLanhDao.isEmpty() && !NOT_A_LEADER_VALUE.equalsIgnoreCase(capLanhDao);
    }

    // Cung hinh dang ViewQueryService#toLeaderFields + "level" (Domino buildLeaderNode co field nay).
    private Map<String, Object> toLeaderNode(AppUser user) {
        Map<String, Object> dynamicFields = dynamicDocumentFieldsService.parseDynamicFields(user.getDynamicFieldsJson());
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", user.getId());
        node.put("name", user.getFullName());
        node.put("username", user.getUsername());
        node.put("sort_order", user.getSortOrder());
        node.put("position", dynamicFields.getOrDefault("position", ""));
        node.put("caplanhdao", user.getCapLanhDao());
        node.put("level", user.getLevel() == null ? "" : String.valueOf(user.getLevel()));
        node.put("delegation_obj", dynamicFields.getOrDefault("delegation_obj", ""));
        return node;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static UUID parseUuid(String value) {
        try {
            return (value == null || value.isBlank()) ? null : UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
