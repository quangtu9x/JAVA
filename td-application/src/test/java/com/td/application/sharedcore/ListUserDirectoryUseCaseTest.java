package com.td.application.sharedcore;

import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.sharedcore.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * BL-118 (tiếp, 19/08/2026): tương đương qlvb/danhmuc/DanhSachNguoiDung — trả toàn bộ người dùng
 * kèm tên hiển thị đơn vị, thay cho lời gọi Domino cũ luôn trả rỗng (token Java thiếu claim tổ chức
 * mà TDTokenObject/TDDatabaseConnection phía Domino cần).
 */
@ExtendWith(MockitoExtension.class)
class ListUserDirectoryUseCaseTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private DynamicDocumentFieldsService dynamicDocumentFieldsService;

    private ListUserDirectoryUseCase useCase;

    @BeforeEach
    void setUp() {
        lenient().when(dynamicDocumentFieldsService.parseDynamicFields(anyString())).thenReturn(Map.of());
        var realOrgUseCase = new GetUserOrganizationProfileUseCase(
            appUserRepository, organizationRepository, dynamicDocumentFieldsService);
        useCase = new ListUserDirectoryUseCase(appUserRepository, realOrgUseCase);
    }

    @Test
    void execute_noUsers_returnsEmptyList() {
        when(appUserRepository.findAllByDeletedOnIsNullOrderByFullNameAsc()).thenReturn(List.of());

        var result = useCase.execute();

        assertThat(result).isEmpty();
    }

    @Test
    void execute_returnsUsernameAndFullNameForEachUser() {
        AppUser user1 = new AppUser("kc-1", "nhanvien01", "Nguyen Van A", null, null, null, true);
        AppUser user2 = new AppUser("kc-2", "nhanvien02", "Tran Thi B", null, null, null, true);
        when(appUserRepository.findAllByDeletedOnIsNullOrderByFullNameAsc()).thenReturn(List.of(user1, user2));

        var result = useCase.execute();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUsername()).isEqualTo("nhanvien01");
        assertThat(result.get(0).getName()).isEqualTo("Nguyen Van A");
        assertThat(result.get(1).getUsername()).isEqualTo("nhanvien02");
        // Không có tổ chức -> field đơn vị rỗng, không phải null (an toàn cho FE).
        assertThat(result.get(0).getDepartment()).isEmpty();
        assertThat(result.get(0).getAgency()).isEmpty();
    }
}
