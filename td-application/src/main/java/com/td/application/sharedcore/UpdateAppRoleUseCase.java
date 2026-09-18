package com.td.application.sharedcore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.TextNormalizer;
import com.td.application.common.models.Result;
import com.td.application.documents.DynamicDocumentFieldsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional("identityTransactionManager")
@Slf4j
public class UpdateAppRoleUseCase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AppRoleRepository appRoleRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<UUID> execute(UUID id, UpdateAppRoleRequest request) {
        try {
            var opt = appRoleRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy vai trò với ID: " + id);
            }

            var role = opt.get();
            role.update(
                TextNormalizer.normalizeAndSanitize(request.getName()),
                TextNormalizer.normalizeAndSanitize(request.getDescription()),
                request.getIsSystemRole(),
                request.getIsActive(),
                request.getSortOrder()
            );

            if (request.getDynamicFields() != null && !request.getDynamicFields().isEmpty()) {
                // Merge vào field động hiện có (không xoá field cũ không được nhắc tới trong lần
                // sửa này) — cùng pattern đã dùng ở UpdateOrganizationUseCase (BL-109).
                Map<String, Object> existingFields =
                    dynamicDocumentFieldsService.parseDynamicFields(role.getDynamicFieldsJson());
                String newFieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(
                    request.getDynamicFields(), request.getDateFields(),
                    request.getNumberFields(), request.getRichtextFields());
                Map<String, Object> newFields = dynamicDocumentFieldsService.parseDynamicFields(newFieldsJson);

                Map<String, Object> merged = new LinkedHashMap<>(existingFields);
                merged.putAll(newFields);
                role.replaceDynamicFields(toJson(merged));
            }

            var saved = appRoleRepository.save(role);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Cập nhật vai trò thất bại", ex);
            return Result.failure("Cập nhật vai trò thất bại: " + ex.getMessage());
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
