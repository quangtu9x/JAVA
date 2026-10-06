package com.td.infrastructure.persistence.identity;

import com.td.infrastructure.persistence.repository.BaseRepository;

import com.td.domain.sharedcore.AppUser;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserJpaRepository extends BaseRepository<AppUser> {

    // BL-118: "parentUserId" là cột cụ thể (không phải "id" kế thừa generic từ AbstractEntity<T>) —
    // an toàn derive thẳng, không cần @Query tường minh (khác trường hợp so sánh "id" đã gặp lỗi
    // "Cannot compare... java.lang.Object... java.util.UUID" — xem CategoryFileMetadataJpaRepository).
    List<AppUser> findByParentUserIdAndDeletedOnIsNull(UUID parentUserId);

    // Gap #5 (docs/analysis/18-checklist-port-kiem-nhiem-domino-sang-java.md, 08/09/2026): badge
    // "có kiêm nhiệm" đúng toàn cục — cùng lý do an toàn derive thẳng như trên ("parentUserId" là cột
    // cụ thể, không phải "id" kế thừa generic).
    boolean existsByParentUserIdAndDeletedOnIsNull(UUID parentUserId);

    List<AppUser> findByParentUserIdInAndDeletedOnIsNull(List<UUID> parentUserIds);

    // Bo sung 09/09/2026 (GetLegacyOrganizationTreeUseCase#execute includeUsers) - "organizationId"
    // la cot cu the (khong phai "id" ke thua generic) - an toan derive thang, cung ly do nhu tren.
    List<AppUser> findByOrganizationIdInAndDeletedOnIsNull(List<UUID> organizationIds);

    // BL-118 (tiếp, 19/08/2026): "Danh sách người dùng" (tương đương qlvb/danhmuc/DanhSachNguoiDung)
    // — toàn bộ user đang hoạt động, KHÔNG phân trang (FE cache 1 lần vào localStorage, dùng cho
    // hàng chục dropdown/lookup "người xử lý"/"người ký"). "deletedOn" không phải "id" nên an toàn
    // derive thẳng.
    List<AppUser> findAllByDeletedOnIsNullOrderByFullNameAsc();

    boolean existsByUsernameAndDeletedOnIsNull(String username);

    // 06/10/2026: @Query tường minh - derive "IsActive..." dễ bị parse nhầm từ khoá "Is".
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN TRUE ELSE FALSE END FROM AppUser u WHERE u.username = :username AND u.isActive = false AND u.deletedOn IS NULL")
    boolean existsLockedByUsername(@Param("username") String username);

    @Query("SELECT u.username FROM AppUser u WHERE u.username IN :usernames AND u.isActive = true AND u.deletedOn IS NULL")
    List<String> findActiveUsernamesIn(@Param("usernames") java.util.Collection<String> usernames);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN TRUE ELSE FALSE END FROM AppUser u WHERE u.username = :username AND u.id <> :id AND u.deletedOn IS NULL")
    boolean existsByUsernameAndIdNotAndDeletedOnIsNull(@Param("username") String username, @Param("id") UUID id);

    boolean existsByKeycloakSubjectAndDeletedOnIsNull(String keycloakSubject);

    @Query("SELECT u FROM AppUser u WHERE u.id = :id AND u.deletedOn IS NULL")
    Optional<AppUser> findByIdAndDeletedOnIsNull(@Param("id") UUID id);

    @Query("SELECT u FROM AppUser u WHERE u.keycloakSubject = :keycloakSubject AND u.deletedOn IS NULL")
    Optional<AppUser> findByKeycloakSubjectAndDeletedOnIsNull(@Param("keycloakSubject") String keycloakSubject);

    @Query("SELECT u FROM AppUser u WHERE u.username = :username AND u.deletedOn IS NULL")
    Optional<AppUser> findByUsernameAndDeletedOnIsNull(@Param("username") String username);
}
