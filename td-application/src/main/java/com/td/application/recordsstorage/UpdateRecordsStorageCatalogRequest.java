package com.td.application.recordsstorage;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class UpdateRecordsStorageCatalogRequest {

    @Size(max = 500, message = "Tên danh mục tối đa 500 ký tự")
    private String category;

    @Size(max = 100, message = "Số, ký hiệu tối đa 100 ký tự")
    private String code;

    private String phong;
    private UUID phongId;
    private String firstTopic;
    private UUID firstTopicId;
    private String secondTopic;
    private UUID secondTopicId;

    private String storagePeriod;
    private Integer yearInception;
    private Integer sortOrder;
    private String note;
    private Integer numberOfDocumentNumber;
    private String kyhieu;
    private String internalUnitObj;
    private String individualObj;
    private String identifier;
}
