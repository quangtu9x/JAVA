package com.td.application.sharedcore;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.TextNormalizer;
import com.td.application.common.models.Result;
import com.td.domain.sharedcore.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional("identityTransactionManager")
public class UpdateOrganizationUseCase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OrganizationRepository organizationRepository;

    public Result<UUID> execute(UUID id, UpdateOrganizationRequest request) {
        try {
            var currentOpt = organizationRepository.findByIdAndDeletedOnIsNull(id);
            if (currentOpt.isEmpty()) {
                return Result.failure("Không tìm thấy tổ chức với ID: " + id);
            }

            var current = currentOpt.get();

            String identifier = request.getIdentifier() != null
                ? TextNormalizer.normalize(request.getIdentifier()).toUpperCase().replaceAll("\\s+", "_")
                : null;
            String name = TextNormalizer.normalizeAndSanitize(request.getName());
            String parentName = TextNormalizer.normalizeAndSanitize(request.getParent());
            String legacyParentId = request.getParentid() == null ? null : TextNormalizer.normalize(request.getParentid());

            if (identifier != null && !identifier.equals(current.getIdentifier())
                    && organizationRepository.existsByIdentifierAndIdNotAndDeletedOnIsNull(identifier, id)) {
                return Result.failure("Identifier '" + identifier + "' đã được sử dụng");
            }

            boolean updateParent = request.getParentid() != null;
            UUID effectiveParentId = updateParent
                ? parseLegacyUuid(legacyParentId)
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
            // Bugfix 03/09/2026 (báo lỗi người dùng: "level = 0, không có cấp 0, bắt đầu tính từ 1"):
            // node gốc (không có cha) là CẤP 1 — xem javadoc Organization.level.
            int newLevel = 1;
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

                if (parentName == null || parentName.isBlank()) {
                    parentName = parent.getName();
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

            if (updateParent && effectiveParentId == null) {
                parentName = "";
                legacyParentId = "";
            }

            current.update(
                identifier,
                name,
                null,
                effectiveParentId,
                effectiveNodeType,
                newLevel,
                newFullPath,
                request.getSort_order(),
                request.getIs_active()
            );

            current.updateLegacyData(
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

            // Bugfix 03/09/2026 (báo lỗi người dùng, tự test bằng payload {"linked_tenant_id":
            // "H05_02"} sau khi khởi tạo 1 đơn vị mới — giá trị không lưu): CÙNG DẠNG lỗi với
            // dynamicFields ở dưới — Organization.updateLinkedTenant() đã có sẵn (kèm javadoc đúng
            // ngữ nghĩa: null = không đổi, chuỗi rỗng = gỡ liên kết) nhưng use case này CHƯA BAO GIỜ
            // gọi tới, dù UpdateOrganizationRequest.linked_tenant_id đã có sẵn field từ 19/08/2026.
            if (request.getLinked_tenant_id() != null) {
                current.updateLinkedTenant(request.getLinked_tenant_id());
            }

            // Bugfix 03/09/2026 (báo lỗi người dùng, tự test bằng payload {"dynamicFields":
            // {"clerical":"0"}} — xem chi tiết vẫn không đổi VÀ trong CSDL cũng không có): use case
            // này CHƯA BAO GIỜ đọc request.getDynamicFields() — dù UpdateOrganizationRequest.dynamicFields
            // đã có sẵn field + javadoc mô tả đúng ngữ nghĩa MERGE (null = không đổi, có giá trị thì
            // merge vào field động hiện có), và FE (organizationApi.js#updateOrganizationNode) đã tách
            // đúng payload gửi field này cho MỌI field kiểu Domino cũ không có cột tường minh
            // (mailin, clerical, identifier_other, short_name, roles, cfg_*...) — nghĩa là TOÀN BỘ
            // các field đó chưa từng lưu được từ khi port sang Java, dù BE trả 200/thành công thật (
            // Organization.replaceDynamicFields() đã có sẵn, chỉ là không có chỗ nào gọi tới). Merge
            // (không ghi đè field không được nhắc tới) rồi mới gọi.
            if (request.getDynamicFields() != null) {
                Map<String, Object> mergedDynamicFields = parseDynamicFields(current.getDynamicFieldsJson());
                mergedDynamicFields.putAll(request.getDynamicFields());
                current.replaceDynamicFields(toJson(mergedDynamicFields));
            }

            var saved = organizationRepository.save(current);
            refreshDescendants(saved);

            return Result.success(saved.getId());
        } catch (Exception ex) {
            return Result.failure("Cập nhật tổ chức thất bại: " + ex.getMessage());
        }
    }

    // Cùng convention parse/serialize JSON "{}" fallback đã dùng ở ProcessDiagramService/
    // DynamicDocumentFieldsService — dynamicFields của Organization đơn giản hơn (không cần suy luận
    // kiểu ngày/số như BusinessDocument) nên tự xử lý tại chỗ, không kéo thêm phụ thuộc chéo.
    private Map<String, Object> parseDynamicFields(String dynamicFieldsJson) {
        if (dynamicFieldsJson == null || dynamicFieldsJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(
                dynamicFieldsJson, new TypeReference<LinkedHashMap<String, Object>>() { });
            return parsed == null ? new LinkedHashMap<>() : parsed;
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private String toJson(Map<String, Object> dynamicFields) {
        try {
            return OBJECT_MAPPER.writeValueAsString(dynamicFields);
        } catch (Exception ex) {
            return "{}";
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
