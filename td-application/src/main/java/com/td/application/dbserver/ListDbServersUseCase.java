package com.td.application.dbserver;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// BL-117 (bổ sung 19/08/2026): số lượng máy chủ CSDL thực tế nhỏ (vài server vật lý) — cố tình KHÔNG
// phân trang, cùng convention với ListTenantsUseCase.
@Service
@RequiredArgsConstructor
@Transactional(value = "controlPlaneTransactionManager", readOnly = true)
public class ListDbServersUseCase {

    private final DbServerRepository dbServerRepository;

    public Result<List<DbServerDto>> execute() {
        List<DbServerDto> items = dbServerRepository.findAllByDeletedOnIsNullOrderByNameAsc().stream()
            .map(DbServerDtoMapper::map)
            .toList();
        return Result.success(items);
    }
}
