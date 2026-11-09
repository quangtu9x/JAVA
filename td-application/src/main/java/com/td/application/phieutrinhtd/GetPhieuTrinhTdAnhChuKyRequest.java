package com.td.application.phieutrinhtd;

import lombok.Data;

/**
 * 30/10/2026: request cho {@code POST /api/v1/phieutrinhtd/AnhChuKy} - thay Domino
 * {@code qlvb/get?databasetype=qlvb items=anhkyso} + {@code qlvb/getfile} ở khối chữ ký ChiTietPage.jsx.
 * {@code id} = id phiếu trình (chỉ trả ảnh của người có tên trong phiếu trình đó).
 */
@Data
public class GetPhieuTrinhTdAnhChuKyRequest {

    private String id;
    private String username;
}
