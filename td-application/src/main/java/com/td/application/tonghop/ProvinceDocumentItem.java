package com.td.application.tonghop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 1 dòng kết quả tìm kiếm toàn tỉnh. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProvinceDocumentItem {

    private String tenantId;
    private UUID documentId;
    private String kind;
    private String ownerIdentifier;
    private String ownerName;
    private String code;
    private String title;
    private String documentTypeName;
    private String agencyIdentifier;
    private String agencyName;
    private String signer;
    private String priority;
    private LocalDate docDate;
    private LocalDate promulgationDate;
    private LocalDate deadlineDate;
    private String processState;
    private boolean overdue;
    private boolean internal;
    private boolean network;
    private boolean national;
    private boolean paper;
    private boolean signed;
    private boolean revoked;
    private OffsetDateTime createdOn;
}
