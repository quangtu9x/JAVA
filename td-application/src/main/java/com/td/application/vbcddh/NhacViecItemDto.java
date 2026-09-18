package com.td.application.vbcddh;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BL-250 (VBCDDH Core) — 1 mục nhắc việc, đúng cấu trúc thật ({@code MenuItem}, xem
 * {@code NhacViec.java}): {@code label} cố định theo nhóm, {@code count} là số nhiệm vụ khớp.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NhacViecItemDto {

    private String label;
    private long count;
}
