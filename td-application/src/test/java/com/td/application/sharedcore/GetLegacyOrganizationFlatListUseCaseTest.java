package com.td.application.sharedcore;

import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.sharedcore.Organization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Port danhmuc (09/09/2026) — {@code qlvb/danhmuc/DanhSachCoCauToChuc}, xem javadoc
 * {@link GetLegacyOrganizationFlatListUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class GetLegacyOrganizationFlatListUseCaseTest {

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private AppUserRepository appUserRepository;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();

    private GetLegacyOrganizationFlatListUseCase useCase;

    @Test
    void execute_shouldReturnFlatRowWithIdentifierChainLeafToRoot() {
        useCase = new GetLegacyOrganizationFlatListUseCase(
            organizationRepository, dynamicDocumentFieldsService, appUserRepository);

        Organization agencyLevel = newOrganization("UBND", "UBND tinh", null);
        Organization agency = newOrganization("SNV", "So Noi Vu", agencyLevel.getId());
        Organization unit = newOrganization("PTCHC", "Phong To Chuc", agency.getId());

        when(organizationRepository.search(any(), any()))
            .thenReturn(pageOf(List.of(agencyLevel, agency, unit)));

        List<Map<String, Object>> rows = useCase.execute();

        assertThat(rows).hasSize(3);
        Map<String, Object> unitRow = rows.stream()
            .filter(row -> "PTCHC".equals(row.get("identifier")))
            .findFirst().orElseThrow();
        assertThat(unitRow.get("name")).isEqualTo("Phong To Chuc");
        assertThat(unitRow.get("identifiers")).isEqualTo("PTCHC;SNV;UBND;");
        assertThat(unitRow.get("user_manage")).isEqualTo("");
    }

    @Test
    void execute_withDynamicFields_shouldIncludeIdentifierOtherAndClerical() {
        useCase = new GetLegacyOrganizationFlatListUseCase(
            organizationRepository, dynamicDocumentFieldsService, appUserRepository);

        Organization unit = newOrganization("PTCHC", "Phong To Chuc", null);
        unit.replaceDynamicFields("{\"identifier_other\":\"PTCHC_OLD\",\"clerical\":\"1\"}");
        when(organizationRepository.search(any(), any())).thenReturn(pageOf(List.of(unit)));

        Map<String, Object> row = useCase.execute().get(0);

        assertThat(row.get("identifier_other")).isEqualTo("PTCHC_OLD");
        assertThat(row.get("clerical")).isEqualTo("1");
    }

    @Test
    void execute_withoutDynamicFields_shouldDefaultToEmptyString() {
        useCase = new GetLegacyOrganizationFlatListUseCase(
            organizationRepository, dynamicDocumentFieldsService, appUserRepository);

        Organization unit = newOrganization("PTCHC", "Phong To Chuc", null);
        when(organizationRepository.search(any(), any())).thenReturn(pageOf(List.of(unit)));

        Map<String, Object> row = useCase.execute().get(0);

        assertThat(row.get("identifier_other")).isEqualTo("");
        assertThat(row.get("clerical")).isEqualTo("");
    }

    @Test
    void execute_withReceiverConfigured_shouldResolveUsernameFromReceiverId() {
        useCase = new GetLegacyOrganizationFlatListUseCase(
            organizationRepository, dynamicDocumentFieldsService, appUserRepository);

        Organization unit = newOrganization("PTCHC", "Phong To Chuc", null);
        UUID receiverAppUserId = UUID.randomUUID();
        unit.updateLegacyData(null, receiverAppUserId.toString(), "Nguyen Van A", "Truong phong",
            null, null, null, null, null, null);
        when(organizationRepository.search(any(), any())).thenReturn(pageOf(List.of(unit)));
        AppUser receiverUser = new AppUser("sub", "nguyenvana", "Nguyen Van A", null, null, null, true);
        when(appUserRepository.findByIdAndDeletedOnIsNull(receiverAppUserId)).thenReturn(java.util.Optional.of(receiverUser));

        Map<String, Object> row = useCase.execute().get(0);

        assertThat(row.get("user_receiver")).isEqualTo("nguyenvana");
    }

    private Organization newOrganization(String identifier, String name, UUID parentId) {
        return new Organization(identifier, name, null, parentId, "agency_level", 1, name, 0,
            0, null, null, null, null, null, null, null, null, null);
    }

    private Page<Organization> pageOf(List<Organization> organizations) {
        return new PageImpl<>(organizations);
    }
}
