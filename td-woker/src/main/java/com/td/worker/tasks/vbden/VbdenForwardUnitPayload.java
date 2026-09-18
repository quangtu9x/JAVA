package com.td.worker.tasks.vbden;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Payload của envelope {@code vbden.forward_unit} — PHẢI khớp field-by-field
 * {@code com.td.application.vbden.VbdenForwardUnitMessagePayload} (module {@code td-application},
 * đọc trực tiếp mã nguồn để lấy đúng tên field — 2 module độc lập, KHÔNG share class/jar).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VbdenForwardUnitPayload {

    private String originDocumentId;
    private String originTenantId;

    private String targetIdentifier;
    private String targetName;

    private String actingUsername;
    private String opinion;

    private String subject;
    private String code;
    private String promulgationDate;

    private String deadlineDate;
    private String unitInCharge;
    private String unitInChargeIdentifier;

    /**
     * Bổ sung 08/09/2026 (3, xem javadoc {@code VbdenForwardUnitMessagePayload#files} phía
     * td-application): tham chiếu THẲNG tới object HIỆN CÓ (KHÔNG copy) — {@link VbdenForwardUnitTaskHandler}
     * chỉ tạo 1 dòng {@code file_metadata} MỚI trỏ CÙNG {@code objectKey}/{@code bucketName}.
     */
    private List<VbdenFileRef> files;

    /** = {@code com.td.application.documents.VbdiDeliveryFileRef} - lặp lại field, KHÔNG import (2 module độc lập). */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VbdenFileRef {
        private String objectKey;
        private String bucketName;
        private String originalFilename;
        private Long size;
        private String contentType;
        private String fileExtension;
    }
}
