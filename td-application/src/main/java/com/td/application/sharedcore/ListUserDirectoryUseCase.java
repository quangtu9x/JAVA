package com.td.application.sharedcore;

import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * BL-118 (tiếp, 19/08/2026): tương đương {@code qlvb/danhmuc/DanhSachNguoiDung} (Domino thật) —
 * toàn bộ người dùng đang hoạt động, kèm tên hiển thị đơn vị (department/unit/agency/agencyLevel)
 * suy luận qua {@link GetUserOrganizationProfileUseCase#buildProfileForUser} (tái dùng nguyên logic
 * đã có cho {@code qlvb/profile}, không viết lại).
 *
 * <p><b>Vì sao cần endpoint mới thay vì trỏ FE sang {@code SharedCoreUsersController}</b>: endpoint
 * quản trị "Người dùng" hiện có phân trang (tối đa 200/trang) và {@code AppUserDto} không có field
 * denormalize tổ chức — không phù hợp nhu cầu "lấy toàn bộ 1 lần, cache client" mà FE đang cần cho
 * hàng chục dropdown lookup khắp module vbden.</p>
 *
 * <p><b>Bối cảnh phát hiện</b>: {@code getAllUser()} (FE) trước đây gọi thẳng
 * {@code qlvb/danhmuc/DanhSachNguoiDung} (Domino) — token Tdtoken do Java phát hành (chỉ có
 * sub/username/name/roles) không có claim tổ chức (dbpath/identifier...) mà
 * {@code TDDatabaseConnection}/{@code TDTokenObject} phía Domino cần để lọc theo đơn vị, nên API đó
 * luôn âm thầm trả về danh sách RỖNG cho mọi người dùng (không phải lỗi HTTP cứng).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(value = "identityTransactionManager", readOnly = true)
public class ListUserDirectoryUseCase {

    private final AppUserRepository appUserRepository;
    private final GetUserOrganizationProfileUseCase getUserOrganizationProfileUseCase;

    public List<UserDirectoryEntryDto> execute() {
        List<AppUser> users = appUserRepository.findAllByDeletedOnIsNullOrderByFullNameAsc();
        return users.stream().map(this::toDirectoryEntry).toList();
    }

    private UserDirectoryEntryDto toDirectoryEntry(AppUser user) {
        var orgProfile = getUserOrganizationProfileUseCase.buildProfileForUser(user);
        var dto = new UserDirectoryEntryDto();
        dto.setUsername(orgProfile.getUsername());
        dto.setName(orgProfile.getFullName());
        dto.setPosition(orgProfile.getPosition());
        dto.setDepartment(orgProfile.getDepartmentName());
        dto.setUnit(orgProfile.getUnitName());
        dto.setAgency(orgProfile.getAgencyName());
        dto.setAgencyLevel(orgProfile.getAgencyLevelName());
        dto.setIdentifier(orgProfile.getIdentifier());
        return dto;
    }
}
