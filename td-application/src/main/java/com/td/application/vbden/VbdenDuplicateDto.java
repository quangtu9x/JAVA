package com.td.application.vbden;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.UUID;

/** BL-210/211 (Nhập mới VBDen): 1 văn bản đến trùng, trả về từ KiemTrung/KiemTrungSoDen. */
@Data
public class VbdenDuplicateDto {

    private UUID id;
    private String code;
    private String subject;

    @JsonProperty("unit_in_charge")
    private String unitInCharge;

    @JsonProperty("document_number")
    private String documentNumber;

    @JsonProperty("promulgation_date")
    private String promulgationDate;

    @JsonProperty("identifier_creator")
    private String identifierCreator;
}
