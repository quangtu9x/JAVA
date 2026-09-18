package com.td.application.hosoluutru;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Request cho {@link TaoHoSoLuuTruTuHoSoCVUseCase} — tương đương
 * {@code POST qlvb/hosoluutru/chuyenluutru?databasetype=hosocv&id=<hosocvId>&dmhsid=<dmhsId>}.
 */
@Getter
@Setter
public class TaoHoSoLuuTruTuHoSoCVRequest {

    @NotNull
    private UUID hosocvId;

    /** Id danh mục hồ sơ lưu trữ đã chọn — {@code records_storage_catalogs} (BL-116). */
    @NotNull
    private UUID dmhsId;
}
