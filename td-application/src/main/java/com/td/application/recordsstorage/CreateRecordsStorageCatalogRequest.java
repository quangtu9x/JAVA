package com.td.application.recordsstorage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateRecordsStorageCatalogRequest {

    @NotBlank(message = "Tên danh mục không được để trống")
    @Size(max = 500, message = "Tên danh mục tối đa 500 ký tự")
    private String category;

    @NotBlank(message = "Số, ký hiệu không được để trống")
    @Size(max = 100, message = "Số, ký hiệu tối đa 100 ký tự")
    private String code;

    private String phong;
    private UUID phongId;
    private String firstTopic;
    private UUID firstTopicId;
    private String secondTopic;
    private UUID secondTopicId;

    @NotBlank(message = "Thời hạn bảo quản không được để trống")
    private String storagePeriod;

    @NotNull(message = "Năm hình thành hồ sơ không được để trống")
    private Integer yearInception;

    private Integer sortOrder = 0;
    private String note;
    private Integer numberOfDocumentNumber;
    private String kyhieu;
    private String internalUnitObj;
    private String individualObj;

    // Mã định danh đơn vị/phòng ban (currentUser.identifier bên FE).
    private String identifier;
}
