package com.td.application.tonghop;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Bộ lọc "Tìm kiếm văn bản toàn tỉnh" (route FE {@code /tonghop/timkiem}) — chạy trên kho tập trung
 * {@code province_documents} (control-plane), KHÔNG mở DB từng tenant như Domino cũ
 * ({@code TimKiemVanBanDen}/{@code TimKiemToanTinhEs}).
 *
 * <p>Ngày nhận cả {@code yyyy-MM-dd} lẫn {@code dd/MM/yyyy}. Danh sách rỗng = không lọc.</p>
 */
@Data
public class ProvinceDocumentSearchRequest {

    /** Nhiều từ khoá cách nhau khoảng trắng — AND, không phân biệt dấu/hoa thường. */
    private String keyword;

    /** {@code VBDEN} | {@code VBDI}. */
    private List<String> kinds = new ArrayList<>();

    /** Mã định danh đơn vị (sở hữu văn bản). */
    private List<String> unitIdentifiers = new ArrayList<>();

    /** Gồm cả đơn vị con (khớp tiền tố {@code <mã>.}) — mặc định có. */
    private Boolean includeChildUnits = Boolean.TRUE;

    private String fromDate;

    private String toDate;

    /** {@code NOI_BO} | {@code LIEN_THONG} | {@code TRUC_QG} | {@code GIAY}. */
    private List<String> channels = new ArrayList<>();

    /** Xem {@link ProvinceDocumentStates}. */
    private List<String> processStates = new ArrayList<>();

    private Boolean signed;

    private Boolean overdue;

    private Boolean includeRevoked = Boolean.FALSE;

    private int pageNumber = 1;

    private int pageSize = 20;

    /** Xuất tệp: cho phép trang lớn (tối đa {@link SearchProvinceDocumentsUseCase#MAX_EXPORT_ROWS}). */
    private boolean export;

    /**
     * Phạm vi tenant do SERVER đặt ({@link ProvinceScopePolicy}) — client không gửi được ({@code @JsonIgnore}) và use
     * case luôn ghi đè. Null = toàn tỉnh. Có trong {@code toString} nên khoá cache theo phạm vi.
     */
    @JsonIgnore
    private String scopeTenantId;
}
