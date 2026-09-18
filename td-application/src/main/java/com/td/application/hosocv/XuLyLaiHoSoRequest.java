package com.td.application.hosocv;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mở rộng BL-240 (HoSoCV Core) — G3/G9: request cho {@code POST /api/v1/hosocv/XuLyLai}. Nhận danh
 * sách id (khớp FE {@code xulylaihoso}/{@code xulylainhieuhoso}).
 */
@Data
public class XuLyLaiHoSoRequest {

    private List<UUID> hosocvIds = new ArrayList<>();

    private String lyDo;
}
