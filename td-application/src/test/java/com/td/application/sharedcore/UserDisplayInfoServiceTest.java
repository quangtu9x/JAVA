package com.td.application.sharedcore;

import com.td.domain.sharedcore.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 09/09/2026 (yêu cầu người dùng "chưa thấy có thông tin đơn vị của người dùng khi xem (tài khoản
 * thuộc cơ quan, đơn vị, phòng ban nào)"): test {@link UserDisplayInfoService} — xem javadoc lớp về
 * lý do gộp cả 3 cấp thay vì chỉ dùng {@code unitName}.
 */
@ExtendWith(MockitoExtension.class)
class UserDisplayInfoServiceTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private GetUserOrganizationProfileUseCase getUserOrganizationProfileUseCase;

    private UserDisplayInfoService service;

    @BeforeEach
    void setUp() {
        service = new UserDisplayInfoService(appUserRepository, getUserOrganizationProfileUseCase);
    }

    @Test
    void resolve_withAllThreeLevels_shouldJoinAgencyUnitDepartment() {
        var appUser = new AppUser("kc-1", "nhanvien01", "Nguyen Van A", "a@x.com", null, null, true);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nhanvien01")).thenReturn(Optional.of(appUser));
        var profile = new UserOrganizationProfileDto();
        profile.setFullName("Nguyen Van A");
        profile.setAgencyName("So Khoa hoc va Cong nghe");
        profile.setUnitName("Chi cuc Tieu chuan");
        profile.setDepartmentName("Phong Tong hop");
        when(getUserOrganizationProfileUseCase.buildProfileForUser(appUser)).thenReturn(profile);

        var result = service.resolve("nhanvien01");

        assertThat(result.fullName()).isEqualTo("Nguyen Van A");
        assertThat(result.unit()).isEqualTo("So Khoa hoc va Cong nghe / Chi cuc Tieu chuan / Phong Tong hop");
    }

    /** Tài khoản KHÔNG có tổ tiên khớp form "unit" (chỉ có agency) — vẫn phải hiển thị được cơ quan. */
    @Test
    void resolve_withOnlyAgencyLevel_shouldStillShowAgency() {
        var appUser = new AppUser("kc-2", "nhanvien02", "Tran Thi B", "b@x.com", null, null, true);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nhanvien02")).thenReturn(Optional.of(appUser));
        var profile = new UserOrganizationProfileDto();
        profile.setFullName("Tran Thi B");
        profile.setAgencyName("So Khoa hoc va Cong nghe");
        // unitName/departmentName mac dinh rong ("") - dung tinh huong that (khong co to tien "unit").
        when(getUserOrganizationProfileUseCase.buildProfileForUser(appUser)).thenReturn(profile);

        var result = service.resolve("nhanvien02");

        assertThat(result.unit()).isEqualTo("So Khoa hoc va Cong nghe");
    }

    @Test
    void resolve_whenAppUserNotFound_shouldFallbackToUsernameWithEmptyUnit() {
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("unknown")).thenReturn(Optional.empty());

        var result = service.resolve("unknown");

        assertThat(result.fullName()).isEqualTo("unknown");
        assertThat(result.unit()).isEqualTo("");
    }

    @Test
    void resolve_withBlankUsername_shouldNotQueryRepository() {
        var result = service.resolve("");

        assertThat(result.unit()).isEqualTo("");
    }
}
