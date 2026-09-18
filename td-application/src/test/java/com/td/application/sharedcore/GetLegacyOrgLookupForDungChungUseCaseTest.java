package com.td.application.sharedcore;

import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.sharedcore.Organization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Port danhmuc (09/09/2026) — 3 nhánh viewname của {@code LayDanhSachDungChung} mà Java trước đây
 * chưa hỗ trợ, xem javadoc {@link GetLegacyOrgLookupForDungChungUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class GetLegacyOrgLookupForDungChungUseCaseTest {

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private AppUserRepository appUserRepository;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();

    private GetLegacyOrgLookupForDungChungUseCase newUseCase() {
        return new GetLegacyOrgLookupForDungChungUseCase(
            organizationRepository, appUserRepository, dynamicDocumentFieldsService);
    }

    @Test
    void getOrganizationChildren_shouldReturnDirectChildrenOnly() {
        var target = newUseCase();
        UUID parentId = UUID.randomUUID();
        Organization child = newOrganization("SNV", "So Noi Vu", "unit");
        when(organizationRepository.findByParentIdAndDeletedOnIsNull(parentId)).thenReturn(List.of(child));

        List<Map<String, Object>> rows = target.getOrganizationChildren(parentId.toString());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("identifier", "SNV");
        assertThat(rows.get(0)).containsEntry("name", "So Noi Vu");
        assertThat(rows.get(0)).containsEntry("form", "unit");
    }

    @Test
    void getOrganizationChildren_withUnparsableId_shouldReturnEmpty() {
        var target = newUseCase();

        assertThat(target.getOrganizationChildren("not-a-uuid")).isEmpty();
        assertThat(target.getOrganizationChildren(null)).isEmpty();
    }

    @Test
    void getOrganizationMembers_withRecursiveFalse_shouldQueryOnlyGivenOrganization() {
        var target = newUseCase();
        UUID orgId = UUID.randomUUID();
        AppUser user = new AppUser("sub", "nva", "Nguyen Van A", null, orgId, null, true);
        when(appUserRepository.findByOrganizationIdInAndDeletedOnIsNull(List.of(orgId)))
            .thenReturn(List.of(user));

        List<Map<String, Object>> rows = target.getOrganizationMembers(orgId.toString(), false);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("username", "nva");
        assertThat(rows.get(0)).containsEntry("name", "Nguyen Van A");
    }

    @Test
    void getOrganizationMembers_withRecursiveTrue_shouldIncludeDescendantOrganizations() {
        var target = newUseCase();
        UUID rootId = UUID.randomUUID();
        Organization child = newOrganization("PQL", "Phong Quan ly", "department");
        when(organizationRepository.findByParentIdAndDeletedOnIsNull(rootId)).thenReturn(List.of(child));
        // Gian lap child.getId() qua stub rieng: dung Organization that thay vi mock de tranh
        // stub getId() phuc tap - override bang cach doc lai id that cua child.
        when(organizationRepository.findByParentIdAndDeletedOnIsNull(child.getId())).thenReturn(List.of());

        AppUser userInChild = new AppUser("sub2", "nvb", "Nguyen Van B", null, child.getId(), null, true);
        when(appUserRepository.findByOrganizationIdInAndDeletedOnIsNull(any()))
            .thenReturn(List.of(userInChild));

        List<Map<String, Object>> rows = target.getOrganizationMembers(rootId.toString(), true);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("username", "nvb");
    }

    private Organization newOrganization(String identifier, String name, String form) {
        return new Organization(identifier, name, null, null, form, 3, name, 0,
            0, null, null, null, null, null, null, null, null, null);
    }
}
