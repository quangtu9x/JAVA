package com.td.domain.recordsstorage;

import com.td.domain.common.contracts.AuditableEntity;
import com.td.domain.common.contracts.IAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

// BL-116: "Danh mục hồ sơ lưu trữ" (phongtailieu/demuchoso/danhmuchosocoquan — 1 loại bản ghi lá
// dùng chung bởi cả 3 thư mục FE) — entity RIÊNG (không tái dùng PrivateCategory). Cây phân loại
// (Phông/Đề mục lớn/Đề mục nhỏ) mà bản ghi này thuộc về tái dùng PrivateCategory
// (form=phongtailieu/demuclon/demucnho) — xem migration V1.0.22.
@Entity
@Table(name = "records_storage_catalogs")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecordsStorageCatalog extends AuditableEntity<UUID> implements IAggregateRoot {

    @Column(nullable = false, length = 500)
    private String category;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(length = 300)
    private String phong;

    @Column(name = "phong_id")
    private UUID phongId;

    @Column(name = "first_topic", length = 300)
    private String firstTopic;

    @Column(name = "first_topic_id")
    private UUID firstTopicId;

    @Column(name = "second_topic", length = 300)
    private String secondTopic;

    @Column(name = "second_topic_id")
    private UUID secondTopicId;

    @Column(name = "storage_period", nullable = false, length = 100)
    private String storagePeriod;

    @Column(name = "year_inception", nullable = false)
    private int yearInception;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "number_of_document_number")
    private Integer numberOfDocumentNumber;

    @Column(length = 300)
    private String kyhieu;

    @Column(name = "internal_unit_obj", columnDefinition = "TEXT")
    private String internalUnitObj;

    @Column(name = "individual_obj", columnDefinition = "TEXT")
    private String individualObj;

    @Column(length = 200)
    private String identifier;

    public RecordsStorageCatalog(String category, String code, String phong, UUID phongId,
                                  String firstTopic, UUID firstTopicId, String secondTopic, UUID secondTopicId,
                                  String storagePeriod, int yearInception, Integer sortOrder, String note,
                                  Integer numberOfDocumentNumber, String kyhieu, String internalUnitObj,
                                  String individualObj, String identifier) {
        this.id = UUID.randomUUID();
        this.category = category;
        this.code = code;
        this.phong = phong;
        this.phongId = phongId;
        this.firstTopic = firstTopic;
        this.firstTopicId = firstTopicId;
        this.secondTopic = secondTopic;
        this.secondTopicId = secondTopicId;
        this.storagePeriod = storagePeriod;
        this.yearInception = yearInception;
        this.sortOrder = (sortOrder != null) ? sortOrder : 0;
        this.note = note;
        this.numberOfDocumentNumber = numberOfDocumentNumber;
        this.kyhieu = kyhieu;
        this.internalUnitObj = internalUnitObj;
        this.individualObj = individualObj;
        this.identifier = identifier;
    }

    public RecordsStorageCatalog update(String category, String code, String phong, UUID phongId,
                                         String firstTopic, UUID firstTopicId, String secondTopic, UUID secondTopicId,
                                         String storagePeriod, Integer yearInception, Integer sortOrder, String note,
                                         Integer numberOfDocumentNumber, String kyhieu, String internalUnitObj,
                                         String individualObj, String identifier) {
        if (category != null)       this.category = category;
        if (code != null)           this.code = code;
        this.phong          = phong;
        this.phongId        = phongId;
        this.firstTopic     = firstTopic;
        this.firstTopicId   = firstTopicId;
        this.secondTopic    = secondTopic;
        this.secondTopicId  = secondTopicId;
        if (storagePeriod != null)  this.storagePeriod = storagePeriod;
        if (yearInception != null)  this.yearInception = yearInception;
        if (sortOrder != null)      this.sortOrder = sortOrder;
        this.note                       = note;
        this.numberOfDocumentNumber     = numberOfDocumentNumber;
        this.kyhieu                     = kyhieu;
        this.internalUnitObj            = internalUnitObj;
        this.individualObj              = individualObj;
        if (identifier != null)     this.identifier = identifier;
        return this;
    }
}
