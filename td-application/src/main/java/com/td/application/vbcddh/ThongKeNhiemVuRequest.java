package com.td.application.vbcddh;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * BL-250 (VBCDDH Core) — DoD (4): request cho {@code POST /api/v1/vbcddh/ThongKeNhiemVu}, tương
 * thích tham số body thật ({@code ThongKeNhiemVu.java}: {@code donvi}/{@code identifier},
 * {@code nguoi}, {@code tungay}, {@code denngay}). Nếu {@code nguoiList} không rỗng, thống kê theo
 * người chủ trì (field {@code chuyenvienthuchienhide}) — ưu tiên hơn {@code identifiers}, đúng thứ
 * tự ưu tiên thật; ngược lại thống kê theo đơn vị chủ trì (field {@code donvithuchienhide}).
 */
@Data
public class ThongKeNhiemVuRequest {

    private List<String> identifiers = new ArrayList<>();

    private List<String> nguoiList = new ArrayList<>();

    /** Định dạng ISO {@code yyyy-MM-dd}, lọc theo {@code create_date}. */
    private String tuNgay;

    private String denNgay;
}
