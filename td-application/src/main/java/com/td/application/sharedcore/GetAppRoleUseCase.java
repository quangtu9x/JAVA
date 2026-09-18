package com.td.application.sharedcore;

import com.td.application.common.models.Result;
import com.td.application.documents.DynamicDocumentFieldsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(value = "identityTransactionManager", readOnly = true)
public class GetAppRoleUseCase {

    private final AppRoleRepository appRoleRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<AppRoleDto> execute(UUID id) {
        try {
            if (id == null) {
                return Result.failure("ID vai trò không được để trống");
            }

            var opt = appRoleRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy vai trò với ID: " + id);
            }

            var entity = opt.get();
            var dynamicFields = dynamicDocumentFieldsService.parseDynamicFields(entity.getDynamicFieldsJson());
            return Result.success(AppRoleDtoMapper.map(entity, dynamicFields));
        } catch (Exception ex) {
            log.error("Lấy vai trò thất bại", ex);
            return Result.failure("Lấy vai trò thất bại: " + ex.getMessage());
        }
    }
}
