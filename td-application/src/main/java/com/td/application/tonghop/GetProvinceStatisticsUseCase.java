package com.td.application.tonghop;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * "Tổng hợp chung" toàn tỉnh — số liệu theo đơn vị tính TRỰC TIẾP từ kho tập trung theo khoảng thời gian
 * người dùng chọn (thay blob Redis {@code ThongKeTongHopVanBanDen/Di} do job ngoài ghi, không lọc được).
 */
@Service
@RequiredArgsConstructor
public class GetProvinceStatisticsUseCase {

    private final ProvinceQueryRepository provinceQueryRepository;

    public Result<ProvinceStatisticsResult> execute(ProvinceStatisticsRequest request) {
        LocalDate from = request == null ? null : ProvinceDates.parse(request.getFromDate());
        LocalDate to = request == null ? null : ProvinceDates.parse(request.getToDate());
        if (from != null && to != null && from.isAfter(to)) {
            return Result.failure("Từ ngày phải trước hoặc bằng Đến ngày");
        }
        return Result.success(provinceQueryRepository.statistics(from, to));
    }
}
