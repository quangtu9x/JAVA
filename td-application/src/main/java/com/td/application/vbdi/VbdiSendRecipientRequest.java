package com.td.application.vbdi;

import lombok.Data;

/**
 * Yêu cầu người dùng 28/08/2026 — 1 nơi nhận trong request {@link SendVbdiDocumentRequest}, đúng
 * hình dạng JSON {@code coordinator_obj}/{@code internal_unit_obj}/{@code contact_unit_obj}/
 * {@code external_unit_obj} Domino thật (xem {@code docs/analysis/07-phan-tich-port-senddocument-
 * vbdi-qlvb.md} §3 + mẫu thật {@code docs/views_domino/data_vbdi_mau.txt}: mỗi phần tử có
 * {@code identifier}/{@code name}/{@code username}/{@code mailin}).
 */
@Data
public class VbdiSendRecipientRequest {

    /** Mã định danh đơn vị nhận (nhóm đơn vị) — rỗng nếu nhóm {@code coordinator} (cá nhân). */
    private String identifier;

    /** Tên hiển thị nơi nhận. */
    private String name;

    /** Username — chỉ có ý nghĩa với nhóm {@code coordinator} (cá nhân). */
    private String username;

    /** Mã mail-in nếu gửi qua liên thông (rỗng nếu gửi nội bộ). */
    private String mailin;
}
