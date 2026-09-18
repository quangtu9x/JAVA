package com.td.application.tenant;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(value = "controlPlaneTransactionManager", readOnly = true)
public class GetTenantUseCase {

    private final TenantRepository tenantRepository;

    public Result<TenantDto> execute(UUID id) {
        return tenantRepository.findByIdAndDeletedOnIsNull(id)
            .map(entity -> Result.success(TenantDtoMapper.map(entity)))
            .orElseGet(() -> Result.failure("Không tìm thấy tenant với ID: " + id));
    }
}
