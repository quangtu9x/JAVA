package com.td.application.vbdi;

import lombok.Data;

/**
 * BL-220 (VBDi Core), yêu cầu người dùng (tách văn bản đi khỏi bảng dùng chung + bổ sung đường đọc
 * riêng): request cho {@code POST /api/v1/vbdi/XemChiTiet}, tương thích payload legacy
 * {@code qlvb/get?databasetype=vbdi} ({@code {id, items}}) — dùng cho màn xem chi tiết văn bản đi.
 *
 * <p>Đọc TRỰC TIẾP từ bảng vật lý ĐỘC LẬP {@code vbdi_documents} (xem {@link VbdiDocument}) — KHÔNG
 * qua {@code GetDynamicDocumentUseCase} (đọc bảng {@code documents} dùng chung, sẽ không thấy vbdi
 * nữa sau khi tách).</p>
 */
@Data
public class GetVbdiDetailRequest {

    // String (không phải UUID) cùng lý do đã áp dụng GetDuThaoDetailRequest: một số nơi có thể gửi ""
    // khi chưa có id — Jackson mặc định không coi "" là null khi bind thẳng vào UUID.
    private String id;

    private String items = "getall";
}