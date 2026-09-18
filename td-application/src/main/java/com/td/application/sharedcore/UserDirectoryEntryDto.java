package com.td.application.sharedcore;

import lombok.Data;

// BL-118 (tiếp, 19/08/2026): 1 mục trong "Danh sách người dùng" — tương đương
// qlvb/danhmuc/DanhSachNguoiDung (Domino thật, view "user_by_dbpath_cocau") — dùng cho hàng chục
// dropdown/lookup "người xử lý"/"người ký" khắp module vbden (FE cache 1 lần vào localStorage qua
// utils/global.jsx#getAllUser). Field tối giản, chỉ đúng những gì FE thực sự đọc (xem
// SubViewThongTinCoBan.jsx/ColumnsDen.jsx...): username, name, position, department, unit, agency.
@Data
public class UserDirectoryEntryDto {
    private String username;
    private String name;
    private String position = "";
    private String department = "";
    private String unit = "";
    private String agency = "";
    private String agencyLevel = "";
    private String identifier = "";
}
