package com.td.application.sharedcore;

import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.sharedcore.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// Gap #5 (docs/analysis/18-checklist-port-kiem-nhiem-domino-sang-java.md, 08/09/2026): badge "có
// kiêm nhiệm" đúng toàn cục (AppUserDto.hasKiemNhiem) — trước đây field này không tồn tại, FE tự
// suy best-effort trong trang (ItemsList.jsx).
@ExtendWith(MockitoExtension.class)
class GetAppUserUseCaseTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private AppUserRoleGroupRepository appUserRoleGroupRepository;
    @Mock
    private DynamicDocumentFieldsService dynamicDocumentFieldsService;

    private GetAppUserUseCase newUseCase() {
        return new GetAppUserUseCase(appUserRepository, appUserRoleGroupRepository, dynamicDocumentFieldsService);
    }

    @Test
    void execute_nullId_returnsFailure() {
        var result = newUseCase().execute(null);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_userNotFound_returnsFailure() {
        var id = UUID.randomUUID();
        when(appUserRepository.findByIdAndDeletedOnIsNull(id)).thenReturn(Optional.empty());

        var result = newUseCase().execute(id);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_userWithKiemNhiem_setsHasKiemNhiemTrue() {
        var user = new AppUser("kc-parent", "parent01", "Parent", null, null, null, true);
        when(appUserRepository.findByIdAndDeletedOnIsNull(user.getId())).thenReturn(Optional.of(user));
        when(appUserRoleGroupRepository.findByAppUserIdAndDeletedOnIsNull(user.getId())).thenReturn(List.of());
        when(dynamicDocumentFieldsService.parseDynamicFields(anyString())).thenReturn(Map.of());
        when(appUserRepository.existsByParentUserIdAndDeletedOnIsNull(user.getId())).thenReturn(true);

        var result = newUseCase().execute(user.getId());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().isHasKiemNhiem()).isTrue();
    }

    @Test
    void execute_userWithoutKiemNhiem_setsHasKiemNhiemFalse() {
        var user = new AppUser("kc-solo", "solo01", "Solo", null, null, null, true);
        when(appUserRepository.findByIdAndDeletedOnIsNull(user.getId())).thenReturn(Optional.of(user));
        when(appUserRoleGroupRepository.findByAppUserIdAndDeletedOnIsNull(user.getId())).thenReturn(List.of());
        when(dynamicDocumentFieldsService.parseDynamicFields(anyString())).thenReturn(Map.of());
        when(appUserRepository.existsByParentUserIdAndDeletedOnIsNull(user.getId())).thenReturn(false);

        var result = newUseCase().execute(user.getId());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().isHasKiemNhiem()).isFalse();
    }
}
