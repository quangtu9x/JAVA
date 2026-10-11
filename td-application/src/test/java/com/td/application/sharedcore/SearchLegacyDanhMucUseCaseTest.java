package com.td.application.sharedcore;

import com.td.application.categories.CategoryDto;
import com.td.application.categories.SearchCategoriesRequest;
import com.td.application.categories.SearchCategoriesUseCase;
import com.td.application.common.models.PaginationResponse;
import com.td.application.documentregister.SearchDocumentRegistersUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchLegacyDanhMucUseCaseTest {

    @Mock private SearchCategoriesUseCase searchCategoriesUseCase;
    @Mock private SearchDocumentRegistersUseCase searchDocumentRegistersUseCase;
    @Mock private GetLegacyOrgLookupForDungChungUseCase orgLookup;
    @Mock private OrganizationAgencyResolver organizationAgencyResolver;
    @Mock private GetUserRoleGroupConfigUseCase getUserRoleGroupConfigUseCase;
    @InjectMocks private SearchLegacyDanhMucUseCase useCase;

    @Test
    void legacyCatalogAlias_shouldQueryJavaCategoryForm() {
        CategoryDto dto = new CategoryDto();
        dto.setId(UUID.randomUUID());
        dto.setName("Khẩn");
        when(searchCategoriesUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.of(dto), 1, 200, 1, 1, true, true));

        Map<String, Object> result = useCase.execute("Select Form = 'dokhan'", null);

        ArgumentCaptor<SearchCategoriesRequest> captor = ArgumentCaptor.forClass(SearchCategoriesRequest.class);
        verify(searchCategoriesUseCase).execute(captor.capture());
        assertThat(captor.getValue().getForms()).containsExactly("priority");
        assertThat(result.get("supported")).isEqualTo(true);
        assertThat((List<?>) result.get("items")).hasSize(1);
    }

    @Test
    void signerQuery_shouldFilterOrgMembersByPermission() {
        when(orgLookup.getOrganizationMembersDetailed("ORG1", true)).thenReturn(List.of(
            Map.of("username", "a", "name", "A"), Map.of("username", "b", "name", "B")));
        UserRoleGroupConfigDto withRole = new UserRoleGroupConfigDto();
        withRole.setPermissionCodes(List.of("QKyVB"));
        UserRoleGroupConfigDto without = new UserRoleGroupConfigDto();
        when(getUserRoleGroupConfigUseCase.execute("a")).thenReturn(withRole);
        when(getUserRoleGroupConfigUseCase.execute("b")).thenReturn(without);

        String query = "Select Form = 'user'  & (agency_id='ORG1' | unit_id='ORG1' | department_id='ORG1' | nodeid='ORG1')"
            + " & @Contains(@Trim(@LowerCase(roles)); @Trim(@LowerCase(\"QKyVB\")))";
        List<?> items = (List<?>) useCase.execute(query, null).get("items");

        assertThat(items).hasSize(1);
        assertThat(((Map<?, ?>) items.get(0)).get("username")).isEqualTo("a");
    }

    @Test
    void userWithoutOrg_shouldUseCurrentAgencyAndKeywordFilter() {
        UUID agencyId = UUID.randomUUID();
        when(organizationAgencyResolver.resolveCurrentUserAgencyId()).thenReturn(Optional.of(agencyId));
        when(orgLookup.getOrganizationMembersDetailed(agencyId.toString(), true)).thenReturn(List.of(
            Map.of("username", "a", "name", "Nguyễn An"), Map.of("username", "b", "name", "Trần Bình")));

        List<?> items = (List<?>) useCase.execute("Select Form = 'user'", "bình").get("items");

        assertThat(items).hasSize(1);
    }

    @Test
    void unitChildren_shouldUseDirectChildren() {
        when(orgLookup.getOrganizationsByForm("AG1", "unit", true)).thenReturn(List.of(Map.of("name", "Phòng 1")));

        String query = "Select Form = 'unit' &parentid=\"AG1\" & !(@Contains(@Trim(@LowerCase(Form));@Trim(@LowerCase(\"_delete\"))))";
        assertThat((List<?>) useCase.execute(query, null).get("items")).hasSize(1);
    }

    @Test
    void unknownPattern_shouldBeUnsupported() {
        assertThat(useCase.execute("Select Form = 'api_group'", null).get("supported")).isEqualTo(false);
        assertThat(useCase.execute("Select Form = 'document_register'", null).get("supported")).isEqualTo(false);
    }
}
