package com.td.application.vbden;

import lombok.Data;

/**
 * 17/09/2026 (yêu cầu người dùng "fix gap luôn" — {@link GuiSangVbcddhUseCase} chưa replicate
 * {@code TaoNhiemVu()} thật): 1 phần tử của {@code listChuTri}/{@code listPhoiHop} — đối chiếu
 * {@code GuiSangVBCDDH.java#TaoNhiemVu()} dòng 700-780, mỗi phần tử là 1 JSON object có 1 TRONG 2
 * dạng (Domino tự phân biệt qua field nào có mặt, KHÔNG có cờ tường minh):
 * <ul>
 *   <li><b>Giao cho ĐƠN VỊ</b> ({@code identifier} có mặt): {@code name}/{@code identifier} = tên/mã
 *   định danh đơn vị thực hiện — tuỳ chọn kèm {@code receiver}/{@code userReceiver} (đại diện CỤ THỂ
 *   của đơn vị đó nhận việc — Domino gốc tự tra cứu thêm qua {@code vwuserbyusername} để lấy
 *   position/unit, ở đây ĐƠN GIẢN HOÁ: tin trực tiếp {@code receiver}/{@code userReceiver} client gửi
 *   — client đã có UI chọn người, cùng nguyên tắc "trust request identifier đã resolve" đã áp dụng ở
 *   nhiều use case khác trong module này).</li>
 *   <li><b>Giao thẳng cho CÁ NHÂN</b> ({@code username} có mặt, KHÔNG có {@code identifier}):
 *   {@code name}/{@code username} = tên/tài khoản người thực hiện trực tiếp.</li>
 * </ul>
 */
@Data
public class NhiemVuCdDhAssigneeDto {

    private String name;

    private String identifier;

    private String username;

    private String receiver;

    private String userReceiver;
}
