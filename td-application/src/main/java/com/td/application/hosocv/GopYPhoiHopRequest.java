package com.td.application.hosocv;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Mở rộng BL-240 (HoSoCV Core) — G4/G9 ({@code docs/analysis/08-...md} mục 5.2
 * {@code saveInitHoso}/{@code TDDocument.java} dòng 916-967): request cho
 * {@code POST /api/v1/hosocv/GopYPhoiHop} — gộp 2 actionName FE {@code gopy}/{@code phoihop}
 * (cùng cơ chế: ghi ý kiến + đồng bộ ngược {@code coordinated}/{@code user_coordinated}).
 */
@Data
public class GopYPhoiHopRequest {

    @NotNull(message = "hosocvId khong duoc de trong")
    private UUID hosocvId;

    @NotBlank(message = "opinion khong duoc de trong")
    private String opinion;

    /** {@code "phoihop"} (mặc định) hoặc {@code "gopy"} — chỉ ảnh hưởng {@code process_type} ghi vết. */
    private String loai;
}
