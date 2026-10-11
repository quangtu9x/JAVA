package com.td.application.workcalendars;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.sharedcore.GetUserOrganizationProfileUseCase;
import com.td.application.sharedcore.GetUserRoleGroupConfigUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** Centralizes the same calendar edit rules exposed by the FE menu configuration. */
@Service
@RequiredArgsConstructor
public class WorkCalendarPermissionService {

    private static final String PERSONAL = "canhan";
    private static final String DEPARTMENT = "phong";
    private static final String UNIT = "donvi";
    private static final String AGENCY = "coquan";
    private static final String PROVINCE_LEADERS = "lanhdaotinh";

    private final CurrentUserProvider currentUserProvider;
    private final GetUserRoleGroupConfigUseCase roleConfigUseCase;
    private final GetUserOrganizationProfileUseCase organizationProfileUseCase;

    public boolean canCreate(String rawScope, String targetIdentifier) {
        String username = currentUserProvider.currentUsername().orElse(null);
        if (username == null || username.isBlank()) return false;
        if (PERSONAL.equals(normalize(rawScope))) return true;
        return canEdit(rawScope, targetIdentifier, null);
    }

    public boolean canEdit(String rawScope, String targetIdentifier, String ownerUsername) {
        String username = currentUserProvider.currentUsername().orElse(null);
        if (username == null || username.isBlank()) return false;
        if (username.equals(ownerUsername)) return true;

        String scope = normalize(rawScope);
        if (PERSONAL.equals(scope)) return false;

        List<String> menus = roleConfigUseCase.execute(username).getMenuFields()
            .getOrDefault("menu_lichct", List.of());
        if (PROVINCE_LEADERS.equals(scope)) {
            return menus.contains("Nhập lịch lãnh đạo UBND tỉnh");
        }

        int level;
        String requiredMenu;
        switch (scope) {
            case DEPARTMENT -> { level = 5; requiredMenu = "Nhập lịch phòng"; }
            case UNIT -> { level = 4; requiredMenu = "Nhập lịch đơn vị"; }
            case AGENCY -> { level = 3; requiredMenu = "Nhập lịch cơ quan"; }
            default -> { return false; }
        }
        if (!menus.contains(requiredMenu)) return false;
        var profile = organizationProfileUseCase.execute(username);
        return targetIdentifier != null && targetIdentifier.equals(identifierAtLevel(profile.getIdentifiers(), level));
    }

    private String identifierAtLevel(String identifiers, int level) {
        if (identifiers == null || identifiers.isBlank()) return null;
        String value = identifiers.endsWith(";") ? identifiers.substring(0, identifiers.length() - 1) : identifiers;
        String[] parts = value.split(";");
        int index = parts.length - level;
        return index < 0 || index >= parts.length ? null : parts[index].trim();
    }

    private String normalize(String scope) {
        return scope == null ? "" : scope.trim().toLowerCase();
    }
}
