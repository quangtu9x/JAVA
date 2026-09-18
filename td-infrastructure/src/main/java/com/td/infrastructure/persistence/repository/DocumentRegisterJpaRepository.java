package com.td.infrastructure.persistence.repository;

import com.td.domain.documentregister.DocumentRegister;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRegisterJpaRepository extends BaseRepository<DocumentRegister> {

    @Query("SELECT d FROM DocumentRegister d WHERE d.id = :id AND d.deletedOn IS NULL")
    Optional<DocumentRegister> findByIdAndDeletedOnIsNull(@Param("id") UUID id);

    @Query("SELECT d FROM DocumentRegister d WHERE d.id IN :ids AND d.deletedOn IS NULL")
    List<DocumentRegister> findAllByIdInAndDeletedOnIsNull(@Param("ids") List<UUID> ids);

    boolean existsByIdentifierAndDocumentRegisterTypeAndNameAndDeletedOnIsNull(
        String identifier, String documentRegisterType, String name);

    // Lưu ý (xem docs/HUONG_DAN_NHAN_BAN_VA_SUA_API.md, mục "Bước 5"): derived query dạng
    // "...IdNot..." trên repository generic (BaseRepository<T> kế thừa JpaRepository<T, UUID>) khiến
    // Spring Data suy luận sai kiểu của "id" (ra java.lang.Object thay vì UUID) và nổ lỗi
    // "Cannot compare left expression of type 'java.lang.Object' with right expression of type
    // 'java.util.UUID'" ngay lúc khởi động ứng dụng — PHẢI viết JPQL tường minh bằng @Query, đúng
    // pattern đã dùng cho Category/PrivateCategory/AppUser/Organization.
    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN TRUE ELSE FALSE END FROM DocumentRegister d " +
        "WHERE d.identifier = :identifier AND d.documentRegisterType = :documentRegisterType " +
        "AND d.name = :name AND d.id <> :id AND d.deletedOn IS NULL")
    boolean existsByIdentifierAndDocumentRegisterTypeAndNameAndIdNotAndDeletedOnIsNull(
        @Param("identifier") String identifier,
        @Param("documentRegisterType") String documentRegisterType,
        @Param("name") String name,
        @Param("id") UUID id);

    boolean existsByIdentifierAndDocumentRegisterTypeAndIsDefaultTrueAndDeletedOnIsNull(
        String identifier, String documentRegisterType);

    // BL-210/211 (Nhập mới VBDen): tương đương qlvb/danhmuc/DanhSachSo lấy đúng 1 sổ MẶC ĐỊNH của
    // 1 đơn vị/loại sổ — không so sánh "id" nên an toàn derive (không dính lỗi type-erasure).
    Optional<DocumentRegister> findByIdentifierAndDocumentRegisterTypeAndIsDefaultTrueAndDeletedOnIsNull(
        String identifier, String documentRegisterType);

    // BL-210/211 (19/08/2026): danh sách sổ CÓ THỂ CHỌN khi Nhập mới (dropdown "Chọn sổ văn bản") —
    // mọi sổ khớp identifier + loại, CHƯA khoá. Không so sánh "id" nên an toàn derive.
    List<DocumentRegister> findByIdentifierAndDocumentRegisterTypeAndIsLockedFalseAndDeletedOnIsNull(
        String identifier, String documentRegisterType);

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN TRUE ELSE FALSE END FROM DocumentRegister d " +
        "WHERE d.identifier = :identifier AND d.documentRegisterType = :documentRegisterType " +
        "AND d.isDefault = TRUE AND d.id <> :id AND d.deletedOn IS NULL")
    boolean existsByIdentifierAndDocumentRegisterTypeAndIsDefaultTrueAndIdNotAndDeletedOnIsNull(
        @Param("identifier") String identifier,
        @Param("documentRegisterType") String documentRegisterType,
        @Param("id") UUID id);
}
