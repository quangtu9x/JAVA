package com.td.application.tenant;

import com.td.domain.sharedcore.AppUser;
import com.td.domain.sharedcore.Organization;
import com.td.domain.tenant.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * BL-118 (bugfix NGHIÊM TRỌNG, 20/08/2026): xác định tenant mà VĂN BẢN/NGHIỆP VỤ mới của 1 username
 * nên được lưu vào — dùng khi Authentication (Keycloak JWT hoặc Tdtoken) không mang claim
 * {@code tenant_id}/{@code identifier} (trường hợp THƯỜNG XUYÊN xảy ra, Keycloak chưa từng cấu hình
 * claim này cho bất kỳ user nào).
 *
 * <p><b>Nghiệp vụ (xác nhận 20/08/2026, qua đối chiếu database thật)</b>: {@code AppUser}/
 * {@code Organization} (và toàn bộ Nhóm vai trò/AppRole/AppPermission) là "danh mục dùng chung" —
 * KHÔNG nhân bản theo tenant, sống CỐ ĐỊNH ở 1 database duy nhất (đọc qua
 * {@code identityEntityManagerFactory}, xem {@code MultiPersistenceUnitJpaConfig} — LUÔN trỏ tenant
 * mặc định, độc lập hoàn toàn với {@link com.td.infrastructure.tenant.TenantContext} của request
 * đang xử lý). Nhờ vậy, tra {@link com.td.application.sharedcore.AppUserRepository}/
 * {@link com.td.application.sharedcore.OrganizationRepository} ở use case này KHÔNG còn là bài toán
 * "gà-và-trứng" (không cần quét qua từng tenant nghiệp vụ để "tìm" AppUser nữa, khác các đợt sửa
 * trước — xem lịch sử trong {@code docs/migration-backlog/04-migration-status.md} — luôn tra được
 * NGAY LẬP TỨC, không cần cache).
 *
 * <p>Riêng "Cơ quan" (tổ tiên gần nhất có {@code nodeType == "agency"}) mới có thể tự khởi tạo 1
 * database (tenant) RIÊNG cho dữ liệu nghiệp vụ/văn bản — "Đơn vị"/"Phòng ban" con bên trong 1 Cơ
 * quan KHÔNG có database riêng, dùng CHUNG tenant của Cơ quan đó. Vì vậy: đi lên
 * {@code Organization.parentId} từ tổ chức trực tiếp của user tới "Cơ quan" gần nhất, đọc
 * {@code linkedTenantId} của node đó (admin cấu hình qua "Quản lý đơn vị") — có giá trị VÀ tenant đó
 * còn active trong registry ({@link TenantRepository}) thì dùng; ngược lại {@link #execute} trả về
 * rỗng, caller ({@code TenantResolver}) fallback về {@code defaultTenantId}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResolveUserTenantUseCase {

    // Khớp đúng "form" mà GetUserOrganizationProfileUseCase#FORM_AGENCY đang dùng cho cấp "Cơ quan".
    private static final String FORM_AGENCY = "agency";

    private final com.td.application.sharedcore.AppUserRepository appUserRepository;
    private final com.td.application.sharedcore.OrganizationRepository organizationRepository;
    private final TenantRepository tenantRepository;

    @Transactional("identityTransactionManager")
    public Optional<String> execute(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        Optional<AppUser> user = appUserRepository.findByUsernameAndDeletedOnIsNull(username);
        if (user.isEmpty()) {
            return Optional.empty();
        }

        String linkedTenantId = findAgencyAncestor(user.get().getOrganizationId())
            .map(Organization::getLinkedTenantId)
            .orElse(null);
        if (linkedTenantId == null || linkedTenantId.isBlank()) {
            return Optional.empty();
        }

        if (!isActiveTenant(linkedTenantId)) {
            log.warn("To chuc cua username '{}' lien ket toi tenant '{}' nhung tenant nay khong con "
                + "active/khong ton tai trong registry - bo qua, fallback ve tenant mac dinh", username, linkedTenantId);
            return Optional.empty();
        }
        return Optional.of(linkedTenantId);
    }

    private boolean isActiveTenant(String tenantId) {
        return tenantRepository.findByTenantIdAndDeletedOnIsNull(tenantId)
            .map(Tenant::isActive)
            .orElse(false);
    }

    private Optional<Organization> findAgencyAncestor(UUID organizationId) {
        UUID currentId = organizationId;
        Set<UUID> visited = new HashSet<>();
        while (currentId != null && visited.add(currentId)) {
            Optional<Organization> orgOpt = organizationRepository.findByIdAndDeletedOnIsNull(currentId);
            if (orgOpt.isEmpty()) {
                break;
            }
            Organization org = orgOpt.get();
            if (FORM_AGENCY.equals(org.getNodeType())) {
                return Optional.of(org);
            }
            currentId = org.getParentId();
        }
        return Optional.empty();
    }
}
