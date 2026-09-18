package com.td.application.dbserver;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional("controlPlaneTransactionManager")
public class ActivateDbServerUseCase {

    private final DbServerRepository dbServerRepository;

    public Result<UUID> execute(UUID id) {
        try {
            var opt = dbServerRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy máy chủ CSDL với ID: " + id);
            }
            var entity = opt.get();
            entity.activate();
            var saved = dbServerRepository.save(entity);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Kích hoạt máy chủ CSDL thất bại", ex);
            return Result.failure("Kích hoạt máy chủ CSDL thất bại: " + ex.getMessage());
        }
    }
}
