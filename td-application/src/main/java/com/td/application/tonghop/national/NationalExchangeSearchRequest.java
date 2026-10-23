package com.td.application.tonghop.national;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Bộ lọc màn "Trục quốc gia" (route FE {@code /tonghop/trucquocgia}). Khoảng thời gian theo lần cập nhật cuối
 * của gói ({@code updated_at}) và LUÔN bị giới hạn (mặc định 30 ngày) — tài liệu tdgateway/docs/22 §5 cấm quét
 * toàn lịch sử.
 */
@Data
public class NationalExchangeSearchRequest {

    /** Mã văn bản (ID nghiệp vụ/eDXML/Trục), mã hoặc tên cơ quan gửi/nhận. */
    private String keyword;

    /** {@code INBOUND} (văn bản đến từ Trục) | {@code OUTBOUND} (gửi ra Trục). Rỗng = cả hai. */
    private String direction;

    /** {@code edoc} (văn bản) | {@code status} (gói trạng thái). Rỗng = cả hai. */
    private String messageType;

    /** Nhóm trạng thái — xem {@link NationalExchangeStates}. Rỗng = tất cả. */
    private List<String> states = new ArrayList<>();

    /** Đơn vị trong tỉnh (tenant) — chiều đi: tenant gửi; chiều đến: tenant nhận. */
    private String tenantId;

    /** yyyy-MM-dd hoặc dd/MM/yyyy. */
    private String fromDate;

    private String toDate;

    /**
     * "Gói cần hỗ trợ": lỗi/cách ly, hoặc chưa xong mà không tiến triển quá {@link #staleHours} giờ, hoặc gói đến còn
     * đơn vị trong tỉnh chưa nhận (ACK) quá ngưỡng. Kết hợp AND với các bộ lọc khác.
     */
    private boolean needsSupport;

    /** Ngưỡng giờ "treo"/ACK quá hạn — mặc định {@code 2}. */
    private Integer staleHours;

    /** Mã văn bản nghiệp vụ (business_document_id) — liên kết từ Giám sát VB đi/Nhật ký gửi nhận. */
    private String businessDocumentId;

    /** Xuất tệp: bỏ phân trang, tối đa {@code SearchNationalExchangesUseCase.MAX_EXPORT_ROWS} dòng. */
    private boolean export;

    private int pageNumber = 1;

    private int pageSize = 20;
}
