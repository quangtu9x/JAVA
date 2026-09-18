package com.td.application.sharedcore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.TextNormalizer;
import com.td.application.common.models.Result;
import com.td.domain.sharedcore.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional("identityTransactionManager")
public class CreateOrganizationUseCase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OrganizationRepository organizationRepository;

    public Result<UUID> execute(CreateOrganizationRequest request) {
        try {
            String name = TextNormalizer.normalizeAndSanitize(request.getName());
            String nodeType = OrganizationHierarchyRules.normalizeNodeType(request.getForm());
            String identifier = normalizeCode(request.getIdentifier());
            // agency_level: identifier không bắt buộc → tự sinh từ name nếu FE không gửi
            if ((identifier == null || identifier.isBlank()) && "agency_level".equals(nodeType)) {
                identifier = normalizeCode(name);
            }
            if (identifier == null || identifier.isBlank()) {
                return Result.failure("Identifier không được để trống");
            }
            String legacyParentId = TextNormalizer.normalize(request.getParentid());
            UUID parentId = parseLegacyUuid(legacyParentId);
            String parentName = TextNormalizer.normalizeAndSanitize(request.getParent());

            if (!OrganizationHierarchyRules.isValidNodeType(nodeType)) {
                return Result.failure("Form không hợp lệ. Hỗ trợ: agency_level, agency, unit, department");
            }

            if (organizationRepository.existsByIdentifierAndDeletedOnIsNull(identifier)) {
                return Result.failure("Identifier '" + identifier + "' đã tồn tại");
            }

            // Bugfix 03/09/2026 (báo lỗi người dùng: "level = 0, không có cấp 0, bắt đầu tính từ 1"):
            // node gốc (agency_level không có cha) là CẤP 1 — xem javadoc Organization.level.
            int level = 1;
            String fullPath = name;

            if (parentId == null) {
                if (!OrganizationHierarchyRules.isAllowedRootType(nodeType)) {
                    return Result.failure("Node gốc chỉ cho phép loại agency_level");
                }
            } else {
                var parentOpt = organizationRepository.findByIdAndDeletedOnIsNull(parentId);
                if (parentOpt.isEmpty()) {
                    return Result.failure("Không tìm thấy node cha với ID: " + parentId);
                }

                var parent = parentOpt.get();
                if (!parent.isActive()) {
                    return Result.failure("Node cha đang bị vô hiệu hóa, không thể tạo node con");
                }

                if (parentName == null || parentName.isBlank()) {
                    parentName = parent.getName();
                }

                String parentNodeType = OrganizationHierarchyRules.resolveNodeType(parent);
                if (!OrganizationHierarchyRules.canBeChild(parentNodeType, nodeType)) {
                    return Result.failure("Quan hệ cha-con không hợp lệ: parent=" + parentNodeType + ", child=" + nodeType);
                }

                level = parent.getLevel() + 1;
                fullPath = parent.getFullPath() + " / " + name;
            }

            var organization = new Organization(
                identifier,
                name,
                null,
                parentId,
                nodeType,
                level,
                fullPath,
                request.getSort_order() == null ? 0 : request.getSort_order(),
                request.getSystem(),
                TextNormalizer.normalize(request.getReceiver_id()),
                TextNormalizer.normalizeAndSanitize(request.getReceiver()),
                TextNormalizer.normalizeAndSanitize(request.getReceiver_position()),
                parentName,
                legacyParentId,
                TextNormalizer.normalize(request.getServername()),
                TextNormalizer.normalize(request.getServer_id()),
                TextNormalizer.normalize(request.getIpserver()),
                TextNormalizer.normalize(request.getDbpath())
            );

            if (Boolean.FALSE.equals(request.getIs_active())) {
                organization.update(null, null, null, parentId, nodeType, level, fullPath, null, false);
            }

            // Bugfix 03/09/2026 (cùng lỗi phát hiện ở UpdateOrganizationUseCase — xem javadoc ở đó):
            // use case này cũng CHƯA BAO GIỜ gọi organization.updateLinkedTenant() dù
            // CreateOrganizationRequest.linked_tenant_id đã có sẵn field từ 19/08/2026.
            if (request.getLinked_tenant_id() != null) {
                organization.updateLinkedTenant(request.getLinked_tenant_id());
            }

            // Bugfix 03/09/2026 (cùng lỗi phát hiện ở UpdateOrganizationUseCase — xem javadoc ở đó):
            // use case này cũng CHƯA BAO GIỜ đọc request.getDynamicFields() dù CreateOrganizationRequest
            // đã có field này (mặc định "{}" rỗng, FE luôn gửi kèm — xem organizationApi.js#createOrganizationNode).
            // Node MỚI tạo nên ghi thẳng (không cần merge như Update).
            if (request.getDynamicFields() != null && !request.getDynamicFields().isEmpty()) {
                organization.replaceDynamicFields(toJson(request.getDynamicFields()));
            }

            var saved = organizationRepository.save(organization);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            return Result.failure("Tạo node tổ chức thất bại: " + ex.getMessage());
        }
    }

    private String toJson(java.util.Map<String, String> dynamicFields) {
        try {
            return OBJECT_MAPPER.writeValueAsString(dynamicFields);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        return TextNormalizer.normalize(code).toUpperCase().replaceAll("\\s+", "_");
    }

    private UUID parseLegacyUuid(String legacyId) {
        if (legacyId == null || legacyId.isBlank()) {
            return null;
        }

        String normalized = legacyId.trim();
        try {
            return UUID.fromString(normalized);
        } catch (IllegalArgumentException ignored) {
            String hex = normalized.replace("-", "");
            if (hex.matches("(?i)[0-9a-f]{32}")) {
                String uuid = hex.substring(0, 8) + "-"
                    + hex.substring(8, 12) + "-"
                    + hex.substring(12, 16) + "-"
                    + hex.substring(16, 20) + "-"
                    + hex.substring(20);
                return UUID.fromString(uuid);
            }
            throw new IllegalArgumentException("parentid không đúng định dạng UUID: " + legacyId);
        }
    }
}
