package com.td.domain.categories;

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

// BL-118 (19/08/2026): tệp đính kèm cho "danh mục dùng chung" (Category, control-plane — Banner/
// Footer/Hướng dẫn sử dụng, xem CategoriesController) — cố ý KHÔNG dùng lại bảng `file_metadata`
// (tenant-scoped, phục vụ Document) vì nội dung Banner/Footer/Hướng dẫn sử dụng dùng CHUNG cho MỌI
// tenant (control-plane), trong khi `file_metadata` sống trong DATABASE RIÊNG của từng tenant — nếu
// tái dùng, mỗi tenant sẽ có 1 bản file khác nhau cho cùng 1 Category dùng chung, sai với đúng chủ
// đích "dùng chung". Object lưu trữ vật lý vẫn tái dùng CHUNG 1 MinioService/bucket (không tenant-
// scoped, xác nhận qua MinIOProperties — chỉ 1 bucket cấu hình tĩnh cho toàn app) — chỉ có bảng
// METADATA này là entity control-plane mới, theo đúng khuyến nghị của MultiPersistenceUnitJpaConfig
// (sửa CẢ 2 chỗ: exclude khỏi EntityManagerFactory nghiệp vụ + thêm vào controlPlaneEntityManagerFactory).
@Entity
@Table(name = "category_file_metadata")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoryFileMetadata extends AuditableEntity<UUID> implements IAggregateRoot {

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false, unique = true, length = 255)
    private String storedFilename;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(name = "file_extension", length = 20)
    private String fileExtension;

    @Column(name = "bucket_name", nullable = false, length = 100)
    private String bucketName;

    @Column(columnDefinition = "TEXT")
    private String description;

    public CategoryFileMetadata(UUID categoryId, String originalFilename, String storedFilename,
                                 String filePath, long fileSize, String contentType,
                                 String fileExtension, String bucketName, String description) {
        this.id = UUID.randomUUID();
        this.categoryId = categoryId;
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.fileExtension = fileExtension;
        this.bucketName = bucketName;
        this.description = description;
    }
}
