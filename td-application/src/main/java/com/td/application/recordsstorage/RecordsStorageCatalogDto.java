package com.td.application.recordsstorage;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class RecordsStorageCatalogDto {
    private UUID id;
    private String category;
    private String code;
    private String phong;
    private UUID phongId;
    private String firstTopic;
    private UUID firstTopicId;
    private String secondTopic;
    private UUID secondTopicId;
    private String storagePeriod;
    private int yearInception;
    private int sortOrder;
    private String note;
    private Integer numberOfDocumentNumber;
    private String kyhieu;
    private String internalUnitObj;
    private String individualObj;
    private String identifier;
    private LocalDateTime createdOn;
    private LocalDateTime lastModifiedOn;
}
