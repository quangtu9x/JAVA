package com.td.application.vbden;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.UUID;

/**
 * BL-210/211 (Nhập mới VBDen): request cho {@code POST /api/v1/vbden/KiemTrung} — tương thích
 * {@code qlvb/vbden/KiemTrung} (Domino thật, {@code KiemTrung.java}): kiểm tra văn bản đến đã tồn
 * tại cùng (identifier_creator, promulgation_date, code) hay chưa, trước khi lưu.
 */
@Data
public class KiemTrungVbdenRequest {

    /** Văn bản đang sửa (loại trừ khỏi kết quả) — để trống khi đang tạo mới. */
    private UUID id;

    private String identifier;

    @JsonProperty("promulgation_date")
    private String promulgationDate;

    private String code;
}
