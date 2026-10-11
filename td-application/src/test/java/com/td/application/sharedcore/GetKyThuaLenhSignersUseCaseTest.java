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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;

/**
 * 01/10/2026: port {@code publish.java#ky_thua_lenh} — cây mô phỏng đúng dữ liệu thật skhcn:
 * Tỉnh(1) → Khối(2) → Sở KHCN(3) → {Văn phòng sở(4), Phòng Công nghệ(4)}.
 */
@ExtendWith(MockitoExtension.class)
class GetKyThuaLenhSignersUseCaseTest {

    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private OrganizationRepository organizationRepository;

    private GetKyThuaLenhSignersUseCase useCase;

    private Organization tinh;
    private Organization khoi;
    private Organization so;
    private Organization vanPhong;
    private Organization phongCongNghe;
    private final List<AppUser> users = new ArrayList<>();
    private final Map<UUID, List<Organization>> children = new HashMap<>();

    @BeforeEach
    void setUp() {
        useCase = new GetKyThuaLenhSignersUseCase(currentUserProvider, appUserRepository, organizationRepository,
            new DynamicDocumentFieldsService(), UserDelegationTestSupport.emptyResolver());
        tinh = org("H05", "Tỉnh", "agency_level", null);
        khoi = org("H05.K", "Khối sở", "agency", tinh.getId());
        so = org("H05.01", "Sở KHCN", "agency", khoi.getId());
        vanPhong = org("H05.01.VP", "Văn phòng sở", "unit", so.getId());
        phongCongNghe = org("H05.01.CN", "Phòng Công nghệ", "unit", so.getId());

        lenient().when(organizationRepository.findByParentIdAndDeletedOnIsNull(any()))
            .thenAnswer(inv -> children.getOrDefault(inv.getArgument(0), List.of()));
        lenient().when(appUserRepository.findByOrganizationIdInAndDeletedOnIsNull(anyList()))
            .thenAnswer(inv -> {
                List<UUID> ids = inv.getArgument(0);
                return users.stream().filter(u -> ids.contains(u.getOrganizationId())).toList();
            });
    }

    private Organization org(String identifier, String name, String nodeType, UUID parentId) {
        Organization organization = new Organization(identifier, name, null, parentId, nodeType, 0, name, 0,
            null, null, null, null, null, null, null, null, null, null);
        lenient().when(organizationRepository.findByIdAndDeletedOnIsNull(organization.getId()))
            .thenReturn(Optional.of(organization));
        if (parentId != null) {
            children.computeIfAbsent(parentId, key -> new ArrayList<>()).add(organization);
        }
        return organization;
    }

    private AppUser user(String username, String name, Organization organization, Integer level, String capLanhDao,
                         int sortOrder, boolean active) {
        AppUser appUser = new AppUser("kc-" + username, username, name, null, organization.getId(), null, active,
            level, sortOrder, null, null, null, null, null, capLanhDao, null, false, null);
        users.add(appUser);
        lenient().when(appUserRepository.findByUsernameAndDeletedOnIsNull(username)).thenReturn(Optional.of(appUser));
        return appUser;
    }

