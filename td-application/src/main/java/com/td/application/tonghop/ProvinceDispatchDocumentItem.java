package com.td.application.tonghop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 1 văn bản đi + số liệu cuộn (rollup) các lượt gửi của nó. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProvinceDispatchDocumentItem {

    private String tenantId;
    private UUID documentId;
    private String code;
    private String title;
    private String documentTypeName;
    private String senderIdentifier;
    private String senderName;
    private String signer;
    private LocalDate promulgationDate;
    private boolean signed;

    private long recipients;
    private long waiting;      // SENT
    private long arrived;      // ARRIVED (chưa vào sổ)
    private long processing;   // ACCEPTED/ASSIGNED/PROCESSING
    private long done;
    private long refused;
    private long revoked;
    private long lateArrival;
    private long lateAccept;
    private boolean hasInternal;
    private boolean hasContact;
    private boolean hasExternal;
    private OffsetDateTime firstSentOn;
    private OffsetDateTime lastEventOn;
}
