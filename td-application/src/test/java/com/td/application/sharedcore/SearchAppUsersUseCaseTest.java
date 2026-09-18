package com.td.application.sharedcore;

import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.sharedcore.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Gap #5 (docs/analysis/18-checklist-port-kiem-nhiem-domino-sang-java.md, 08/09/2026): badge "có
// kiêm nhiệm" đúng toàn cục — nạp 1 lần cho CẢ TRANG (tránh N+1), thay best-effort trong-trang cũ.
@ExtendWith(MockitoExtension.class)
class SearchAppUsersUseCaseTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private AppUserRoleGroupRepository appUserRoleGroupRepository;
    @Mock
    private DynamicDocumentFieldsService dynamicDocumentFieldsService;

    private SearchAppUsersUseCase newUseCase() {
        return new SearchAppUsersUseCase(appUserRepository, appUserRoleGroupRepository, dynamicDocumentFieldsService);
    }

    @Test
    void execute_pageWithSomeParents_marksOnlyParentsAsHasKiemNhiem() {
        var parent = new AppUser("kc-cha", "cha01", "Cha", null, null, null, true);
        var soloUser = new AppUser("kc-doclap", "doclap01", "Độc lập", null, null, null, true);
        Page<AppUser> page = new PageImpl<>(List.of(parent, soloUser));

        when(appUserRepository.search(any(), any(Pageable.class))).thenReturn(page);
        when(appUserRoleGroupRepository.findByAppUserIdInAndDeletedOnIsNull(any())).thenReturn(List.of());
        when(dynamicDocumentFieldsService.parseDynamicFields(anyString())).thenReturn(Map.of());
        // Đúng 1 con trỏ parentUserId về "parent" — soloUser không có con nào.
        var child = new AppUser("kc-con", "con01", "Con", null, null, null, true,
            null, 0, null, null, null, null, null, null, null, false, parent.getId());
        when(appUserRepository.findByParentUserIdInAndDeletedOnIsNull(List.of(parent.getId(), soloUser.getId())))
            .thenReturn(List.of(child));

        var result = newUseCase().execute(new SearchAppUsersRequest());

        assertThat(result.getItems()).hasSize(2);
        var parentDto = result.getItems().stream().filter(dto -> dto.getId().equals(parent.getId())).findFirst().orElseThrow();
        var soloDto = result.getItems().stream().filter(dto -> dto.getId().equals(soloUser.getId())).findFirst().orElseThrow();
        assertThat(parentDto.isHasKiemNhiem()).isTrue();
        assertThat(soloDto.isHasKiemNhiem()).isFalse();
    }

    @Test
    void execute_emptyPage_doesNotQueryKiemNhiem() {
        Page<AppUser> page = new PageImpl<>(List.of());
        when(appUserRepository.search(any(), any(Pageable.class))).thenReturn(page);
        when(appUserRoleGroupRepository.findByAppUserIdInAndDeletedOnIsNull(any())).thenReturn(List.of());

        var result = newUseCase().execute(new SearchAppUsersRequest());

        assertThat(result.getItems()).isEmpty();
        verify(appUserRepository, never()).findByParentUserIdInAndDeletedOnIsNull(any());
    }
}
