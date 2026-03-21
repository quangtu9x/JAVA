package com.td.application.sharedcore;

import com.td.domain.sharedcore.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetLegacyOrganizationTreeUseCase {

    private static final String DEFAULT_JEDIS_KEY = "qlvb_CoCauToChuc_#";

    private final OrganizationRepository organizationRepository;

    public LegacyOrganizationTreeResponse execute(String jedisKey) {
        String normalizedJedisKey = normalizeJedisKey(jedisKey);

        try {
            List<Organization> organizations = loadOrganizationsForTree();
            List<LegacyOrganizationTreeNode> nodes = buildTree(organizations);
            return LegacyOrganizationTreeResponse.success(nodes, normalizedJedisKey);
        } catch (Exception ex) {
            return LegacyOrganizationTreeResponse.failure(
                "Load organization tree failed",
                normalizedJedisKey,
                ex.getMessage()
            );
        }
    }

    private List<Organization> loadOrganizationsForTree() {
        List<Organization> organizations = new ArrayList<>();

        int pageNumber = 0;
        int pageSize = 200;
        while (true) {
            var request = new SearchOrganizationsRequest();
            request.setPageNumber(pageNumber);
            request.setPageSize(pageSize);
            request.setIsActive(true);
            request.setSortBy("sortOrder");
            request.setSortDirection("asc");

            var pageable = PageRequest.of(
                pageNumber,
                pageSize,
                Sort.by(
                    Sort.Order.asc("level"),
                    Sort.Order.asc("sortOrder"),
                    Sort.Order.asc("name")
                )
            );

            var page = organizationRepository.search(request, pageable);
            organizations.addAll(page.getContent());

            if (page.isLast()) {
                break;
            }

            pageNumber++;
        }

        return organizations;
    }

    private List<LegacyOrganizationTreeNode> buildTree(List<Organization> organizations) {
        if (organizations == null || organizations.isEmpty()) {
            return List.of();
        }

        Map<UUID, Organization> byId = new HashMap<>();
        Map<UUID, List<Organization>> childrenByParent = new HashMap<>();

        for (Organization organization : organizations) {
            byId.put(organization.getId(), organization);
            childrenByParent
                .computeIfAbsent(organization.getParentId(), key -> new ArrayList<>())
                .add(organization);
        }

        Comparator<Organization> comparator = Comparator
            .comparingInt(Organization::getSortOrder)
            .thenComparing(organization -> safe(organization.getName()));

        for (List<Organization> children : childrenByParent.values()) {
            children.sort(comparator);
        }

        List<Organization> roots = organizations.stream()
            .filter(organization -> organization.getParentId() == null || !byId.containsKey(organization.getParentId()))
            .sorted(comparator)
            .toList();

        List<LegacyOrganizationTreeNode> result = new ArrayList<>();
        for (Organization root : roots) {
            result.add(buildNode(root, childrenByParent, new NodeContext(null, null, null)));
        }

        return result;
    }

    private LegacyOrganizationTreeNode buildNode(
            Organization organization,
            Map<UUID, List<Organization>> childrenByParent,
            NodeContext context) {
        String form = resolveForm(organization);
        NodeContext nextContext = nextContext(organization, form, context);

        List<LegacyOrganizationTreeNode> children = childrenByParent
            .getOrDefault(organization.getId(), List.of())
            .stream()
            .map(child -> buildNode(child, childrenByParent, nextContext))
            .toList();

        return new LegacyOrganizationTreeNode(
            organization.getId().toString(),
            safe(organization.getName()),
            form,
            buildNodeData(organization, form, nextContext),
            children
        );
    }

    private Map<String, String> buildNodeData(Organization organization, String form, NodeContext context) {
        String orgId = organization.getId().toString();
        String identifier = safe(organization.getIdentifier());
        String dbPath = safe(organization.getDbpath());

        Map<String, String> data = new LinkedHashMap<>();
        switch (form) {
            case "agency":
                data.put("identifier", identifier);
                data.put("identifier_other", "");
                data.put("user_receiver", "");
                data.put("agency_id", orgId);
                data.put("dbpath", dbPath);
                break;
            case "unit":
                data.put("identifier", identifier);
                data.put("identifier_other", safe(context.agencyIdentifier()));
                data.put("user_receiver", "");
                data.put("agency_id", safe(context.agencyId()));
                data.put("unit_id", orgId);
                data.put("dbpath", dbPath);
                break;
            case "department":
                data.put("department_id", orgId);
                data.put("unit_id", safe(context.unitId()));
                data.put("agency_id", safe(context.agencyId()));
                data.put("identifier", identifier);
                data.put("user_receiver", "");
                data.put("dbpath", dbPath);
                break;
            default:
                data.put("identifier", identifier);
                data.put("identifier_other", "");
                data.put("user_receiver", "");
                data.put("dbpath", dbPath);
                break;
        }

        return data;
    }

    private String resolveForm(Organization organization) {
        String nodeType = OrganizationHierarchyRules.resolveNodeType(organization);
        return switch (nodeType) {
            case OrganizationHierarchyRules.AGENCY_LEVEL -> "agency_level";
            case OrganizationHierarchyRules.AGENCY -> "agency";
            case OrganizationHierarchyRules.UNIT -> "unit";
            case OrganizationHierarchyRules.DEPARTMENT -> "department";
            default -> "agency_level";
        };
    }

    private NodeContext nextContext(Organization organization, String form, NodeContext current) {
        String agencyId = current.agencyId();
        String agencyIdentifier = current.agencyIdentifier();
        String unitId = current.unitId();

        if ("agency".equals(form)) {
            agencyId = organization.getId().toString();
            agencyIdentifier = safe(organization.getIdentifier());
            unitId = null;
        } else if ("unit".equals(form)) {
            unitId = organization.getId().toString();
        }

        return new NodeContext(agencyId, agencyIdentifier, unitId);
    }

    private String normalizeJedisKey(String jedisKey) {
        if (jedisKey == null || jedisKey.isBlank()) {
            return DEFAULT_JEDIS_KEY;
        }
        return jedisKey.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record NodeContext(String agencyId, String agencyIdentifier, String unitId) {
    }
}
