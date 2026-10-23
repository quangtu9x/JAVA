package com.td.infrastructure.tonghop;

import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 1 dòng {@code province_documents} chuẩn bị ghi. */
@Data
class ProvinceDocumentRow {

    private String tenantId;
    private UUID documentId;
    private String docKind;
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
    private String statusCode;
    private String processState;
    private boolean overdue;
    private boolean internal;
    private boolean network;
    private boolean national;
    private boolean paper;
    private boolean signed;
    private boolean revoked;
    private String sourceSendLogId;
    private String searchText;
    private OffsetDateTime createdOn;
    private OffsetDateTime sourceModifiedOn;
    private boolean deleted;
}
