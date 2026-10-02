package com.td.infrastructure.persistence.repository;

import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileMetadataJpaRepository extends JpaRepository<FileMetadataEntity, UUID> {

    List<FileMetadataEntity> findAllByDocumentId(UUID documentId);

    Optional<FileMetadataEntity> findByIdAndDocumentId(UUID id, UUID documentId);

    // DT-007 (27/08/2026): loc tep dinh kem theo DUNG 1 lan xu ly (draft_process) cu the trong CUNG
    // 1 du thao cha - xem javadoc FileMetadataEntity#processId.
    List<FileMetadataEntity> findAllByDocumentIdAndProcessId(UUID documentId, UUID processId);

    // Bugfix 31/08/2026 ("o danh sach chua thay hien thi tep dinh kem" - man "Vao so van ban den"):
    // ban 1-document (findAllByDocumentId) chi phu hop man CHI TIET; ap dung y nguyen cho TUNG dong
    // cua 1 trang danh sach (co the toi 200 dong, xem GetSoVanBanDenUseCase#buildPageable) se tao N+1
    // query - can 1 query GOM theo danh sach document_id, xem ListFilesForDocumentsUseCaseImpl.
    List<FileMetadataEntity> findAllByDocumentIdIn(Collection<UUID> documentIds);

    // Yeu cau nguoi dung 15/09/2026 (is_primary that - xem javadoc FileMetadataEntity#isPrimary):
    // demote tep primary CU truoc khi danh dau tep MOI la primary, xem
    // UploadFileUseCaseImpl#demoteExistingPrimary.
    List<FileMetadataEntity> findAllByDocumentIdAndIsPrimaryTrue(UUID documentId);

    // Yeu cau nguoi dung 25/09/2026 (OnlyOffice luu thanh tep MOI - xem SaveOnlyOfficeCallbackUseCaseImpl):
    // tep moi cua 1 phien soan thao (de ghi tiep vao dung tep khi Document Server luu nhieu lan) + so ban da
    // sua ra tu 1 tep goc (dua vao key phien soan thao, de lan mo sau khong dung lai cache cu).
    Optional<FileMetadataEntity> findFirstByDocumentIdAndSourceFileIdAndEditSessionKey(
            UUID documentId, UUID sourceFileId, String editSessionKey);

    long countBySourceFileId(UUID sourceFileId);

    // Yeu cau nguoi dung 24/09/2026 (xem SupersedeDocumentFilesUseCaseImpl): danh dau tep "da duoc thay
    // the" - NATIVE vi dong vua danh dau se lap tuc bi @SQLRestriction cua entity an di.
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = """
            UPDATE file_metadata SET superseded_at = CURRENT_TIMESTAMP, superseded_by = :supersededBy
            WHERE document_id = :documentId AND id IN (:ids) AND superseded_at IS NULL
            """, nativeQuery = true)
    int markSuperseded(@org.springframework.data.repository.query.Param("documentId") UUID documentId,
                       @org.springframework.data.repository.query.Param("ids") Collection<UUID> ids,
                       @org.springframework.data.repository.query.Param("supersededBy") UUID supersededBy);
}
