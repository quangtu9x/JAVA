package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * BL-210 (VBDen Core) — DoD (4): request cho {@code POST /api/v1/vbden/QuaTrinhXuLy} — tương thích
 * API legacy {@code qlvb/vbden/QuaTrinhXuLy?databasetype=vbden_res} ({@code {parentid,
 * viewname:"QTXL_by_parentid", items:"getall"}}).
 */
@Data
public class VbdenQuaTrinhXuLyRequest {

    @NotNull(message = "parentid khong duoc de trong")
    private UUID parentid;

    private String items = "getall";

    private int pageNumber = 0;

    private int pageSize = 20;
}
