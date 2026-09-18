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
public class GetOrganizationUseCase {

    private final OrganizationRepository organizationRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<OrganizationDto> execute(UUID id) {
        try {
            if (id == null) {
                return Result.failure("ID tổ chức không được để trống");
            }

            var opt = organizationRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy tổ chức với ID: " + id);
            }

            var entity = opt.get();
            var dynamicFields = dynamicDocumentFieldsService.parseDynamicFields(entity.getDynamicFieldsJson());
            return Result.success(OrganizationDtoMapper.map(entity, dynamicFields));
        } catch (Exception ex) {
            log.error("Lấy tổ chức thất bại", ex);
            return Result.failure("Lấy tổ chức thất bại: " + ex.getMessage());
        }
    }
}
