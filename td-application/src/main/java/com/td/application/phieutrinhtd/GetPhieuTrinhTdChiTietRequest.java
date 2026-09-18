package com.td.application.phieutrinhtd;

import lombok.Data;

/** Request cho {@code POST /api/v1/phieutrinhtd/XemChiTiet}, tương thích {@code qlvb/get?databasetype=phieutrinhtd}. */
@Data
public class GetPhieuTrinhTdChiTietRequest {

    private String id;
}
