package com.td.application.vbdi;

import lombok.Data;

/**
 * 1 người/đơn vị được chọn trong modal "Chọn nhân/đơn vị xem để biết" ({@code YkienModal.jsx},
 * {@code actionname="xemdebiet"}) — xem javadoc {@link ChuyenXemDeBietVbdiUseCase}. Dùng chung cho cả
 * 2 danh sách {@code coordinators} (cá nhân, khớp {@code username}) và {@code internalUnits} (đơn vị
 * nội bộ, khớp {@code identifier}) trong {@link ChuyenXemDeBietVbdiRequest} — đúng cách Domino gốc
 * ({@code DocumentOutProcesses.java#ProcessDocument}) dùng CHUNG 1 shape phần tử JSON cho cả 2 key
 * ("coordinator"/"internal_unit"), chỉ khác field nào có giá trị.
 */
@Data
public class VbdiForwardReadRecipient {

    private String id;
    private String name;
    private String username;
    private String identifier;
    private String unit;
}
