package com.td.application.sharedcore;

import com.td.application.common.TextNormalizer;
import com.td.application.common.models.Result;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.sharedcore.AppRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// BL-111: Vai trò người dùng — CRUD "Vai trò".
@Service
@RequiredArgsConstructor
@Transactional("identityTransactionManager")
@Slf4j
public class CreateAppRoleUseCase {

    private final AppRoleRepository appRoleRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<UUID> execute(CreateAppRoleRequest request) {
        try {
            String name = TextNormalizer.normalizeAndSanitize(request.getName());
            // TextNormalizer.normalize(null) trả về null (không phải "") — request.getCode() luôn
            // null khi FE không gửi (form "Vai trò" không có ô nhập mã), nên phải chặn null trước
            // khi gọi isBlank(), nếu không sẽ NPE (bug đã xảy ra thực tế: "Cannot invoke
            // String.isBlank() because code is null").
            String code = TextNormalizer.normalize(request.getCode());
            if (code == null || code.isBlank()) {
                // Form "Vai trò" phía Domino gốc chỉ có "Tên vai trò", không có ô nhập mã riêng —
                // tự sinh từ name (cùng cách CreateOrganizationUseCase tự sinh identifier cho
                // agency_level, BL-105), thêm hậu tố số nếu trùng vì tên vai trò dễ đụng nhau hơn.
                code = generateUniqueCode(name);
            } else if (appRoleRepository.existsByCodeAndDeletedOnIsNull(code)) {
                return Result.failure("Mã vai trò '" + code + "' đã tồn tại");
            }

            var role = new AppRole(
                code,
                name,
                TextNormalizer.normalizeAndSanitize(request.getDescription()),
                request.getIsSystemRole() != null && request.getIsSystemRole(),
                request.getIsActive() == null || request.getIsActive(),
                request.getSortOrder() == null ? 0 : request.getSortOrder()
            );

            if (request.getDynamicFields() != null && !request.getDynamicFields().isEmpty()) {
                String dynamicFieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(
                    request.getDynamicFields(), request.getDateFields(),
                    request.getNumberFields(), request.getRichtextFields());
                role.replaceDynamicFields(dynamicFieldsJson);
            }

            var saved = appRoleRepository.save(role);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Tạo vai trò thất bại", ex);
            return Result.failure("Tạo vai trò thất bại: " + ex.getMessage());
        }
    }

    private String generateUniqueCode(String name) {
        String base = (name == null || name.isBlank() ? "VAITRO" : name)
            .toUpperCase().replaceAll("\\s+", "_");
        String candidate = base;
        int suffix = 2;
        while (appRoleRepository.existsByCodeAndDeletedOnIsNull(candidate)) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        return candidate;
    }
}
