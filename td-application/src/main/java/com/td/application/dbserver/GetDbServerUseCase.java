package com.td.application.dbserver;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(value = "controlPlaneTransactionManager", readOnly = true)
public class GetDbServerUseCase {

    private final DbServerRepository dbServerRepository;

    public Result<DbServerDto> execute(UUID id) {
        return dbServerRepository.findByIdAndDeletedOnIsNull(id)
            .map(entity -> Result.success(DbServerDtoMapper.map(entity)))
            .orElseGet(() -> Result.failure("Không tìm thấy máy chủ CSDL với ID: " + id));
    }
}
