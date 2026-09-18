package com.td.application.sharedcore;

import com.td.application.common.security.CurrentUserProvider;
import com.td.domain.sharedcore.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Đi lên {@link Organization#getParentId()} tới tổ tiên gần nhất có {@code form=="agency"} (Sở/cơ
 * quan) — thuật toán tách ra từ
 * {@code GetLegacyOrganizationTreeUseCase#resolveCurrentUserAgencyId()} (bugfix 03/09/2026) để dùng
 * lại ở nơi khác cần biết "tổ chức X thuộc cơ quan/Sở nào" mà không cần tải cả cây.
 *
 * <p>Dùng bởi {@link SendVbdiDocumentUseCase} (18/09/2026, hoàn thiện "phát hành đi → đến nội bộ"):
 * so khớp agency-id của 1 tổ chức được chọn ở cây "Cơ quan, đơn vị thuộc Tỉnh" (contact_unit) với
 * agency-id của CHÍNH người gửi — bằng nhau nghĩa là người dùng chọn nhầm cột (chọn chính cơ quan
 * mình/phòng ban con của mình ở cây toàn tỉnh thay vì cây "Đơn vị nội bộ" hẹp) — xem javadoc
 * {@code SendVbdiDocumentUseCase#reclassifyIfOwnAgency}.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(value = "identityTransactionManager", readOnly = true)
public class OrganizationAgencyResolver {

    private static final String FORM_AGENCY = "agency";

    private final OrganizationRepository organizationRepository;
    private final AppUserRepository appUserRepository;
    private final CurrentUserProvider currentUserProvider;

    /**
     * Đi lên {@code parentId} từ {@code organizationId} tới tổ tiên gần nhất có form "agency" (hoặc
     * chính nó nếu đã là "agency"). {@code Optional.empty()} nếu không tìm thấy (tổ chức không tồn
     * tại/đã xoá, hoặc không có tổ tiên nào là "agency" — vd đứt gãy dữ liệu) — fail-open, KHÔNG
     * throw, để nơi gọi tự quyết định xử lý (thường là "không reclassify/không lọc gì").
     */
    public Optional<UUID> resolveAgencyId(UUID organizationId) {
        UUID currentId = organizationId;
        Set<UUID> visited = new HashSet<>();
        while (currentId != null && visited.add(currentId)) {
            var orgOpt = organizationRepository.findByIdAndDeletedOnIsNull(currentId);
            if (orgOpt.isEmpty()) {
                return Optional.empty();
            }
            Organization org = orgOpt.get();
            if (FORM_AGENCY.equals(OrganizationHierarchyRules.resolveNodeType(org))) {
                return Optional.of(org.getId());
            }
            currentId = org.getParentId();
        }
        return Optional.empty();
    }

    /** Agency-id của user đang đăng nhập hiện tại — {@code Optional.empty()} nếu không resolve được. */
    public Optional<UUID> resolveCurrentUserAgencyId() {
        String username = currentUserProvider.currentUsername().orElse(null);
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        var userOpt = appUserRepository.findByUsernameAndDeletedOnIsNull(username);
        if (userOpt.isEmpty() || userOpt.get().getOrganizationId() == null) {
            return Optional.empty();
        }
        return resolveAgencyId(userOpt.get().getOrganizationId());
    }
}