    private void loginAs(String username) {
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of(username));
    }

    // skhcn (van thu, o Van phong so), cap ban hanh = So: lanh dao van phong level 3 (khoa = So) hien;
    // chuyen vien/lanh dao phong level 4 KHONG hien (khong phai lanhdaovp, khoa nhom 2 = Van phong).
    @Test
    void execute_skhcnAtSoLevel_shouldReturnOfficeLeadersOrderedBySortOrder() {
        user("demo6", "Phó Chánh Văn phòng", vanPhong, 3, "lanhdaovp", 2, true);
        user("demo5", "Chánh Văn phòng", vanPhong, 3, "lanhdaovp", 1, true);
        user("demo7", "Chuyên viên 3", vanPhong, 4, null, 3, true);
        user("demo1", "Trưởng phòng CN", phongCongNghe, 4, "lanhdaodv", 1, true);
        user("skhcn", "Văn thư", vanPhong, 3, null, 9, true);
        loginAs("skhcn");

        var data = useCase.execute(so.getId().toString()).getData();

        assertThat(data).extracting(node -> node.get("username")).containsExactly("demo5", "demo6");
        assertThat(data.get(0)).containsEntry("caplanhdao", "lanhdaovp").containsEntry("level", "3");
    }

    // Nhom 2: lanh dao cua to chuc NGAY DUOI cap ban hanh tren chuoi nguoi soan (truong phong ky thua
    // lenh giam doc), khoa = Phong Cong nghe (level 4). Khong trung voi nhom 1, bo user bi khoa.
    @Test
    void execute_staffInDepartment_shouldAddLeadersOfChildOrganization() {
        user("demo5", "Chánh Văn phòng", vanPhong, 3, "lanhdaovp", 1, true);
        user("demo1", "Trưởng phòng CN", phongCongNghe, 4, "lanhdaodv", 1, true);
        user("demo2", "Phó Trưởng phòng CN", phongCongNghe, 4, "lanhdaophong", 2, true);
        user("demoX", "Lãnh đạo nghỉ", phongCongNghe, 4, "lanhdaophong", 3, false);
        user("demo3", "Chuyên viên 1", phongCongNghe, 4, "khong", 4, true);
        loginAs("demo3");

        var data = useCase.execute(so.getId().toString()).getData();

        assertThat(data).extracting(node -> node.get("username")).containsExactly("demo5", "demo1", "demo2");
    }

    @Test
    void execute_withInvalidCapBanHanh_shouldReturnEmpty() {
        assertThat(useCase.execute("not-a-uuid").getData()).isEmpty();
        assertThat(useCase.execute(null).getData()).isEmpty();
    }

    // 01/10/2026: cau hinh "lanh dao cua 1 to chuc" tren to chuc cap ban hanh (HĐND: Hoi dong nhan dan
    // -> lanh dao ngoi TRUC TIEP o co quan) - THAY THE thuat toan mac dinh.
    @Test
    void execute_configuredLeadersOfOrg_shouldReplaceDefaultWithDirectLeaders() {
        Organization hoiDong = org("K56.01", "Hội đồng nhân dân", "unit", so.getId());
        hoiDong.replaceDynamicFields("{\"cfg_kythualenh_nguon\":\"lanhdao_to_chuc\",\"cfg_kythualenh_to_chuc_id\":\""
            + so.getId() + "\"}");
        user("chutich", "Chủ tịch", so, 3, "lanhdaocq", 1, true);
        user("demo5", "Chánh Văn phòng", vanPhong, 3, "lanhdaovp", 1, true);
        user("vanthu", "Văn thư", so, 3, "khong", 9, true);
        loginAs("vanthu");

        var data = useCase.execute(hoiDong.getId().toString()).getData();

        assertThat(data).extracting(node -> node.get("username")).containsExactly("chutich");
    }

    // cau hinh "chuc vu trong cac to chuc" (HĐND: Van phong -> Truong phong cua cac phong), khong phan
    // biet hoa thuong, giu thu tu to chuc da cau hinh.
    @Test
    void execute_configuredPositionInOrgs_shouldMatchPositionCaseInsensitive() {
        vanPhong.replaceDynamicFields("{\"cfg_kythualenh_nguon\":\"chucvu_cac_to_chuc\",\"cfg_kythualenh_to_chuc_ids\":\""
            + phongCongNghe.getId() + ";" + vanPhong.getId() + "\",\"cfg_kythualenh_chuc_vu\":\"Trưởng Phòng\"}");
        user("tp_cn", "TP Công nghệ", phongCongNghe, 4, "lanhdaophong", 1, true)
            .replaceDynamicFields("{\"position\":\"trưởng  phòng\"}");
        user("pp_cn", "PP Công nghệ", phongCongNghe, 4, "lanhdaophong", 2, true)
            .replaceDynamicFields("{\"position\":\"Phó Trưởng phòng\"}");
        user("demo5", "Chánh Văn phòng", vanPhong, 3, "lanhdaovp", 1, true)
            .replaceDynamicFields("{\"position\":\"Chánh Văn phòng\"}");
        loginAs("tp_cn");

        var data = useCase.execute(vanPhong.getId().toString()).getData();

        assertThat(data).extracting(node -> node.get("username")).containsExactly("tp_cn");
    }
}
