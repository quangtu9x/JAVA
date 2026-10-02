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

    // 27/09/2026 - vwcocautochuc_cay: goc (depth 0) + moi hau due, pre-order, anh em theo sortOrder roi ten.
    @Test
    void getOrganizationSubtree_shouldReturnRootAndDescendantsInTreeOrderWithDepth() {
        var target = newUseCase();
        Organization agency = newOrganization("H05.01", "So KHCN", "agency", 0);
        Organization center = newOrganization("H05.01.05", "Trung tam CNTT", "unit", 3);
        Organization office = newOrganization("H05.01.02", "Van phong so", "unit", 1);
        Organization dept = newOrganization("H05.01.05.01", "Phong Tong hop", "department", 0);
        when(organizationRepository.findByIdAndDeletedOnIsNull(agency.getId())).thenReturn(java.util.Optional.of(agency));
        when(organizationRepository.findByParentIdAndDeletedOnIsNull(agency.getId())).thenReturn(List.of(center, office));
        when(organizationRepository.findByParentIdAndDeletedOnIsNull(center.getId())).thenReturn(List.of(dept));
        when(organizationRepository.findByParentIdAndDeletedOnIsNull(office.getId())).thenReturn(List.of());
        when(organizationRepository.findByParentIdAndDeletedOnIsNull(dept.getId())).thenReturn(List.of());

        List<Map<String, Object>> rows = target.getOrganizationSubtree(agency.getId().toString());

        assertThat(rows).extracting(row -> row.get("identifier"))
            .containsExactly("H05.01", "H05.01.02", "H05.01.05", "H05.01.05.01");
        assertThat(rows).extracting(row -> row.get("depth")).containsExactly(0, 1, 1, 2);
    }

    @Test
    void getOrganizationSubtree_withUnknownOrInvalidRoot_shouldReturnEmpty() {
        var target = newUseCase();
        UUID missing = UUID.randomUUID();
        when(organizationRepository.findByIdAndDeletedOnIsNull(missing)).thenReturn(java.util.Optional.empty());

        assertThat(target.getOrganizationSubtree(missing.toString())).isEmpty();
        assertThat(target.getOrganizationSubtree("not-a-uuid")).isEmpty();
    }

    private Organization newOrganization(String identifier, String name, String form) {
        return newOrganization(identifier, name, form, 0);
    }

    private Organization newOrganization(String identifier, String name, String form, int sortOrder) {
        return new Organization(identifier, name, null, null, form, 3, name, sortOrder,
            0, null, null, null, null, null, null, null, null, null);
    }
}
