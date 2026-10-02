package com.td.domain.sharedcore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

// Ban sao "identity" cua FileMetadataEntity (td-infrastructure/persistence/entity) - PHAI đặt trong
// package này (khác com.td.infrastructure.persistence.entity như FileMetadataEntity) vì
// MultiPersistenceUnitJpaConfig#identityEntityManagerFactory CHỈ scan entity TRỰC TIẾP trong
// com.td.domain.sharedcore (xem javadoc lớp đó, scanEntitiesUnderPackage) - đây là lý do DUY NHẤT
// entity này nằm ở td-domain thay vì td-infrastructure, KHÔNG phải vì nó là 1 domain aggregate thật
// sự - cố ý KHÔNG dùng AuditableEntity/private setter như các entity sharedcore khác cùng package,
// giữ nguyên phong cách @Builder đơn giản của FileMetadataEntity mà nó thay thế.
//
// Dùng cho MỌI tính năng cần lưu tệp đính kèm GẮN VỚI DANH TÍNH người dùng (AppUser/Organization...)
// thay vì gắn với tenant nghiệp vụ đang xử lý request - xem bug thật đã gặp (22/09/2026): ảnh chữ ký
// người dùng (UserSignatureImage*, sharedcore) từng dùng file_metadata (tenant-scoped) nên admin cấu
// hình lúc tenant của admin resolve khác tenant của chính người dùng đó (AppUser/Organization vốn
// KHÔNG nhân bản theo tenant - xem GetUserOrganizationProfileUseCase#... /ResolveUserTenantUseCase),
// khiến ảnh "biến mất" khi chính người dùng tự đọc lại. Object vật lý vẫn dùng CHUNG 1 MinioService/
// bucket (không tenant-scoped, giống CategoryFileMetadata dùng cho Banner/Footer/Hướng dẫn sử dụng ở
// control-plane) - chỉ riêng bảng METADATA này cần tách khỏi file_metadata.
@Entity
@Table(name = "identity_file_metadata")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityFileMetadata {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    // Phân biệt nhiều tính năng dùng CHUNG bảng này (vd "user-signature", sau này có thể thêm tính
    // năng khác cần tệp đính kèm theo danh tính) - chỉ dùng tách thư mục MinIO, KHÔNG dùng để lọc
    // truy vấn (truy vấn luôn theo documentId, giống hệt quy ước file_metadata).
    @Column(name = "module", nullable = false)
    private String module;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false, unique = true)
    private String storedFilename;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_extension")
    private String fileExtension;

    @Column(name = "bucket_name", nullable = false)
    private String bucketName;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "description")
    private String description;

    @Builder.Default
    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = Boolean.FALSE;
}
