package com.td.application.duthao;

import lombok.Data;

/**
 * BL-230 (DuThao Core) — Slice 1 ("create/save core + get detail", tài liệu phân tích 04 mục 13) —
 * request cho {@code POST /api/v1/duthao/XemChiTiet}, tương thích payload legacy
 * {@code qlvb/get?databasetype=duthao} ({@code {id, items}}, xem
 * {@code ChiTietPage.jsx#fetchData}).
 *
 * <p>{@code id} kiểu {@code String} (không phải {@code UUID}) cùng lý do đã áp dụng ở
 * {@code GetVbdenDetailRequest}: component chi tiết dùng chung cho nhiều màn (toanbo/
 * yeucausoanthaolai/thungrac...), một số nơi có thể gửi {@code ""} khi chưa có id — Jackson mặc
 * định không coi {@code ""} là {@code null} khi bind thẳng vào {@code UUID}, sẽ throw lỗi 400 thay
 * vì cho use case tự xử lý "id rỗng" như một lỗi nghiệp vụ bình thường.</p>
 *
 * <p>KHÔNG có khái niệm {@code idso}/{@code documentRegisterId} như vbden — {@code duthao} không có
 * kiến trúc "2-document" (sổ văn bản), nên request đơn giản hơn nhiều so với
 * {@code GetVbdenDetailRequest}.</p>
 */
@Data
public class GetDuThaoDetailRequest {

    private String id;

    private String items = "getall";
}
