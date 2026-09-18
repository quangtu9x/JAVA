package com.td.application.hosocv;

/**
 * BL-240 (HoSoCV Core) — trạng thái hồ sơ công việc. Đúng bằng chứng thật
 * ({@code docs/frontend/07-hosocv-hosoluutru.md} mục "Field dữ liệu"/"Vòng đời hồ sơ công việc"):
 * field {@code status} chỉ có 2 giá trị {@code "0"} (đang xử lý)/{@code "1"} (đã kết thúc, có
 * {@code end_date}+{@code summary}). Các trường trạng thái khác của vòng đời đầy đủ
 * ({@code trinhlanhdaoduyet}: 0/1/2/3, {@code storaged}: 0/1/2/3 — trình duyệt/chuyển lưu trữ)
 * KHÔNG thuộc phạm vi BL-240 (4 DoD chỉ yêu cầu tạo/gắn văn bản/danh sách/download file — không có
 * DoD nào về trình duyệt hay chuyển lưu trữ, xem Javadoc {@code HoSoCVController}).
 */
public final class HoSoCVStatus {

    public static final String DANG_XU_LY = "0";
    public static final String DA_KET_THUC = "1";

    private HoSoCVStatus() {
    }
}
