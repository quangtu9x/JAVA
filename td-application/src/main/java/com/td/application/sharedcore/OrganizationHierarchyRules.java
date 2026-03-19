package com.td.application.sharedcore;

import com.td.domain.sharedcore.Organization;

import java.util.Locale;

final class OrganizationHierarchyRules {

    static final String AGENCY_LEVEL = "agency_level";
    static final String AGENCY = "agency";
    static final String UNIT = "unit";
    static final String DEPARTMENT = "department";

    private OrganizationHierarchyRules() {
    }

    static String normalizeNodeType(String nodeType) {
        if (nodeType == null) {
            return null;
        }
        return nodeType.trim().toLowerCase(Locale.ROOT);
    }

    static String resolveNodeType(Organization organization) {
        String explicit = normalizeNodeType(organization.getNodeType());
        if (isValidNodeType(explicit)) {
            return explicit;
        }

        int level = organization.getLevel();
        if (level <= 1) {
            return AGENCY_LEVEL;
        }
        if (level == 2) {
            return AGENCY;
        }
        if (level == 3) {
            return UNIT;
        }
        return DEPARTMENT;
    }

    static boolean isValidNodeType(String nodeType) {
        return AGENCY_LEVEL.equals(nodeType)
            || AGENCY.equals(nodeType)
            || UNIT.equals(nodeType)
            || DEPARTMENT.equals(nodeType);
    }

    static boolean isAllowedRootType(String nodeType) {
        return AGENCY_LEVEL.equals(nodeType);
    }

    static boolean canBeChild(String parentNodeType, String childNodeType) {
        if (!isValidNodeType(parentNodeType) || !isValidNodeType(childNodeType)) {
            return false;
        }

        return switch (parentNodeType) {
            case AGENCY_LEVEL -> AGENCY_LEVEL.equals(childNodeType) || AGENCY.equals(childNodeType);
            case AGENCY -> UNIT.equals(childNodeType);
            case UNIT -> DEPARTMENT.equals(childNodeType);
            case DEPARTMENT -> false;
            default -> false;
        };
    }
}
