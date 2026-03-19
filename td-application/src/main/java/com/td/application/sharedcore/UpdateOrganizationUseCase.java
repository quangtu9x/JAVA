package com.td.application.sharedcore;

import com.td.application.common.TextNormalizer;
import com.td.application.common.models.Result;
import com.td.domain.sharedcore.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UpdateOrganizationUseCase {

    private final OrganizationRepository organizationRepository;

    public Result<UUID> execute(UUID id, UpdateOrganizationRequest request) {
        try {
            var currentOpt = organizationRepository.findByIdAndDeletedOnIsNull(id);
            if (currentOpt.isEmpty()) {
                return Result.failure("Không tìm thấy tổ chức với ID: " + id);
            }

            var current = currentOpt.get();

            String code = request.getCode() != null
                ? TextNormalizer.normalize(request.getCode()).toUpperCase().replaceAll("\\s+", "_")
                : null;
            String name = TextNormalizer.normalizeAndSanitize(request.getName());
            String description = TextNormalizer.normalizeAndSanitize(request.getDescription());

            if (code != null && !code.equals(current.getCode())
                    && organizationRepository.existsByCodeAndIdNotAndDeletedOnIsNull(code, id)) {
                return Result.failure("Mã tổ chức '" + code + "' đã được sử dụng");
            }

            UUID effectiveParentId = request.isUpdateParent()
                ? request.getParentId()
                : current.getParentId();

            if (effectiveParentId != null && effectiveParentId.equals(id)) {
                return Result.failure("Tổ chức không thể là cha của chính nó");
            }

            if (createsCycle(id, effectiveParentId)) {
                return Result.failure("Quan hệ cha-con không hợp lệ: phát hiện vòng lặp trong cây tổ chức");
            }

            String effectiveNodeType = OrganizationHierarchyRules.normalizeNodeType(request.getForm());

            if (!OrganizationHierarchyRules.isValidNodeType(effectiveNodeType)) {
                return Result.failure("Form không hợp lệ. Hỗ trợ: agency_level, agency, unit, department");
            }

            String effectiveName = name != null ? name : current.getName();
            int newLevel = 0;
            String newFullPath = effectiveName;

            if (effectiveParentId == null) {
                if (!OrganizationHierarchyRules.isAllowedRootType(effectiveNodeType)) {
                    return Result.failure("Node gốc chỉ cho phép loại agency_level");
                }
            } else {
                var parentOpt = organizationRepository.findByIdAndDeletedOnIsNull(effectiveParentId);
                if (parentOpt.isEmpty()) {
                    return Result.failure("Không tìm thấy node cha với ID: " + effectiveParentId);
                }

                var parent = parentOpt.get();
                if (!parent.isActive()) {
                    return Result.failure("Node cha đang bị vô hiệu hóa, không thể gán node con");
                }

                String parentNodeType = OrganizationHierarchyRules.resolveNodeType(parent);
                if (!OrganizationHierarchyRules.canBeChild(parentNodeType, effectiveNodeType)) {
                    return Result.failure("Quan hệ cha-con không hợp lệ: parent=" + parentNodeType + ", child=" + effectiveNodeType);
                }

                newLevel = parent.getLevel() + 1;
                newFullPath = parent.getFullPath() + " / " + effectiveName;
            }

            Organization invalidChild = findFirstIncompatibleChild(id, effectiveNodeType);
            if (invalidChild != null) {
                String childType = OrganizationHierarchyRules.resolveNodeType(invalidChild);
                return Result.failure("Không thể đổi node hiện tại vì node con '" + invalidChild.getName()
                    + "' có loại " + childType + " không tương thích với cha loại " + effectiveNodeType);
            }

            current.update(
                code,
                name,
                description,
                effectiveParentId,
                effectiveNodeType,
                newLevel,
                newFullPath,
                request.getSortOrder(),
                request.getIsActive()
            );

            var saved = organizationRepository.save(current);
            refreshDescendants(saved);

            return Result.success(saved.getId());
        } catch (Exception ex) {
            return Result.failure("Cập nhật tổ chức thất bại: " + ex.getMessage());
        }
    }

    private boolean createsCycle(UUID nodeId, UUID candidateParentId) {
        UUID currentParentId = candidateParentId;
        while (currentParentId != null) {
            if (currentParentId.equals(nodeId)) {
                return true;
            }

            var parentOpt = organizationRepository.findByIdAndDeletedOnIsNull(currentParentId);
            if (parentOpt.isEmpty()) {
                return false;
            }

            currentParentId = parentOpt.get().getParentId();
        }
        return false;
    }

    private Organization findFirstIncompatibleChild(UUID nodeId, String parentNodeType) {
        List<Organization> children = organizationRepository.findByParentIdAndDeletedOnIsNull(nodeId);
        for (Organization child : children) {
            String childNodeType = OrganizationHierarchyRules.resolveNodeType(child);
            if (!OrganizationHierarchyRules.canBeChild(parentNodeType, childNodeType)) {
                return child;
            }
        }
        return null;
    }

    private void refreshDescendants(Organization parent) {
        List<Organization> children = organizationRepository.findByParentIdAndDeletedOnIsNull(parent.getId());
        for (Organization child : children) {
            String childNodeType = OrganizationHierarchyRules.resolveNodeType(child);
            int childLevel = parent.getLevel() + 1;
            String childFullPath = parent.getFullPath() + " / " + child.getName();

            child.update(
                null,
                null,
                null,
                parent.getId(),
                childNodeType,
                childLevel,
                childFullPath,
                null,
                null
            );

            Organization savedChild = organizationRepository.save(child);
            refreshDescendants(savedChild);
        }
    }
}
