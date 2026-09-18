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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * "Cấp ban hành" (Slice 1 dự thảo, 24/08/2026) — {@link GetCapBanHanhOptionsUseCase} tương thích
 * {@code qlvb/danhmuc/publish?get=capbanhanh}. Xem javadoc use case về quyết định đơn giản hoá đã
 * xác nhận với người dùng: KHÔNG port check {@code dbpath} — luôn thêm {@code agency_level} vào
 * đầu danh sách nếu tồn tại.
 */
@ExtendWith(MockitoExtension.class)
class GetCapBanHanhOptionsUseCaseTest {

    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private DynamicDocumentFieldsService dynamicDocumentFieldsService;

    private GetCapBanHanhOptionsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetCapBanHanhOptionsUseCase(
            currentUserProvider, appUserRepository, organizationRepository, dynamicDocumentFieldsService);
    }

    @Test
    void execute_withUnsupportedDatabasetype_shouldReturnFailure() {
        var result = useCase.execute("hosocv");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("hosocv");
    }

    // 28/08/2026 ("Cấp ban hành" cho vbdi, SubFormThongTinCoBan.jsx) - databasetype=vbdi giờ ĐÃ ho
    // tro, cung thuat toan y het duthao (xem javadoc lop).
    @Test
    void execute_withVbdiDatabasetype_shouldSucceed() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var result = useCase.execute("vbdi");

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void execute_withoutAuthenticatedUser_shouldReturnEmptyList() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var result = useCase.execute("duthao");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEmpty();
    }

    @Test
    void execute_userNotFound_shouldReturnEmptyList() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("ghost"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("ghost")).thenReturn(Optional.empty());

        var result = useCase.execute("duthao");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEmpty();
    }

    // Kich ban chinh: agency_level -> agency -> unit -> department, tra ve dung thu tu GOC->LA,
    // agency_level LUON co mat (khong con check dbpath).
    @Test
    void execute_resolvesFullAncestryRootToLeaf_alwaysIncludingAgencyLevel() {
        Organization agencyLevel = newOrg("H05", "Tỉnh A", "agency_level", null);
        Organization agency = newOrg("H05.12", "Sở Nội vụ", "agency", agencyLevel.getId());
        Organization unit = newOrg("H05.12.07", "Phòng Tổ chức", "unit", agency.getId());
        Organization department = newOrg("H05.12.07.01", "Tổ Hành chính", "department", unit.getId());

        AppUser user = new AppUser("kc-nv1", "nv1", "Nguyen Van A", null, department.getId(), null, true,
            null, 0, null, null, null, null, null, null, null, false, null);
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nv1"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nv1")).thenReturn(Optional.of(user));
        when(organizationRepository.findByIdAndDeletedOnIsNull(department.getId())).thenReturn(Optional.of(department));
        when(organizationRepository.findByIdAndDeletedOnIsNull(unit.getId())).thenReturn(Optional.of(unit));
        when(organizationRepository.findByIdAndDeletedOnIsNull(agency.getId())).thenReturn(Optional.of(agency));
        when(organizationRepository.findByIdAndDeletedOnIsNull(agencyLevel.getId())).thenReturn(Optional.of(agencyLevel));
        when(dynamicDocumentFieldsService.parseDynamicFields(anyString())).thenReturn(Map.of());

        var result = useCase.execute("duthao");

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> data = result.getData();
        assertThat(data).hasSize(4);
        assertThat(data.get(0)).containsEntry("name", "Tỉnh A").containsEntry("form", "agency_level");
        assertThat(data.get(1)).containsEntry("name", "Sở Nội vụ").containsEntry("form", "agency");
        assertThat(data.get(2)).containsEntry("name", "Phòng Tổ chức").containsEntry("form", "unit");
        assertThat(data.get(3)).containsEntry("name", "Tổ Hành chính").containsEntry("form", "department");
    }

    // Domino goc: bo qua to tien khong co identifier (rong).
    @Test
    void execute_ancestorWithBlankIdentifier_shouldBeSkipped() {
        Organization agencyLevel = newOrg("H05", "Tỉnh A", "agency_level", null);
        Organization agencyNoIdentifier = newOrg("", "Cơ quan không rõ", "agency", agencyLevel.getId());

        AppUser user = new AppUser("kc-nv2", "nv2", "Nguyen Van B", null, agencyNoIdentifier.getId(), null, true,
            null, 0, null, null, null, null, null, null, null, false, null);
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nv2"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nv2")).thenReturn(Optional.of(user));
        when(organizationRepository.findByIdAndDeletedOnIsNull(agencyNoIdentifier.getId())).thenReturn(Optional.of(agencyNoIdentifier));
        when(organizationRepository.findByIdAndDeletedOnIsNull(agencyLevel.getId())).thenReturn(Optional.of(agencyLevel));
        when(dynamicDocumentFieldsService.parseDynamicFields(anyString())).thenReturn(Map.of());

        var result = useCase.execute("duthao");

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0)).containsEntry("name", "Tỉnh A");
    }

    // identifier_other/mailin doc tu Organization.dynamicFieldsJson.
    @Test
    void execute_shouldReadIdentifierOtherAndMailinFromDynamicFields() {
        Organization agencyLevel = newOrg("H05", "Tỉnh A", "agency_level", null);
        AppUser user = new AppUser("kc-nv3", "nv3", "Nguyen Van C", null, agencyLevel.getId(), null, true,
            null, 0, null, null, null, null, null, null, null, false, null);
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nv3"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nv3")).thenReturn(Optional.of(user));
        when(organizationRepository.findByIdAndDeletedOnIsNull(agencyLevel.getId())).thenReturn(Optional.of(agencyLevel));
        when(dynamicDocumentFieldsService.parseDynamicFields(anyString()))
            .thenReturn(Map.of("identifier_other", "H05-KHAC", "mailin", "IO_01"));

        var result = useCase.execute(null);

        assertThat(result.getData().get(0))
            .containsEntry("identifier_other", "H05-KHAC")
            .containsEntry("mailin", "IO_01");
    }

    private Organization newOrg(String identifier, String name, String nodeType, UUID parentId) {
        return new Organization(identifier, name, null, parentId, nodeType, 0, name, 0,
            0, null, null, null, null, null, null, null, null, null);
    }
}
