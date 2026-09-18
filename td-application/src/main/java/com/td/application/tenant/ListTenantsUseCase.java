package com.td.application.tenant;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// BL-117: số lượng tenant thực tế (đơn vị hành chính) là nhỏ (hàng chục, không phải hàng nghìn) —
// cố tình KHÔNG phân trang, khác convention phân trang nặng đang dùng cho các danh mục nghiệp vụ
// lớn khác (Category/DocumentRegister...).
@Service
@RequiredArgsConstructor
@Transactional(value = "controlPlaneTransactionManager", readOnly = true)
public class ListTenantsUseCase {

    private final TenantRepository tenantRepository;

    public Result<List<TenantDto>> execute() {
        List<TenantDto> items = tenantRepository.findAllByDeletedOnIsNull().stream()
            .map(TenantDtoMapper::map)
            .toList();
        return Result.success(items);
    }
}
