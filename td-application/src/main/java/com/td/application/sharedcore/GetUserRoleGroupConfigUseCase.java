package com.td.application.sharedcore;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.sharedcore.AppPermission;
import com.td.domain.sharedcore.AppRole;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.sharedcore.AppUserRoleGroup;
import com.td.domain.sharedcore.RoleGroupPermission;
import com.td.domain.sharedcore.RoleGroupRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Bổ sung 19/08/2026: bug thật — user CHỈ được gán quyền/menu qua "Nhóm vai trò" (RoleGroup,
// AppUser <-> RoleGroup là N:N, xem AppUserRoleGroup.java — 1 user có thể thuộc NHIỀU nhóm) thì
// GET /qlvb/profile không hề tra cứu tới, nên currentUser.roles/menu_* rỗng, menu chức năng không
// hiện dù đã cấu hình đủ ở "Nhóm vai trò". Use case này tra cứu ĐÚNG 1 lần cho cả 2 nhánh cấu hình độc
// lập của RoleGroup (xem RoleGroup.java), MERGE (union) qua TẤT CẢ nhóm mà user thuộc về:
//   - RoleGroupPermission -> AppPermission.code: quyền gán TRỰC TIẾP cho role group (độc lập vai trò
//     con) — khớp định dạng chuỗi mà CheckRole()/currentUser.roles đang dùng.
//   - RoleGroupRole -> AppRole.dynamicFieldsJson (menu_top/menu_qlvb/menu_vbden/...): mỗi "Vai trò"
//     con mang theo cấu hình menu riêng — 1 role group có thể có NHIỀU vai trò con nên MERGE (union,
//     khử trùng, giữ thứ tự) qua tất cả.
//
// LƯU Ý QUAN TRỌNG (bugfix thứ 2, phát hiện sau khi fix thứ 1 chỉ làm menu_top hiện đúng, các menu_*
// còn lại vẫn không hiện): "menu_top" và các "menu_*" còn lại KHÔNG cùng định dạng lưu trữ trong
// AppRole.dynamicFieldsJson dù javadoc AppRole.java mô tả chung chung "chuỗi nối ;":
//   - "menu_top": FE (vaitro/components/ChiTietModal.jsx, ô "Menu chức năng" — antd Select
//     mode="multiple") thật sự lưu 1 CHUỖI NỐI ";" (vd "Văn bản đến;Quản trị;").
//   - Mọi "menu_*" khác (menu_qlvb, menu_vbden,...): FE (ConfigMenu.jsx render lại chính cây
//     SidebarMenu* kèm checkbox, xem SidebarMenuItem.tsx dòng `JSON.stringify(allValidTitles)`) lưu 1
//     CHUỖI ĐÃ JSON-ENCODE SẴN 1 MẢNG (vd "[\"Người dùng\",\"Vai trò\"]") — xác nhận qua dữ liệu
//     Domino thật đã capture ở legacyAdminProfile.json. Parse nhầm bằng split(";") sẽ ra 1 "mục" duy
//     nhất là cả chuỗi JSON nguyên văn (kèm ngoặc/dấu nháy) — không bao giờ khớp
//     roleArray.includes(name) ở CheckShowMenu() phía FE.
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(value = "identityTransactionManager", readOnly = true)
public class GetUserRoleGroupConfigUseCase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() { };

    private static final String MENU_TOP_FIELD = "menu_top";

    // Đúng 12 field menu_* mà AppRole/ConfigMenu.jsx hỗ trợ (xem AppRole.java javadoc + QLVBCompatAuthController).
    private static final List<String> MENU_FIELD_NAMES = List.of(
        MENU_TOP_FIELD, "menu_qlvb", "menu_vbden", "menu_vbdi", "menu_duthao", "menu_hosocv",
        "menu_vbcddh", "menu_lichct", "menu_chiasetep", "menu_hosoluutru", "menu_tlch", "menu_phieutrinh");

    private final AppUserRepository appUserRepository;
    private final AppUserRoleGroupRepository appUserRoleGroupRepository;
    private final RoleGroupRoleRepository roleGroupRoleRepository;
    private final RoleGroupPermissionRepository roleGroupPermissionRepository;
    private final AppRoleRepository appRoleRepository;
    private final AppPermissionRepository appPermissionRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public UserRoleGroupConfigDto execute(String username) {
        var dto = new UserRoleGroupConfigDto();
        if (username == null || username.isBlank()) {
            return dto;
        }

        UUID appUserId = appUserRepository.findByUsernameAndDeletedOnIsNull(username)
            .map(AppUser::getId)
            .orElse(null);
        if (appUserId == null) {
            return dto;
        }

        // 1 user có thể thuộc NHIỀU "Nhóm vai trò" — merge (union) cấu hình qua TẤT CẢ nhóm.
        List<UUID> roleGroupIds = appUserRoleGroupRepository.findByAppUserIdAndDeletedOnIsNull(appUserId).stream()
            .filter(AppUserRoleGroup::isActive)
            .map(AppUserRoleGroup::getRoleGroupId)
            .toList();
        if (roleGroupIds.isEmpty()) {
            return dto;
        }

        List<UUID> permissionIds = roleGroupPermissionRepository.findByRoleGroupIdInAndDeletedOnIsNull(roleGroupIds)
            .stream()
            .filter(RoleGroupPermission::isActive)
            .map(RoleGroupPermission::getPermissionId)
            .toList();
        List<String> permissionCodes = appPermissionRepository.findByIdInAndDeletedOnIsNull(permissionIds).stream()
            .map(AppPermission::getCode)
            .filter(code -> code != null && !code.isBlank())
            .distinct()
            .toList();
        dto.setPermissionCodes(permissionCodes);

        List<UUID> roleIds = roleGroupRoleRepository.findByRoleGroupIdInAndDeletedOnIsNull(roleGroupIds)
            .stream()
            .filter(RoleGroupRole::isActive)
            .map(RoleGroupRole::getRoleId)
            .toList();
        List<AppRole> roles = appRoleRepository.findByIdInAndDeletedOnIsNull(roleIds);

        Map<String, LinkedHashSet<String>> merged = new LinkedHashMap<>();
        for (String field : MENU_FIELD_NAMES) {
            merged.put(field, new LinkedHashSet<>());
        }
        for (AppRole role : roles) {
            if (!role.isActive()) {
                continue;
            }
            Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(role.getDynamicFieldsJson());
            for (String field : MENU_FIELD_NAMES) {
                Object raw = fields.get(field);
                if (raw instanceof String value && !value.isBlank()) {
                    List<String> items = MENU_TOP_FIELD.equals(field) ? splitSemicolon(value) : parseJsonStringArray(value);
                    merged.get(field).addAll(items);
                }
            }
        }
        Map<String, List<String>> menuFields = new LinkedHashMap<>();
        merged.forEach((field, items) -> menuFields.put(field, List.copyOf(items)));
        dto.setMenuFields(menuFields);

        return dto;
    }

    /** "menu_top" — chuỗi nối ";" (xem javadoc lớp). */
    private List<String> splitSemicolon(String value) {
        List<String> items = new java.util.ArrayList<>();
        for (String item : value.split(";")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }
        return items;
    }

    /**
     * Mọi "menu_*" khác — chuỗi ĐÃ JSON-encode sẵn 1 mảng (xem javadoc lớp). Dữ liệu cũ/lỗi không
     * đúng JSON hợp lệ (vd ai đó lỡ nhập tay) fallback về tách ";" thay vì làm rớt toàn bộ field đó.
     */
    private List<String> parseJsonStringArray(String value) {
        try {
            List<String> parsed = OBJECT_MAPPER.readValue(value, STRING_LIST_TYPE);
            return parsed == null ? List.of() : parsed;
        } catch (Exception ex) {
            log.warn("Khong parse duoc menu JSON array, fallback tach ';': {}", value);
            return splitSemicolon(value);
        }
    }
}
