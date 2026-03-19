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
            String code = normalizeCode(request.getCode());
            String name = TextNormalizer.normalizeAndSanitize(request.getName());
            String description = TextNormalizer.normalizeAndSanitize(request.getDescription());
            String nodeType = OrganizationHierarchyRules.normalizeNodeType(request.getForm());

            if (!OrganizationHierarchyRules.isValidNodeType(nodeType)) {
                return Result.failure("Form không hợp lệ. Hỗ trợ: agency_level, agency, unit, department");
            }

            if (organizationRepository.existsByCodeAndDeletedOnIsNull(code)) {
                return Result.failure("Mã tổ chức '" + code + "' đã tồn tại");
            }

            UUID parentId = request.getParentId();
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

                String parentNodeType = OrganizationHierarchyRules.resolveNodeType(parent);
                if (!OrganizationHierarchyRules.canBeChild(parentNodeType, nodeType)) {
                    return Result.failure("Quan hệ cha-con không hợp lệ: parent=" + parentNodeType + ", child=" + nodeType);
                }

                level = parent.getLevel() + 1;
                fullPath = parent.getFullPath() + " / " + name;
            }

            var organization = new Organization(
                code,
                name,
                description,
                parentId,
                nodeType,
                level,
                fullPath,
                request.getSortOrder()
            );

            if (Boolean.FALSE.equals(request.getIsActive())) {
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
}
