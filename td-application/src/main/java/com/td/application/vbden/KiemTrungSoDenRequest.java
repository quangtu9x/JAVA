package com.td.application.vbden;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.UUID;

/**
 * BL-210/211 (Nhập mới VBDen): request cho {@code POST /api/v1/vbden/KiemTrungSoDen} — tương thích
 * {@code qlvb/vbden/KiemTrungSoDen} (Domino thật, {@code KiemTrungSoDen.java}): kiểm tra số văn bản
 * đến đã được cấp cho văn bản khác trong CÙNG 1 sổ đăng ký hay chưa.
 */
@Data
public class KiemTrungSoDenRequest {

    @JsonProperty("document_register_id")
    private UUID documentRegisterId;

    @JsonProperty("document_number")
    private String documentNumber;
}
