package com.td.infrastructure.persistence.repository;

import com.td.domain.privatecatalog.PrivateCategory;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrivateCategoryJpaRepository extends BaseRepository<PrivateCategory> {

    boolean existsByCodeAndDeletedOnIsNull(String code);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN TRUE ELSE FALSE END FROM PrivateCategory c WHERE c.code = :code AND c.id <> :id AND c.deletedOn IS NULL")
    boolean existsByCodeAndIdNotAndDeletedOnIsNull(@Param("code") String code, @Param("id") UUID id);

    @Query("SELECT c FROM PrivateCategory c WHERE c.id = :id AND c.deletedOn IS NULL")
    Optional<PrivateCategory> findByIdAndDeletedOnIsNull(@Param("id") UUID id);

    // JSTree.jsx (TreeComponent, "nhóm cấu hình riêng" — DXL nhom_nguoi_phathanh_by_identifier):
    // Domino key hoá bằng @Explode(donvi_sudung_identifier;";") — 1 nhóm hiện ra cho MỌI đơn vị liệt
    // kê trong field đó, KHÁC "identifier" (cột thật, người TẠO ra nhóm — dùng bởi màn quản trị
    // nhomnguoinhan/ItemsList.jsx). "donvi_sudung_identifier" chỉ nằm trong dynamic_fields (JSONB,
    // chuỗi nối ";", xem ChiTietModal.jsx#onFinish) nên KHÔNG lọc được qua Specification chung
    // (PostgresPrivateCategoryRepository#identifierFilter, cột thật) — cần native query riêng, so
    // khớp CHÍNH XÁC 1 token trong danh sách nối ";" (không phải LIKE '%x%' đơn thuần — tránh khớp
    // nhầm identifier là tiền tố/hậu tố của identifier khác, vd "SO_A" khớp nhầm "SO_AB").
    //
    // Bugfix 15/09/2026 (báo lỗi người dùng, phát hiện khi test "Chuyển xem để biết" mở
    // ModalNguoiSuDung/ModalDonViPhongBanNoiBo → JSTree.jsx gọi query này): PostgreSQL JDBC không tự
    // suy luận được kiểu tham số khi :identifier là đối số ĐẦU TIÊN/GIỮA của CONCAT (hàm variadic
    // "any") — "ERROR: could not determine data type of parameter $3". Ép kiểu tường minh
    // CAST(:identifier AS text) ở MỌI lần dùng trong CONCAT để Postgres luôn biết kiểu tham số.
    @Query(value = """
        SELECT * FROM private_categories
        WHERE form = :form
          AND deleted_on IS NULL
          AND is_active = true
          AND (
            dynamic_fields->>'donvi_sudung_identifier' = :identifier
            OR dynamic_fields->>'donvi_sudung_identifier' LIKE CONCAT(CAST(:identifier AS text), ';%')
            OR dynamic_fields->>'donvi_sudung_identifier' LIKE CONCAT('%;', CAST(:identifier AS text))
            OR dynamic_fields->>'donvi_sudung_identifier' LIKE CONCAT('%;', CAST(:identifier AS text), ';%')
          )
        ORDER BY sort_order ASC
        """, nativeQuery = true)
    List<PrivateCategory> findByFormAndDonViSuDungIdentifier(
        @Param("form") String form, @Param("identifier") String identifier);

    // 09/10/2026 (port "Sử dụng xin ý kiến" — DXL tudien nhom_{TVUB,TVNB,BTV,BCH,TTDU}_by_identifier):
    // SELECT Form="group_people" & type="<loại nhóm>", khoá @Explode(identifier + ";" +
    // donvi_sudung_identifier) — khớp cột identifier (đơn vị tạo nhóm) HOẶC 1 token của
    // donvi_sudung_identifier (cùng cách so khớp token như truy vấn trên).
    @Query(value = """
        SELECT * FROM private_categories
        WHERE form = :form
          AND deleted_on IS NULL
          AND is_active = true
          AND dynamic_fields->>'type' = :type
          AND (
            identifier = :identifier
            OR dynamic_fields->>'donvi_sudung_identifier' = :identifier
            OR dynamic_fields->>'donvi_sudung_identifier' LIKE CONCAT(CAST(:identifier AS text), ';%')
            OR dynamic_fields->>'donvi_sudung_identifier' LIKE CONCAT('%;', CAST(:identifier AS text))
            OR dynamic_fields->>'donvi_sudung_identifier' LIKE CONCAT('%;', CAST(:identifier AS text), ';%')
          )
        ORDER BY sort_order ASC
        """, nativeQuery = true)
    List<PrivateCategory> findByFormAndTypeAndIdentifier(
        @Param("form") String form, @Param("type") String type, @Param("identifier") String identifier);
}
