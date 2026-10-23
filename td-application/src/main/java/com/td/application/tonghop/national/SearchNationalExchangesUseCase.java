package com.td.application.tonghop.national;

import com.td.application.common.models.Result;
import com.td.application.tonghop.ProvinceDates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;

/**
 * Danh sách + thẻ số liệu gói trao đổi qua Trục quốc gia (toàn tỉnh). Đọc DB TD Gateway — tắt cấu hình
 * ({@code NATIONAL_EXCHANGE_ADMIN_ENABLED=false}) hoặc DB không kết nối được thì trả lỗi nghiệp vụ, không ném 500.
 */
@Slf4j
@Service
public class SearchNationalExchangesUseCase {

    static final String NOT_CONFIGURED =
        "Chưa cấu hình kết nối dữ liệu TD Gateway (NATIONAL_EXCHANGE_ADMIN_ENABLED) — liên hệ quản trị hệ thống";
    static final String UNAVAILABLE = "Không đọc được dữ liệu TD Gateway, vui lòng thử lại sau";

    static final int DEFAULT_DAYS = 30;
    /** Không cho quét quá 1 năm/lần (read model không phân vùng — tdgateway/docs/22 §5). */
    static final int MAX_DAYS = 366;
    static final int MAX_PAGE_SIZE = 100;
    /** Phân trang offset chỉ cho trang nông; sâu hơn phải thu hẹp bộ lọc. */
    static final int MAX_OFFSET = 10_000;
    /** Xuất Excel: 1 lần tối đa — khoảng thời gian vẫn bị giới hạn {@link #MAX_DAYS}. */
    static final int MAX_EXPORT_ROWS = 10_000;
    static final int DEFAULT_STALE_HOURS = 2;
    static final int MAX_STALE_HOURS = 720;

    static final String FORBIDDEN = "Bạn không có quyền xem dữ liệu gửi nhận Trục quốc gia";

    private final ObjectProvider<NationalExchangeQueryRepository> repositoryProvider;
    private final NationalExchangeAccessPolicy accessPolicy;

    public SearchNationalExchangesUseCase(ObjectProvider<NationalExchangeQueryRepository> repositoryProvider,
                                          NationalExchangeAccessPolicy accessPolicy) {
        this.repositoryProvider = repositoryProvider;
        this.accessPolicy = accessPolicy;
    }

    public Result<NationalExchangeSearchResult> execute(NationalExchangeSearchRequest request) {
        NationalExchangeSearchRequest r = request == null ? new NationalExchangeSearchRequest() : request;
        String invalid = validate(r);
        if (invalid != null) {
            return Result.failure(invalid);
        }
        NationalExchangeAccess access = accessPolicy.current();
        if (!access.isCanView()) {
            return Result.failure(FORBIDDEN);
        }
        applyScope(r, access);
        LocalDate[] range = range(r);
        if (range == null) {
            return Result.failure("Khoảng thời gian không hợp lệ (Từ ngày ≤ Đến ngày, tối đa " + MAX_DAYS + " ngày)");
        }
        int size = r.isExport() ? MAX_EXPORT_ROWS : Math.max(1, Math.min(r.getPageSize(), MAX_PAGE_SIZE));
        int page = r.isExport() ? 1 : Math.max(1, r.getPageNumber());
        long offset = (long) (page - 1) * size;
        if (offset > MAX_OFFSET) {
            return Result.failure("Trang quá sâu — hãy thu hẹp bộ lọc hoặc khoảng thời gian");
        }
        return call(repo -> {
            NationalExchangeSearchResult result = repo.search(r, range[0], range[1], (int) offset, size);
            if (!access.isCanAudit() && result.getItems() != null) {
                result.getItems().forEach(NationalExchangeMasking::maskItem);
            }
            result.setPageNumber(page);
            result.setPageSize(size);
            result.setFromDate(range[0]);
            result.setToDate(range[1]);
            return result;
        });
    }

    public Result<NationalExchangeSummary> summary(NationalExchangeSearchRequest request) {
        NationalExchangeSearchRequest r = request == null ? new NationalExchangeSearchRequest() : request;
        String invalid = validate(r);
        if (invalid != null) {
            return Result.failure(invalid);
        }
        NationalExchangeAccess access = accessPolicy.current();
        if (!access.isCanView()) {
            return Result.failure(FORBIDDEN);
        }
        applyScope(r, access);
        LocalDate[] range = range(r);
        if (range == null) {
            return Result.failure("Khoảng thời gian không hợp lệ (Từ ngày ≤ Đến ngày, tối đa " + MAX_DAYS + " ngày)");
        }
        return call(repo -> repo.summary(r, range[0], range[1]));
    }

    /** Người dùng không có phạm vi toàn tỉnh: ép lọc theo tenant đang đăng nhập, bỏ qua tenantId client gửi. */
    static void applyScope(NationalExchangeSearchRequest r, NationalExchangeAccess access) {
        if (!access.isProvince()) {
            r.setTenantId(access.getTenantId());
        }
    }

    private <T> Result<T> call(Function<NationalExchangeQueryRepository, T> action) {
        NationalExchangeQueryRepository repo = repositoryProvider.getIfAvailable();
        if (repo == null) {
            return Result.failure(NOT_CONFIGURED);
        }
        try {
            return Result.success(action.apply(repo));
        } catch (RuntimeException ex) {
            log.warn("Doc du lieu TD Gateway that bai: {}", ex.getMessage(), ex);
            return Result.failure(UNAVAILABLE);
        }
    }

    static String validate(NationalExchangeSearchRequest r) {
        if (blank(r.getDirection()) == null) {
            r.setDirection(null);
        } else if (!NationalExchangeStates.DIRECTIONS.contains(r.getDirection())) {
            return "Chiều trao đổi không hợp lệ";
        }
        if (blank(r.getMessageType()) == null) {
            r.setMessageType(null);
        } else if (!NationalExchangeStates.MESSAGE_TYPES.contains(r.getMessageType())) {
            return "Loại gói không hợp lệ";
        }
        List<String> states = r.getStates() == null ? List.of() : r.getStates();
        if (!NationalExchangeStates.ALL.containsAll(states)) {
            return "Nhóm trạng thái không hợp lệ";
        }
        r.setStates(states);
        r.setTenantId(blank(r.getTenantId()));
        String keyword = blank(r.getKeyword());
        r.setKeyword(keyword == null ? null : keyword.length() > 300 ? keyword.substring(0, 300) : keyword);
        String businessDocumentId = blank(r.getBusinessDocumentId());
        if (businessDocumentId != null && businessDocumentId.length() > 100) {
            return "Mã văn bản không hợp lệ";
        }
        r.setBusinessDocumentId(businessDocumentId);
        Integer stale = r.getStaleHours();
        r.setStaleHours(stale == null || stale <= 0 ? DEFAULT_STALE_HOURS : Math.min(stale, MAX_STALE_HOURS));
        return null;
    }

    /** [from, to] bao gồm 2 đầu; mặc định 30 ngày gần nhất. Null = không hợp lệ. */
    static LocalDate[] range(NationalExchangeSearchRequest r) {
        LocalDate to = ProvinceDates.parse(r.getToDate());
        LocalDate from = ProvinceDates.parse(r.getFromDate());
        if (to == null) {
            to = from == null ? LocalDate.now() : from.plusDays(MAX_DAYS - 1L);
            if (to.isAfter(LocalDate.now())) {
                to = LocalDate.now();
            }
        }
        if (from == null) {
            from = to.minusDays(DEFAULT_DAYS - 1L);
        }
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            return null;
        }
        return new LocalDate[] {from, to};
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
