package com.td.application.sharedcore;

import com.td.application.common.TextNormalizer;
import com.td.application.common.models.Result;
import com.td.domain.sharedcore.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CreateOrganizationUseCase {

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

            int level = 0;
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

            var saved = organizationRepository.save(organization);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            return Result.failure("Tạo node tổ chức thất bại: " + ex.getMessage());
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
