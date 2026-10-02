package com.td.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;

// Yeu cau nguoi dung 24/09/2026 ("tep cu chua xoa, chi hien thi tep moi cap nhat, khong cho tai tep
// cu"): tep DA DUOC THAY THE (superseded_at co gia tri, xem V1.0.64/SupersedeDocumentFilesUseCaseImpl)
// bi an khoi MOI truy van JPA tren entity nay - liet ke, tai, xem, doi ten, xoa, ky so, copy... - de
// khong 1 duong nao (hien tai hay them sau) vo tinh lo tep cu ra ngoai. Dong va object MinIO GIU
// NGUYEN. Muon doc lai tep da thay the thi PHAI dung native SQL co chu dich (khong co API nao lam vay).
@Entity
@Table(name = "file_metadata")
@SQLRestriction("superseded_at IS NULL")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadataEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "document_id")
    private UUID documentId;

    // DT-007 (27/08/2026, "quan ly tep dinh kem theo id cha, draft_process van the hien dung lan
    // gan kem"): id vet xu ly (vd draft_process_documents.id) ma tep duoc dinh kem CUNG LUC -
    // NULL neu tep khong gan voi 1 lan xu ly cu the (moi module khac ngoai duthao). Van dung
    // document_id lam khoa storage/list chinh, khong thay the.
    @Column(name = "process_id")
    private UUID processId;

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

    @Column(name = "file_category", nullable = false)
    private String fileCategory;

    @Column(name = "bucket_name", nullable = false)
    private String bucketName;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "is_public")
    private Boolean isPublic;

    @Column(name = "description")
    private String description;

    // Yeu cau nguoi dung 08/09/2026 ("van ban nhan duoc bang chuc nang 'chuyen xu ly' thi khong cho
    // xoa dinh kem goc") - xem migration V1.0.50 + javadoc DeleteFileUseCaseImpl: TRUE = dong nay CHI
    // tham chieu (khong so huu) object MinIO tai file_path/bucket_name (vd ban sao
    // vbden.forward_unit "doc chung" voi van ban goc o tenant khac) - PHAI chan xoa.
    //
    // Bugfix 09/09/2026 (bao loi nguoi dung: upload dinh kem vbdi 500 "null value in column
    // is_shared violates not-null constraint"): cot DB la NOT NULL DEFAULT FALSE (migration
    // V1.0.50), nhung field Java truoc day KHONG co gia tri mac dinh (null) - Hibernate LUON liet ke
    // du moi cot trong cau INSERT (ke ca gia tri null tuong minh) nen cot DEFAULT cua DB KHONG BAO
    // GIO duoc ap dung cho insert qua JPA (chi ap dung khi cau INSERT thuc su BO QUA ten cot) - moi
    // noi tao FileMetadataEntity qua builder() ma quen goi .isShared(...) (UploadFileUseCaseImpl,
    // CopyDocumentFilesUseCaseImpl, PrepareVbdiDeliveryFilesUseCaseImpl - da sua tuong minh them cho
    // ro y, xem cac file do) deu vi pham NOT NULL. @Builder.Default lam luoi an toan cho BAT KY noi
    // nao (ke ca sau nay) quen goi .isShared(...) qua builder.
    @Builder.Default
    @Column(name = "is_shared")
    private Boolean isShared = Boolean.FALSE;

    // Yeu cau nguoi dung 15/09/2026 (port ky so vbdi/vbden - "giu file goc, tep ky luu moi, thay
    // the tep chinh thuc"): tep CHINH THUC/hien hanh cua document_id nay (vd tep da ky so) - chi 1
    // tep is_primary=true tai 1 thoi diem cho cung document_id (xem UploadFileUseCaseImpl#
    // demoteExistingPrimary). @Builder.Default bat buoc - cung ly do @isShared o tren (Hibernate
    // luon INSERT du moi cot, DB DEFAULT khong ap dung qua JPA).
    @Builder.Default
    @Column(name = "is_primary")
    private Boolean isPrimary = Boolean.FALSE;

    // Yeu cau nguoi dung 24/09/2026 - xem ghi chu @SQLRestriction o dau lop. Chi ghi qua native UPDATE
    // (FileMetadataJpaRepository#markSuperseded); entity load qua JPA luon co gia tri null.
    @Column(name = "superseded_at")
    private LocalDateTime supersededAt;

    @Column(name = "superseded_by")
    private UUID supersededBy;

    // Yeu cau nguoi dung 25/09/2026 (V1.0.65): tep MOI duoc sua ra tu tep goc nao (OnlyOffice luu thanh
    // tep moi, khong ghi de) + key phien soan thao da tao ra no - xem SaveOnlyOfficeCallbackUseCaseImpl.
    @Column(name = "source_file_id")
    private UUID sourceFileId;

    @Column(name = "edit_session_key", length = 200)
    private String editSessionKey;
}
