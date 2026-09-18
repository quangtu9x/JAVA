package com.td.application.hosocv;

/**
 * Mở rộng BL-240 (HoSoCV Core) — G3 ({@code docs/analysis/08-...md} mục 2.2 "Vòng đời hồ sơ"):
 * hằng số 2 field trạng thái mở rộng KHÔNG thuộc {@link HoSoCVStatus} gốc (chỉ có
 * {@code status}/{@code DANG_XU_LY}/{@code DA_KET_THUC}) — lưu trong {@code dynamic_fields} (JSONB,
 * KHÔNG phải cột tĩnh, khác {@code status}).
 *
 * <p>{@code trinhlanhdaoduyet}: {@code ""}=chưa trình, {@code "2"}=đang chờ duyệt,
 * {@code "1"}=đã được duyệt, {@code "3"}=không được duyệt.</p>
 *
 * <p>{@code storaged}: {@code ""}/{@code "0"}=chưa chuyển (chờ), {@code "2"}=đã chuyển/đang chờ kho
 * tiếp nhận, {@code "1"}=kho đã tiếp nhận (lưu trữ chính thức), {@code "3"}=kho trả lại. Trạng thái
 * {@code "1"}/{@code "3"} cần module đối ứng {@code hosoluutru} (BL-270, CHƯA xây) hành động —
 * {@code ChuyenLuuTruHoSoUseCase} hiện tại CHỈ làm được {@code ""/"0" → "2"} (phía hồ sơ tự chuyển),
 * xem javadoc lớp đó.</p>
 */
public final class HoSoCVLifecycleStatus {

    public static final String TRINH_CHUA_TRINH = "";
    public static final String TRINH_CHO_DUYET = "2";
    public static final String TRINH_DA_DUYET = "1";
    public static final String TRINH_KHONG_DUOC_DUYET = "3";

    public static final String STORAGED_CHUA_CHUYEN = "0";
    public static final String STORAGED_DA_CHUYEN_CHO_TIEP_NHAN = "2";
    public static final String STORAGED_DA_LUU_TRU = "1";
    public static final String STORAGED_TRA_LAI = "3";

    public static final String FIELD_TRINHLANHDAODUYET = "trinhlanhdaoduyet";
    public static final String FIELD_STORAGED = "storaged";
    public static final String FIELD_STORAGED_DATE = "storaged_date";
    public static final String FIELD_END_DATE = "end_date";
    public static final String FIELD_SUMMARY = "summary";
    public static final String FIELD_CATEGORY = "category";

    private HoSoCVLifecycleStatus() {
    }
}
