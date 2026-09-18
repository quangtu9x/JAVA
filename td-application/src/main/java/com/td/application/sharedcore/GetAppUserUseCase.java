package com.td.application.sharedcore;

import com.td.application.common.models.Result;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.sharedcore.AppUserRoleGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(value = "identityTransactionManager", readOnly = true)
public class GetAppUserUseCase {

    private final AppUserRepository appUserRepository;
    private final AppUserRoleGroupRepository appUserRoleGroupRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<AppUserDto> execute(UUID id) {
        try {
            if (id == null) {
                return Result.failure("ID người dùng không được để trống");
            }

            var opt = appUserRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy người dùng với ID: " + id);
            }

            var entity = opt.get();
            var dynamicFields = dynamicDocumentFieldsService.parseDynamicFields(entity.getDynamicFieldsJson());
            var roleGroupIds = appUserRoleGroupRepository.findByAppUserIdAndDeletedOnIsNull(entity.getId()).stream()
                .map(AppUserRoleGroup::getRoleGroupId)
                .toList();
            // Gap #5 (docs/analysis/18-checklist-port-kiem-nhiem-domino-sang-java.md): badge "có
            // kiêm nhiệm" đúng toàn cục.
            boolean hasKiemNhiem = appUserRepository.existsByParentUserIdAndDeletedOnIsNull(entity.getId());
            return Result.success(AppUserDtoMapper.map(entity, dynamicFields, roleGroupIds, hasKiemNhiem));
        } catch (Exception ex) {
            log.error("Lấy người dùng thất bại", ex);
            return Result.failure("Lấy người dùng thất bại: " + ex.getMessage());
        }
    }
}
