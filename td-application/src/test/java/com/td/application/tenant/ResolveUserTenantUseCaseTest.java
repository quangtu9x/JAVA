package com.td.application.tenant;

import com.td.application.sharedcore.AppUserRepository;
import com.td.application.sharedcore.OrganizationRepository;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.sharedcore.Organization;
import com.td.domain.tenant.Tenant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// BL-118 (bugfix NGHIEM TRONG, 20/08/2026): xac dinh tenant NGHIEP VU/VAN BAN cua 1 username -
// AppUser/Organization la "danh muc dung chung" (khong nhan ban theo tenant, xem
// MultiPersistenceUnitJpaConfig), nen tra cuu o day KHONG con la bai toan quet/cache nua (khac cac
// dot sua truoc) - chi can di len "Co quan" gan nhat va doc linkedTenantId.
//
// LUU Y KY THUAT TEST: KHONG duoc goi orgWithNode(...) (tu stub mock ben trong) LONG NGAY trong
// argument cua 1 when(...).thenReturn(...) KHAC dang do dang - lam Mockito nham lan trang thai
// stubbing hien tai, nem UnfinishedStubbingException. Luon tao bien cuc bo TRUOC roi moi dung.
@ExtendWith(MockitoExtension.class)
class ResolveUserTenantUseCaseTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private TenantRepository tenantRepository;

    private ResolveUserTenantUseCase useCase() {
        return new ResolveUserTenantUseCase(appUserRepository, organizationRepository, tenantRepository);
    }

    private AppUser appUserWithOrganization(UUID organizationId) {
        return new AppUser("kc-sub", "skhcn", "Nguyen Van A", "a@example.com", organizationId, null, true);
    }

    private Organization orgWithNode(String nodeType, UUID parentId, String linkedTenantId) {
        Organization org = mock(Organization.class);
        lenient().when(org.getNodeType()).thenReturn(nodeType);
        lenient().when(org.getParentId()).thenReturn(parentId);
        lenient().when(org.getLinkedTenantId()).thenReturn(linkedTenantId);
        return org;
    }

    private Tenant activeTenant(String tenantId) {
        return new Tenant(tenantId, "Ten hien thi", "10.0.0.1", 5432, tenantId.toLowerCase(),
            "postgres", "ENC", "org.postgresql.Driver");
    }

    @Test
    void execute_shouldReturnEmpty_whenUsernameBlank() {
        assertThat(useCase().execute(" ")).isEmpty();
        assertThat(useCase().execute(null)).isEmpty();
    }

    @Test
    void execute_shouldReturnEmpty_whenAppUserNotFound() {
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("skhcn")).thenReturn(Optional.empty());

        assertThat(useCase().execute("skhcn")).isEmpty();
    }

    @Test
    void execute_shouldReturnEmpty_whenNoAgencyAncestorHasLinkedTenantId() {
        UUID agencyId = UUID.randomUUID();
        Organization agency = orgWithNode("agency", null, null);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("skhcn"))
            .thenReturn(Optional.of(appUserWithOrganization(agencyId)));
        when(organizationRepository.findByIdAndDeletedOnIsNull(agencyId)).thenReturn(Optional.of(agency));

        assertThat(useCase().execute("skhcn")).isEmpty();
    }

    // Ca "skhcn" that: to chuc TRUC TIEP cua user CHINH LA "Co quan", da lien ket H05_01 VA H05_01
    // dang active trong registry -> phai tra ve H05_01.
    @Test
    void execute_shouldReturnLinkedTenant_whenOwnOrganizationIsAgencyWithLinkedTenantId() {
        UUID agencyId = UUID.randomUUID();
        Organization agency = orgWithNode("agency", null, "H05_01");
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("skhcn"))
            .thenReturn(Optional.of(appUserWithOrganization(agencyId)));
        when(organizationRepository.findByIdAndDeletedOnIsNull(agencyId)).thenReturn(Optional.of(agency));
        when(tenantRepository.findByTenantIdAndDeletedOnIsNull("H05_01"))
            .thenReturn(Optional.of(activeTenant("H05_01")));

        assertThat(useCase().execute("skhcn")).contains("H05_01");
    }

    @Test
    void execute_shouldWalkUpToAgencyAncestor_whenOwnOrganizationIsDepartmentOrUnit() {
        UUID deptId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();
        Organization dept = orgWithNode("department", unitId, null);
        Organization unit = orgWithNode("unit", agencyId, null);
        Organization agency = orgWithNode("agency", null, "H05_01");
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nhanvien01"))
            .thenReturn(Optional.of(appUserWithOrganization(deptId)));
        when(organizationRepository.findByIdAndDeletedOnIsNull(deptId)).thenReturn(Optional.of(dept));
        when(organizationRepository.findByIdAndDeletedOnIsNull(unitId)).thenReturn(Optional.of(unit));
        when(organizationRepository.findByIdAndDeletedOnIsNull(agencyId)).thenReturn(Optional.of(agency));
        when(tenantRepository.findByTenantIdAndDeletedOnIsNull("H05_01"))
            .thenReturn(Optional.of(activeTenant("H05_01")));

        assertThat(useCase().execute("nhanvien01")).contains("H05_01");
    }

    @Test
    void execute_shouldReturnEmpty_whenLinkedTenantIsNoLongerActiveInRegistry() {
        UUID agencyId = UUID.randomUUID();
        Organization agency = orgWithNode("agency", null, "H05_01");
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("skhcn"))
            .thenReturn(Optional.of(appUserWithOrganization(agencyId)));
        when(organizationRepository.findByIdAndDeletedOnIsNull(agencyId)).thenReturn(Optional.of(agency));
        Tenant deactivated = activeTenant("H05_01");
        deactivated.deactivate();
        when(tenantRepository.findByTenantIdAndDeletedOnIsNull("H05_01")).thenReturn(Optional.of(deactivated));

        assertThat(useCase().execute("skhcn")).isEmpty();
    }

    @Test
    void execute_shouldReturnEmpty_whenLinkedTenantDoesNotExistInRegistry() {
        UUID agencyId = UUID.randomUUID();
        Organization agency = orgWithNode("agency", null, "TENANT_KHONG_TON_TAI");
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("skhcn"))
            .thenReturn(Optional.of(appUserWithOrganization(agencyId)));
        when(organizationRepository.findByIdAndDeletedOnIsNull(agencyId)).thenReturn(Optional.of(agency));
        when(tenantRepository.findByTenantIdAndDeletedOnIsNull("TENANT_KHONG_TON_TAI")).thenReturn(Optional.empty());

        assertThat(useCase().execute("skhcn")).isEmpty();
    }

    @Test
    void execute_shouldReturnEmpty_whenAppUserHasNoOrganization() {
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("skhcn"))
            .thenReturn(Optional.of(appUserWithOrganization(null)));

        assertThat(useCase().execute("skhcn")).isEmpty();
    }

    @Test
    void execute_shouldStopWalking_onCircularParentId_ratherThanLoopForever() {
        UUID orgId = UUID.randomUUID();
        Organization selfLoop = orgWithNode("department", orgId, null);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("skhcn"))
            .thenReturn(Optional.of(appUserWithOrganization(orgId)));
        when(organizationRepository.findByIdAndDeletedOnIsNull(orgId)).thenReturn(Optional.of(selfLoop));

        assertThat(useCase().execute("skhcn")).isEmpty();
    }
}
