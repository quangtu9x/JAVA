package com.td.application.sharedcore;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.sharedcore.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends IRepository<AppUser> {

    boolean existsByUsernameAndDeletedOnIsNull(String username);

    // 06/10/2026: tài khoản đã khai báo (chưa xoá) nhưng đang bị khoá (isActive=false) - chặn đăng
    // nhập, xem AccountProvisioningChecker#isLocked.
    boolean existsLockedByUsername(String username);

    // 06/10/2026: trong tập username cho trước, trả về những username còn dùng được (chưa xoá, không
    // bị khoá) - lọc thành viên "nhóm người nhận" (ảnh chụp receiver_obj), xem ActiveGroupMembersFilter.
    java.util.Set<String> findActiveUsernamesIn(java.util.Collection<String> usernames);

    boolean existsByUsernameAndIdNotAndDeletedOnIsNull(String username, UUID id);

    boolean existsByKeycloakSubjectAndDeletedOnIsNull(String keycloakSubject);

    Optional<AppUser> findByIdAndDeletedOnIsNull(UUID id);

    Optional<AppUser> findByKeycloakSubjectAndDeletedOnIsNull(String keycloakSubject);

    Optional<AppUser> findByUsernameAndDeletedOnIsNull(String username);

    // BL-118: "Kiêm nhiệm" — danh sách chức vụ kiêm nhiệm của 1 người dùng chính, dùng cho
    // qlvb/profile (user_other_obj) — xem GetUserOrganizationProfileUseCase.
    List<AppUser> findByParentUserIdAndDeletedOnIsNull(UUID parentUserId);

    // Gap #5 (docs/analysis/18-checklist-port-kiem-nhiem-domino-sang-java.md, 08/09/2026): badge
    // "có kiêm nhiệm" ĐÚNG TOÀN CỤC (thay cho best-effort trong trang ở ItemsList.jsx trước đây) —
    // dùng cho GetAppUserUseCase (1 bản ghi: existsBy... rẻ hơn fetch cả list).
    boolean existsByParentUserIdAndDeletedOnIsNull(UUID parentUserId);

    // Cùng mục đích, cho SearchAppUsersUseCase: nạp 1 lần cho CẢ TRANG (tránh N+1 — cùng convention
    // với AppUserRoleGroupRepository#findByAppUserIdInAndDeletedOnIsNull).
    List<AppUser> findByParentUserIdInAndDeletedOnIsNull(List<UUID> parentUserIds);

    // Bo sung 09/09/2026 (GetLegacyOrganizationTreeUseCase#execute includeUsers - node la "user"
    // cho man "Nhom xem lich tong hop"): nap 1 lan CHO CA CAY, gom theo organizationId - tranh N+1.
    List<AppUser> findByOrganizationIdInAndDeletedOnIsNull(List<UUID> organizationIds);

    // 15/10/2026 (cache cây cơ cấu): nạp 1 lần mọi "người đại diện nhận" (Organization.receiverId) của
    // CẢ cây, thay cho findByIdAndDeletedOnIsNull từng node (N+1, cây thật ~50k node).
    List<AppUser> findByIdInAndDeletedOnIsNull(java.util.Collection<UUID> ids);

    List<AppUser> findAllByDeletedOnIsNullOrderByFullNameAsc();

    Page<AppUser> search(SearchAppUsersRequest request, Pageable pageable);
}
