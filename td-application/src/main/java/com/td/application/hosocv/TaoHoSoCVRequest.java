package com.td.application.hosocv;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BL-240 (HoSoCV Core) — DoD (1): request cho {@code POST /api/v1/hosocv/TaoHoSo}, cùng khuôn dạng
 * {@code qlvb/add?databasetype=hosocv} (form-data field động) như {@code TaoDuThaoRequest} bên
 * duthao/{@code SoanThaoVbdiRequest} bên vbdi.
 *
 * <p>Không có servlet "tạo hồ sơ công việc" riêng evidenced trong source Domino
 * ({@code HoSoCVController.java} chỉ xử lý 3 action: gắn văn bản, download file, lấy token LGSP —
 * không có action tạo hồ sơ) — việc tạo hồ sơ đi qua cơ chế {@code qlvb/add} tổng quát, đúng field
 * {@code name} (ký hiệu hồ sơ, theo tài liệu FE sinh dạng {@code HOSO-<random>} ở tầng FE, không
 * phải BE) truyền qua {@code fields} như field động khác — không suy diễn logic sinh mã tự động ở
 * BE khi không có bằng chứng BE làm việc này.
 */
@Data
public class TaoHoSoCVRequest {

    private String title;

    private String status;

    private Map<String, String> fields = new LinkedHashMap<>();

    private String dateFields;

    private String numberFields;

    private String richtextFields;
}
