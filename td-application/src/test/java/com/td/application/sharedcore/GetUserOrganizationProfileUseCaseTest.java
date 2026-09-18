package com.td.application.sharedcore;

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

// BL-118 (19/08/2026): qlvb/profile trước đây lấy fullName từ claim Keycloak và hardcode
// position/coquan/donvi/caplanhdao rỗng/hằng số cố định cho MỌI tài khoản. Test này khoá lại use
// case tra cứu dữ liệu tổ chức THẬT (AppUser + đi lên Organization.parentId theo "form"), cùng
// "Kiêm nhiệm" (AppUser.parentUserId) — đối chiếu BE_THANHHOA xác nhận đây LÀ 1 phần payload
// qlvb/profile thật (user_other_obj), không chỉ khái niệm quản trị riêng.
@ExtendWith(MockitoExtension.class)
class GetUserOrganizationProfileUseCaseTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private DynamicDocumentFieldsService dynamicDocumentFieldsService;

    private GetUserOrganizationProfileUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetUserOrganizationProfileUseCase(appUserRepository, organizationRepository, dynamicDocumentFieldsService);
    }

    @Test
    void execute_blankUsername_returnsEmptyDto() {
        var dto = useCase.execute("  ");
        assertThat(dto.getUsername()).isNull();
        assertThat(dto.getKiemNhiem()).isEmpty();
    }

    @Test
    void execute_userNotFound_returnsEmptyDto() {
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("ghost")).thenReturn(Optional.empty());

        var dto = useCase.execute("ghost");

        assertThat(dto.getUsername()).isNull();
        assertThat(dto.getFullName()).isNull();
    }

    @Test
    void execute_resolvesFullOrgHierarchy_byWalkingParentIdUpMatchingForm() {
        Organization agencyLevel = newOrg("Tỉnh A", "agency_level", null);
        Organization agency = newOrg("Văn phòng Bộ", "agency", agencyLevel.getId());
        Organization unit = newOrg("Phòng Kế hoạch - Tài chính", "unit", agency.getId());
        Organization department = newOrg("Tổ Hành chính", "department", unit.getId());

        AppUser user = fullAppUser("nhanvien03", "Nguyễn Văn A", department.getId(), "khong", "G13.01");
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nhanvien03")).thenReturn(Optional.of(user));
        when(appUserRepository.findByParentUserIdAndDeletedOnIsNull(user.getId())).thenReturn(List.of());
        when(dynamicDocumentFieldsService.parseDynamicFields(anyString()))
            .thenReturn(Map.of("position", "Chuyên viên"));

        when(organizationRepository.findByIdAndDeletedOnIsNull(department.getId())).thenReturn(Optional.of(department));
        when(organizationRepository.findByIdAndDeletedOnIsNull(unit.getId())).thenReturn(Optional.of(unit));
        when(organizationRepository.findByIdAndDeletedOnIsNull(agency.getId())).thenReturn(Optional.of(agency));
        when(organizationRepository.findByIdAndDeletedOnIsNull(agencyLevel.getId())).thenReturn(Optional.of(agencyLevel));

        var dto = useCase.execute("nhanvien03");

        assertThat(dto.getUsername()).isEqualTo("nhanvien03");
        assertThat(dto.getFullName()).isEqualTo("Nguyễn Văn A");
        assertThat(dto.getPosition()).isEqualTo("Chuyên viên");
        assertThat(dto.getCapLanhDao()).isEqualTo("khong");
        assertThat(dto.getIdentifier()).isEqualTo("G13.01");
        assertThat(dto.getDepartmentName()).isEqualTo("Tổ Hành chính");
        assertThat(dto.getUnitName()).isEqualTo("Phòng Kế hoạch - Tài chính");
        assertThat(dto.getAgencyName()).isEqualTo("Văn phòng Bộ");
        assertThat(dto.getAgencyLevelName()).isEqualTo("Tỉnh A");
        assertThat(dto.getAgencyId()).isEqualTo(agency.getId().toString());
        // BL-118 (tiếp): "identifiers" — LÁ→GỐC (org trực tiếp của user trước, root sau cùng), đối
        // chiếu BE_THANHHOA capnhat_nguoidung() + legacyAdminProfile.json thật.
        assertThat(dto.getIdentifiers()).isEqualTo(
            department.getIdentifier() + ";" + unit.getIdentifier() + ";"
                + agency.getIdentifier() + ";" + agencyLevel.getIdentifier() + ";");
        // "parentids" — CÙNG thứ tự/lượt đi lên với "identifiers", chỉ khác giá trị (UUID node thay
        // vì mã định danh mnemonic) — tương đương vai trò UNID Domino.
        assertThat(dto.getParentIds()).isEqualTo(
            department.getId() + ";" + unit.getId() + ";" + agency.getId() + ";" + agencyLevel.getId() + ";");
    }

    @Test
    void execute_userWithNoOrganization_returnsBlankOrgFields_notNull() {
        AppUser user = new AppUser("kc-nhanvien06", "nhanvien06", "Le Van C", null, null, null, true);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nhanvien06")).thenReturn(Optional.of(user));
        when(appUserRepository.findByParentUserIdAndDeletedOnIsNull(user.getId())).thenReturn(List.of());
        when(dynamicDocumentFieldsService.parseDynamicFields(anyString())).thenReturn(Map.of());

        var dto = useCase.execute("nhanvien06");

        assertThat(dto.getAgencyName()).isEmpty();
        assertThat(dto.getUnitName()).isEmpty();
        assertThat(dto.getPosition()).isEmpty();
        assertThat(dto.getCapLanhDao()).isEmpty();
        assertThat(dto.getIdentifiers()).isEmpty();
        assertThat(dto.getParentIds()).isEmpty();
    }

    // BL-118: "Kiêm nhiệm" — user_other_obj thật (đối chiếu BE_THANHHOA
    // getJackSonUserInformationAndPermissions) — mỗi mục có hồ sơ tổ chức riêng.
    @Test
    void execute_userWithKiemNhiem_resolvesEachSubProfileIndependently() {
        Organization agency = newOrg("Sở Nội vụ", "agency", null);
        AppUser primary = fullAppUser("nhanvien04", "Trần Thị B", agency.getId(), "lanhdaodv", "G05");
        AppUser kiemNhiem = fullAppUser("nhanvien04-kn1", "Trần Thị B (kiêm nhiệm)", null, "khong", "G05.01");

        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nhanvien04")).thenReturn(Optional.of(primary));
        when(appUserRepository.findByParentUserIdAndDeletedOnIsNull(primary.getId())).thenReturn(List.of(kiemNhiem));
        when(dynamicDocumentFieldsService.parseDynamicFields(anyString())).thenReturn(Map.of("position", "Trưởng phòng"));
        when(organizationRepository.findByIdAndDeletedOnIsNull(agency.getId())).thenReturn(Optional.of(agency));

        var dto = useCase.execute("nhanvien04");

        assertThat(dto.getFullName()).isEqualTo("Trần Thị B");
        assertThat(dto.getAgencyName()).isEqualTo("Sở Nội vụ");
        assertThat(dto.getKiemNhiem()).hasSize(1);
        var knDto = dto.getKiemNhiem().get(0);
        assertThat(knDto.getUsername()).isEqualTo("nhanvien04-kn1");
        assertThat(knDto.getFullName()).isEqualTo("Trần Thị B (kiêm nhiệm)");
        // Không có tổ chức riêng (organizationId null) -> rỗng, không kế thừa org của tài khoản chính.
        assertThat(knDto.getAgencyName()).isEmpty();
    }

    // Gap #1 (docs/analysis/18-checklist-port-kiem-nhiem-domino-sang-java.md, 08/09/2026): hồ sơ
    // kiêm nhiệm đang bị Khoá (isActive=false) phải bị loại khỏi kết quả — khớp hành vi Domino gốc
    // TDAuthentication.CreateKiemNhiemAuthenToken() (chặn cấp token khi status != "1").
    @Test
    void execute_userWithInactiveKiemNhiem_excludesLockedFromResult() {
        Organization agency = newOrg("Sở Nội vụ", "agency", null);
        AppUser primary = fullAppUser("nhanvien05", "Le Thi C", agency.getId(), "khong", "G06");
        AppUser activeKiemNhiem = fullAppUser("nhanvien05-kn1", "Le Thi C (kiêm nhiệm hoạt động)", null, "khong", "G06.01");
        AppUser lockedKiemNhiem = new AppUser("kc-nhanvien05-kn2", "nhanvien05-kn2", "Le Thi C (kiêm nhiệm khoá)", null,
            null, null, false, null, 0, null, null, null, null, null, "khong", "G06.02", false, primary.getId());

        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nhanvien05")).thenReturn(Optional.of(primary));
        when(appUserRepository.findByParentUserIdAndDeletedOnIsNull(primary.getId()))
            .thenReturn(List.of(activeKiemNhiem, lockedKiemNhiem));
        when(dynamicDocumentFieldsService.parseDynamicFields(anyString())).thenReturn(Map.of("position", "Chuyên viên"));
        when(organizationRepository.findByIdAndDeletedOnIsNull(agency.getId())).thenReturn(Optional.of(agency));

        var dto = useCase.execute("nhanvien05");

        assertThat(dto.getKiemNhiem()).hasSize(1);
        assertThat(dto.getKiemNhiem().get(0).getUsername()).isEqualTo("nhanvien05-kn1");
    }

    @Test
    void execute_organizationChainWithCycle_doesNotInfiniteLoop() {
        Organization nodeA = newOrg("Node A", "unit", null);
        Organization nodeB = newOrg("Node B", "agency", nodeA.getId());
        setParentId(nodeA, nodeB.getId()); // vòng lặp cố ý: A -> B -> A

        AppUser user = new AppUser("kc-nhanvien07", "nhanvien07", "Pham Van D", null, nodeA.getId(), null, true);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nhanvien07")).thenReturn(Optional.of(user));
        when(appUserRepository.findByParentUserIdAndDeletedOnIsNull(user.getId())).thenReturn(List.of());
        when(dynamicDocumentFieldsService.parseDynamicFields(anyString())).thenReturn(Map.of());
        when(organizationRepository.findByIdAndDeletedOnIsNull(nodeA.getId())).thenReturn(Optional.of(nodeA));
        when(organizationRepository.findByIdAndDeletedOnIsNull(nodeB.getId())).thenReturn(Optional.of(nodeB));

        var dto = useCase.execute("nhanvien07");

        assertThat(dto.getUnitName()).isEqualTo("Node A");
        assertThat(dto.getAgencyName()).isEqualTo("Node B");
        // Chặn vòng lặp: mỗi node chỉ xuất hiện đúng 1 lần trong identifiers (không lặp vô hạn).
        assertThat(dto.getIdentifiers()).isEqualTo(nodeA.getIdentifier() + ";" + nodeB.getIdentifier() + ";");
    }

    private Organization newOrg(String name, String nodeType, UUID parentId) {
        return new Organization("ID-" + name, name, null, parentId, nodeType, 0, name, 0,
            0, null, null, null, null, null, null, null, null, null);
    }

    // Test-only: ép parentId để dựng tình huống vòng lặp dữ liệu tổ chức lỗi (không nên xảy ra thật
    // nhưng phải chặn an toàn) — dùng reflection vì setter là PRIVATE (Organization.update() không
    // đủ linh hoạt cho tình huống test này).
    private void setParentId(Organization org, UUID parentId) {
        try {
            var field = Organization.class.getDeclaredField("parentId");
            field.setAccessible(true);
            field.set(org, parentId);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private AppUser fullAppUser(String username, String fullName, UUID organizationId, String capLanhDao, String identifier) {
        return new AppUser("kc-" + username, username, fullName, null, organizationId, null, true,
            null, 0, null, null, null, null, null, capLanhDao, identifier, false, null);
    }
}
