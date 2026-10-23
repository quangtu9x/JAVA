package com.td.application.sharedcore;

import com.td.domain.sharedcore.Organization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationTenantLookupTest {

    @Mock
    private OrganizationRepository organizationRepository;

    private Organization org(String identifier, UUID parentId, String nodeType, String linkedTenantId) {
        Organization organization = new Organization(identifier, identifier, null, parentId, nodeType, 0, identifier, 0,
            0, null, null, null, null, null, null, null, null, null);
        organization.updateLinkedTenant(linkedTenantId);
        lenient().when(organizationRepository.findByIdAndDeletedOnIsNull(organization.getId())).thenReturn(Optional.of(organization));
        lenient().when(organizationRepository.findByIdentifierAndDeletedOnIsNull(identifier)).thenReturn(Optional.of(organization));
        return organization;
    }

    // 12/10/2026 du lieu that: Trung tam CNTT-TT (H05.01.05, unit) khong khai bao -> tenant cua So KHCN (H05_01),
    // truoc day roi ve tenant ben gui (snnmt).
    @Test
    void unitWithoutLinkedTenant_shouldUseNearestAncestor() {
        Organization province = org("H05", null, "agency_level", null);
        Organization agency = org("H05.01", province.getId(), "agency", "H05_01");
        org("H05.01.05", agency.getId(), "unit", null);

        assertThat(OrganizationTenantLookup.resolveLinkedTenantId(organizationRepository, "H05.01.05")).contains("H05_01");
        assertThat(OrganizationTenantLookup.resolveLinkedTenantId(organizationRepository, "H05.01")).contains("H05_01");
    }

    @Test
    void noLinkedTenantInChain_orUnknownIdentifier_shouldBeEmpty() {
        Organization province = org("H05", null, "agency_level", null);
        org("H05.09", province.getId(), "agency", null);
        when(organizationRepository.findByIdentifierAndDeletedOnIsNull("KHONG_CO")).thenReturn(Optional.empty());

        assertThat(OrganizationTenantLookup.resolveLinkedTenantId(organizationRepository, "H05.09")).isEmpty();
        assertThat(OrganizationTenantLookup.resolveLinkedTenantId(organizationRepository, "KHONG_CO")).isEmpty();
        assertThat(OrganizationTenantLookup.resolveLinkedTenantId(organizationRepository, " ")).isEmpty();
    }
}
