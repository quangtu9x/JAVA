package com.td.application.hosocv;

import lombok.Data;

/**
 * Mở rộng BL-240 (HoSoCV Core) — G1: request cho {@code POST /api/v1/hosocv/XemChiTiet}, tương
 * thích API legacy {@code qlvb/get?databasetype=hosocv} (chỉ cần {@code id} — {@code items} luôn
 * ngầm định "getall", đúng khuôn {@code GetDuThaoDetailRequest}).
 */
@Data
public class GetHoSoChiTietRequest {

    private String id;
}
