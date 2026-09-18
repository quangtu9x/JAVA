package com.td.application.vbcddh;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * BL-250 (VBCDDH Core) — DoD (5): 2 nhóm nhắc việc, đúng cấu trúc thật ({@code NhacViec.java}:
 * {@code res.set("dangthuchien", ...)} / {@code res.set("dahoanthanh", ...)}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NhacViecResponseDto {

    private List<NhacViecItemDto> dangThucHien;
    private List<NhacViecItemDto> daHoanThanh;
}
