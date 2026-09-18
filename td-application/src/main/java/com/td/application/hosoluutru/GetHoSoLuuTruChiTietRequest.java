package com.td.application.hosoluutru;

import lombok.Data;

/** Request cho {@code POST /api/v1/hosoluutru/XemChiTiet}, tương thích {@code qlvb/get?databasetype=hosoluutru}. */
@Data
public class GetHoSoLuuTruChiTietRequest {

    private String id;
}
