package com.td.application.tonghop;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Tìm kiếm văn bản toàn tỉnh (vbden + vbdi mọi tenant) trên kho tập trung. Khác Domino cũ: không giới hạn
 * cứng 20/200 kết quả, có tổng số thật, phân trang server, bộ đếm theo bộ lọc, xuất toàn bộ kết quả lọc.
 */
@Service
@RequiredArgsConstructor
public class SearchProvinceDocumentsUseCase {

    static final int MAX_PAGE_SIZE = 200;
    public static final int MAX_EXPORT_ROWS = 10_000;

    /** Không xác định được đơn vị của người dùng → không có phạm vi an toàn (xem {@link ProvinceScopePolicy}). */
    public static final String SCOPE_UNRESOLVED =
        "Không xác định được đơn vị của tài khoản — chưa thể xem dữ liệu tổng hợp. Liên hệ quản trị hệ thống";

    private final ProvinceQueryRepository provinceQueryRepository;
    private final ProvinceScopePolicy provinceScopePolicy;

    public Result<ProvinceDocumentSearchResult> execute(ProvinceDocumentSearchRequest request) {
        ProvinceDocumentSearchRequest r = request == null ? new ProvinceDocumentSearchRequest() : request;
        ProvinceScope scope = provinceScopePolicy.current();
        if (!scope.isResolved()) {
            return Result.failure(SCOPE_UNRESOLVED);
        }
        // Luôn ghi đè (không tin client): admin đơn vị chỉ thấy văn bản của tenant mình.
        r.setScopeTenantId(scope.restrictTenantId());
        LocalDate from = ProvinceDates.parse(r.getFromDate());
        LocalDate to = ProvinceDates.parse(r.getToDate());
        if (from != null && to != null && from.isAfter(to)) {
            return Result.failure("Từ ngày phải trước hoặc bằng Đến ngày");
        }
        int pageNumber = Math.max(1, r.getPageNumber());
        int pageSize = r.isExport()
            ? MAX_EXPORT_ROWS
            : Math.min(MAX_PAGE_SIZE, Math.max(1, r.getPageSize()));
        int offset = r.isExport() ? 0 : (pageNumber - 1) * pageSize;
        ProvinceDocumentSearchResult result = provinceQueryRepository.searchDocuments(r, from, to, offset, pageSize);
        result.setPageNumber(r.isExport() ? 1 : pageNumber);
        result.setPageSize(pageSize);
        return Result.success(result);
    }
}
