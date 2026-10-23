package com.td.application.tonghop;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Giám sát gửi văn bản đi tập trung — danh sách văn bản đi (mọi tenant) kèm tiến độ từng nơi nhận, và
 * thẻ KPI/xếp hạng ({@link #summary}). Ngưỡng chậm mặc định: chưa "Đã đến" sau {@value #DEFAULT_LATE_ARRIVAL_MINUTES}
 * phút (nghi lỗi truyền nhận), chưa "Đã tiếp nhận" sau {@value #DEFAULT_LATE_ACCEPT_HOURS} giờ.
 */
@Service
@RequiredArgsConstructor
public class SearchProvinceDispatchesUseCase {

    static final int DEFAULT_LATE_ARRIVAL_MINUTES = 60;
    static final int DEFAULT_LATE_ACCEPT_HOURS = 24;
    private static final int MAX_PAGE_SIZE = 200;

    private final ProvinceQueryRepository provinceQueryRepository;
    private final ProvinceScopePolicy provinceScopePolicy;

    public Result<ProvinceDispatchSearchResult> execute(ProvinceDispatchSearchRequest request) {
        ProvinceDispatchSearchRequest r = request == null ? new ProvinceDispatchSearchRequest() : request;
        if (!applyScope(r)) {
            return Result.failure(SearchProvinceDocumentsUseCase.SCOPE_UNRESOLVED);
        }
        LocalDate from = ProvinceDates.parse(r.getFromDate());
        LocalDate to = ProvinceDates.parse(r.getToDate());
        if (from != null && to != null && from.isAfter(to)) {
            return Result.failure("Từ ngày phải trước hoặc bằng Đến ngày");
        }
        int lateArrival = lateArrivalMinutes(r);
        int lateAccept = lateAcceptHours(r);
        int pageNumber = Math.max(1, r.getPageNumber());
        int pageSize = r.isExport()
            ? SearchProvinceDocumentsUseCase.MAX_EXPORT_ROWS
            : Math.min(MAX_PAGE_SIZE, Math.max(1, r.getPageSize()));
        int offset = r.isExport() ? 0 : (pageNumber - 1) * pageSize;
        var page = provinceQueryRepository.searchDispatchDocuments(r, from, to, lateArrival, lateAccept, offset, pageSize);
        return Result.success(ProvinceDispatchSearchResult.builder()
            .items(page.items())
            .totalItems(page.totalItems())
            .pageNumber(r.isExport() ? 1 : pageNumber)
            .pageSize(pageSize)
            .lateArrivalMinutes(lateArrival)
            .lateAcceptHours(lateAccept)
            .build());
    }

    public Result<ProvinceDispatchSummary> summary(ProvinceDispatchSearchRequest request) {
        ProvinceDispatchSearchRequest r = request == null ? new ProvinceDispatchSearchRequest() : request;
        if (!applyScope(r)) {
            return Result.failure(SearchProvinceDocumentsUseCase.SCOPE_UNRESOLVED);
        }
        LocalDate from = ProvinceDates.parse(r.getFromDate());
        LocalDate to = ProvinceDates.parse(r.getToDate());
        if (from != null && to != null && from.isAfter(to)) {
            return Result.failure("Từ ngày phải trước hoặc bằng Đến ngày");
        }
        return Result.success(provinceQueryRepository.dispatchSummary(r, from, to, lateArrivalMinutes(r),
            lateAcceptHours(r)));
    }

    /** Luôn ghi đè phạm vi (không tin client): admin đơn vị chỉ thấy văn bản đi của tenant mình. */
    private boolean applyScope(ProvinceDispatchSearchRequest r) {
        ProvinceScope scope = provinceScopePolicy.current();
        if (!scope.isResolved()) {
            return false;
        }
        r.setScopeTenantId(scope.restrictTenantId());
        return true;
    }

    private static int lateArrivalMinutes(ProvinceDispatchSearchRequest r) {
        Integer v = r.getLateArrivalMinutes();
        return v == null || v <= 0 ? DEFAULT_LATE_ARRIVAL_MINUTES : v;
    }

    private static int lateAcceptHours(ProvinceDispatchSearchRequest r) {
        Integer v = r.getLateAcceptHours();
        return v == null || v <= 0 ? DEFAULT_LATE_ACCEPT_HOURS : v;
    }
}
