package com.td.application.hosocv;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mở rộng BL-240 (HoSoCV Core) — G3: request cho {@code POST /api/v1/hosocv/KetThucHoSo}.
 * Nhận DANH SÁCH id (khớp FE {@code ketthuchoso}/{@code ketthucnhieuhoso} — cùng 1 luồng, khác số
 * lượng).
 */
@Data
public class KetThucHoSoRequest {

    private List<UUID> hosocvIds = new ArrayList<>();

    private String summary;
}
