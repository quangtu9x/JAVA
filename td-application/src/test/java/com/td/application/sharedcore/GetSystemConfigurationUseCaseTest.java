package com.td.application.sharedcore;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.sharedcore.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 16/09/2026 (yêu cầu người dùng: "modal Chuyển xử lý của văn bản đến chưa nhận cấu hình -> Chuyển
 * chủ trì có 2 lựa chọn là chọn một và chọn nhiều") — {@link GetSystemConfigurationUseCase}, port
 * {@code GetCauHinh.java} thật. Cùng phong cách test với {@link GetCapBanHanhOptionsUseCaseTest}
 * (dùng chung thuật toán {@code walkAncestry}).
 */
@ExtendWith(MockitoExtension.class)
class GetSystemConfigurationUseCaseTest {

    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private DynamicDocumentFieldsService dynamicDocumentFieldsService;

    private GetSystemConfigurationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetSystemConfigurationUseCase(
            currentUserProvider, appUserRepository, organizationRepository, dynamicDocumentFieldsService);
    }

    @Test
    void execute_withoutAuthenticatedUser_shouldReturnEmptyMap() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        assertThat(useCase.execute()).isEmpty();
    }

    @Test
    void execute_userNotFound_shouldReturnEmptyMap() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("ghost"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("ghost")).thenReturn(Optional.empty());

        assertThat(useCase.execute()).isEmpty();
    }

    // Domino: uu tien "coquanid" (to tien cap agency) - KHONG phai to chuc truc tiep cua user
    // (unit) hay to tien xa hon (agency_level).
    @Test
    void execute_withAgencyAncestor_shouldReadCfgFieldsFromAgencyNode_notUnitOrAgencyLevel() {
        Organization agencyLevel = newOrg("H05", "Tỉnh A", "agency_level", null);
        Organization agency = newOrg("H05.12", "Sở Nội vụ", "agency", agencyLevel.getId());
        Organization unit = newOrg("H05.12.07", "Phòng Tổ chức", "unit", agency.getId());

        AppUser user = new AppUser("kc-nv1", "nv1", "Nguyen Van A", null, unit.getId(), null, true,
            null, 0, null, null, null, null, null, null, null, false, null);
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nv1"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nv1")).thenReturn(Optional.of(user));
        when(organizationRepository.findByIdAndDeletedOnIsNull(unit.getId())).thenReturn(Optional.of(unit));
        when(organizationRepository.findByIdAndDeletedOnIsNull(agency.getId())).thenReturn(Optional.of(agency));
        when(organizationRepository.findByIdAndDeletedOnIsNull(agencyLevel.getId())).thenReturn(Optional.of(agencyLevel));
        when(dynamicDocumentFieldsService.parseDynamicFields(agency.getDynamicFieldsJson()))
            .thenReturn(Map.of("cfg_chuyenchutri", "1", "mailin", "IO_01"));

        Map<String, Object> result = useCase.execute();

        assertThat(result).containsEntry("cfg_chuyenchutri", "1");
        // "mailin" khong bat dau bang "cfg_" - phai bi loc bo.
        assertThat(result).doesNotContainKey("mailin");
    }

    // Khong co to tien nao la "agency" (Domino: coquanid rong) - fallback ve chinh to chuc cua user
    // (nodeid).
    @Test
    void execute_withoutAgencyAncestor_shouldFallbackToUsersOwnOrganization() {
        Organization unit = newOrg("H05.12.07", "Phòng Tổ chức", "unit", null);
        AppUser user = new AppUser("kc-nv2", "nv2", "Nguyen Van B", null, unit.getId(), null, true,
            null, 0, null, null, null, null, null, null, null, false, null);
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nv2"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nv2")).thenReturn(Optional.of(user));
        when(organizationRepository.findByIdAndDeletedOnIsNull(unit.getId())).thenReturn(Optional.of(unit));
        when(dynamicDocumentFieldsService.parseDynamicFields(unit.getDynamicFieldsJson()))
            .thenReturn(Map.of("cfg_chuyenchutri", "0"));

        Map<String, Object> result = useCase.execute();

        assertThat(result).containsEntry("cfg_chuyenchutri", "0");
    }

    @Test
    void execute_organizationNotFound_shouldReturnEmptyMap() {
        UUID orphanId = UUID.randomUUID();
        AppUser user = new AppUser("kc-nv3", "nv3", "Nguyen Van C", null, orphanId, null, true,
            null, 0, null, null, null, null, null, null, null, false, null);
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nv3"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nv3")).thenReturn(Optional.of(user));
        when(organizationRepository.findByIdAndDeletedOnIsNull(orphanId)).thenReturn(Optional.empty());

        assertThat(useCase.execute()).isEmpty();
    }

    // Key tra ve phai lowercase (dung Domino "itemName.toLowerCase().trim()").
    @Test
    void execute_shouldLowercaseFieldKeys() {
        Organization agencyLevel = newOrg("H05", "Tỉnh A", "agency_level", null);
        AppUser user = new AppUser("kc-nv4", "nv4", "Nguyen Van D", null, agencyLevel.getId(), null, true,
            null, 0, null, null, null, null, null, null, null, false, null);
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nv4"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nv4")).thenReturn(Optional.of(user));
        when(organizationRepository.findByIdAndDeletedOnIsNull(agencyLevel.getId())).thenReturn(Optional.of(agencyLevel));
        when(dynamicDocumentFieldsService.parseDynamicFields(anyString()))
            .thenReturn(Map.of("CFG_Chuyenchutri", "1"));

        Map<String, Object> result = useCase.execute();

        assertThat(result).containsEntry("cfg_chuyenchutri", "1");
    }

    private Organization newOrg(String identifier, String name, String nodeType, UUID parentId) {
        return new Organization(identifier, name, null, parentId, nodeType, 0, name, 0,
            0, null, null, null, null, null, null, null, null, null);
    }
}
