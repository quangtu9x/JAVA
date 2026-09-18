package com.td.application.vbcddh;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BL-250 (VBCDDH Core) — 1 dòng thống kê nhiệm vụ, đúng cấu trúc thật ({@code NoiDungCDDH}, xem
 * {@code ThongKeNhiemVu.java}): {@code label}/{@code colorTag} cố định theo 6 tổ hợp
 * (tinhtrang × quahan), {@code count} là số nhiệm vụ khớp tổ hợp đó.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NhiemVuThongKeItemDto {

    private long count;
    private String label;
    private String colorTag;
}
