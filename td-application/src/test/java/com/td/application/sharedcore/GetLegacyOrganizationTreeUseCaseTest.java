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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-105 (Cây cơ cấu tổ chức) — Bugfix 21/08/2026 (D.22 tài liệu phân tích vbden): "clerical"/
 * "mailin" (đơn vị NGOÀI hệ thống, nhận văn bản qua email thay vì tài khoản — dùng ở
 * CayChonNoiXuLyCapN.jsx/ChuyenXuLyModalCapN.jsx) đã lưu qua {@code Organization.dynamicFields}
 * (BL-109) nhưng trước đây KHÔNG được đưa vào {@code node.data} của cây tổ chức.
 */
@ExtendWith(MockitoExtension.class)
class GetLegacyOrganizationTreeUseCaseTest {

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private OrganizationAgencyResolver organizationAgencyResolver;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();

    private GetLegacyOrganizationTreeUseCase useCase;

    @Test
    void execute_shouldIncludeMailinAndClericalFromDynamicFields() {
        useCase = new GetLegacyOrganizationTreeUseCase(organizationRepository, dynamicDocumentFieldsService, appUserRepository, organizationAgencyResolver, UserDelegationTestSupport.emptyResolver(), OrganizationTreeCache.passThrough());

        Organization agency = newAgencyLevel("Bo Nong nghiep");
        Organization externalUnit = newAgency("Don vi ngoai", agency.getId());
        externalUnit.replaceDynamicFields("{\"mailin\":\"donvingoai@example.gov.vn\",\"clerical\":\"true\"}");

        when(organizationRepository.search(any(), any()))
            .thenReturn(pageOf(List.of(agency, externalUnit)));

        List<LegacyOrganizationTreeNode> tree = useCase.execute(null, false).getData();

        assertThat(tree).hasSize(1);
        LegacyOrganizationTreeNode agencyNode = tree.get(0);
        assertThat(agencyNode.getData()).containsEntry("mailin", "");
        assertThat(agencyNode.getData()).containsEntry("clerical", "");

        LegacyOrganizationTreeNode unitNode = agencyNode.getChildren().get(0);
        assertThat(unitNode.getData()).containsEntry("mailin", "donvingoai@example.gov.vn");
        assertThat(unitNode.getData()).containsEntry("clerical", "true");
    }

    // 30/09/2026: "Mã định danh khác" thật (dynamicFields.identifier_other) ra key RIÊNG
    // identifier_other_org — KHÔNG đổi "identifier_other" cũ của node agency (vẫn "").
    @Test
    void execute_shouldExposeConfiguredIdentifierOtherAsSeparateKey() {
        useCase = new GetLegacyOrganizationTreeUseCase(organizationRepository, dynamicDocumentFieldsService, appUserRepository, organizationAgencyResolver, UserDelegationTestSupport.emptyResolver(), OrganizationTreeCache.passThrough());

        Organization root = newAgencyLevel("UBND thanh pho");
        Organization vanPhong = newAgency("Van phong UBND thanh pho", root.getId());
        vanPhong.replaceDynamicFields("{\"identifier_other\":\"G10\"}");
        when(organizationRepository.search(any(), any())).thenReturn(pageOf(List.of(root, vanPhong)));

        List<LegacyOrganizationTreeNode> tree = useCase.execute(null, false).getData();

        LegacyOrganizationTreeNode vanPhongNode = tree.get(0).getChildren().get(0);
        assertThat(vanPhongNode.getData()).containsEntry("identifier_other_org", "G10");
        assertThat(vanPhongNode.getData()).containsEntry("identifier_other", "");
        assertThat(tree.get(0).getData()).containsEntry("identifier_other_org", "");
    }

    @Test
    void execute_withoutDynamicFields_shouldDefaultToEmptyString() {
        useCase = new GetLegacyOrganizationTreeUseCase(organizationRepository, dynamicDocumentFieldsService, appUserRepository, organizationAgencyResolver, UserDelegationTestSupport.emptyResolver(), OrganizationTreeCache.passThrough());

        Organization agency = newAgencyLevel("Bo Nong nghiep");
        when(organizationRepository.search(any(), any())).thenReturn(pageOf(List.of(agency)));

        List<LegacyOrganizationTreeNode> tree = useCase.execute(null, false).getData();

        assertThat(tree.get(0).getData()).containsEntry("mailin", "");
        assertThat(tree.get(0).getData()).containsEntry("clerical", "");
    }

    // Bugfix 03/09/2026 (báo lỗi người dùng: modal "Chuyển xử lý" — chọn đơn vị có "Người đại diện
    // nhận" không đưa được vào user_process — xem javadoc buildNodeData/resolveReceiverUsername).
    @Test
    void execute_withReceiverConfigured_shouldResolveUsernameFromReceiverId() {
        useCase = new GetLegacyOrganizationTreeUseCase(organizationRepository, dynamicDocumentFieldsService, appUserRepository, organizationAgencyResolver, UserDelegationTestSupport.emptyResolver(), OrganizationTreeCache.passThrough());

        Organization unit = newAgencyLevel("Phong Ke hoach");
        AppUser receiverUser = new AppUser(
            "sub", "nguyenvana", "Nguyen Van A", null, null, null, true);
        UUID receiverAppUserId = receiverUser.getId();
        unit.updateLegacyData(null, receiverAppUserId.toString(), "Nguyen Van A", "Truong phong",
            null, null, null, null, null, null);
        when(organizationRepository.search(any(), any())).thenReturn(pageOf(List.of(unit)));
        // 15/10/2026: người đại diện nạp theo lô cho cả cây (bỏ N+1 findByIdAndDeletedOnIsNull).
        when(appUserRepository.findByIdInAndDeletedOnIsNull(List.of(receiverAppUserId)))
            .thenReturn(List.of(receiverUser));

        List<LegacyOrganizationTreeNode> tree = useCase.execute(null, false).getData();

        var data = tree.get(0).getData();
        assertThat(data).containsEntry("user_receiver", "nguyenvana");
        assertThat(data).containsEntry("receiver", "Nguyen Van A");
        assertThat(data).containsEntry("receiver_position", "Truong phong");
    }

    @Test
    void execute_withoutReceiverConfigured_shouldReturnEmptyUserReceiver() {
        useCase = new GetLegacyOrganizationTreeUseCase(organizationRepository, dynamicDocumentFieldsService, appUserRepository, organizationAgencyResolver, UserDelegationTestSupport.emptyResolver(), OrganizationTreeCache.passThrough());

        Organization agency = newAgencyLevel("Bo Nong nghiep");
        when(organizationRepository.search(any(), any())).thenReturn(pageOf(List.of(agency)));

        List<LegacyOrganizationTreeNode> tree = useCase.execute(null, false).getData();

        var data = tree.get(0).getData();
        assertThat(data).containsEntry("user_receiver", "");
        assertThat(data).containsEntry("receiver", "");
        assertThat(data).containsEntry("receiver_position", "");
    }

    @Test
    void execute_withReceiverIdPointingToDeletedAppUser_shouldReturnEmptyUserReceiver() {
        useCase = new GetLegacyOrganizationTreeUseCase(organizationRepository, dynamicDocumentFieldsService, appUserRepository, organizationAgencyResolver, UserDelegationTestSupport.emptyResolver(), OrganizationTreeCache.passThrough());

        Organization unit = newAgencyLevel("Phong Ke hoach");
        UUID receiverAppUserId = UUID.randomUUID();
        unit.updateLegacyData(null, receiverAppUserId.toString(), "Nguyen Van A", "Truong phong",
            null, null, null, null, null, null);
        when(organizationRepository.search(any(), any())).thenReturn(pageOf(List.of(unit)));
        when(appUserRepository.findByIdInAndDeletedOnIsNull(List.of(receiverAppUserId))).thenReturn(List.of());

        List<LegacyOrganizationTreeNode> tree = useCase.execute(null, false).getData();

        assertThat(tree.get(0).getData()).containsEntry("user_receiver", "");
    }

    // Bugfix 03/09/2026 (2, báo lỗi người dùng: "cây cơ cấu đang hiển thị thừa thông tin của [cơ
    // quan khác], đúng chỉ hiển thị cơ quan và đơn vị, phòng trong cơ quan") - xem javadoc lớp
    // GetLegacyOrganizationTreeUseCase mục scopeAgencyId/resolveCurrentUserAgencyId.
    @Test
    void execute_withNonAdminUser_shouldOnlyIncludeOwnAgencyBranch() {
        useCase = new GetLegacyOrganizationTreeUseCase(organizationRepository, dynamicDocumentFieldsService, appUserRepository, organizationAgencyResolver, UserDelegationTestSupport.emptyResolver(), OrganizationTreeCache.passThrough());

        Organization root = newAgencyLevel("Tinh Thanh Hoa");
        Organization myAgency = newAgency("So Khoa hoc va Cong nghe", root.getId());
        Organization otherAgency = newAgency("So Nong nghiep va Moi truong", root.getId());
        Organization myUnit = newUnit("Phong Quan ly", myAgency.getId());
        when(organizationRepository.search(any(), any()))
            .thenReturn(pageOf(List.of(root, myAgency, otherAgency, myUnit)));
        when(organizationAgencyResolver.resolveCurrentUserAgencyId()).thenReturn(Optional.of(myAgency.getId()));

        List<LegacyOrganizationTreeNode> tree = useCase.execute(null, false).getData();

        assertThat(tree).hasSize(1);
        LegacyOrganizationTreeNode rootNode = tree.get(0);
        assertThat(rootNode.getChildren()).hasSize(1);
        assertThat(rootNode.getChildren().get(0).getText()).isEqualTo("So Khoa hoc va Cong nghe");
        assertThat(rootNode.getChildren().get(0).getChildren()).extracting(LegacyOrganizationTreeNode::getText)
            .containsExactly("Phong Quan ly");
    }

    @Test
    void execute_withAdmin_shouldIncludeFullTreeRegardlessOfCurrentUser() {
        useCase = new GetLegacyOrganizationTreeUseCase(organizationRepository, dynamicDocumentFieldsService, appUserRepository, organizationAgencyResolver, UserDelegationTestSupport.emptyResolver(), OrganizationTreeCache.passThrough());

        Organization root = newAgencyLevel("Tinh Thanh Hoa");
        Organization myAgency = newAgency("So Khoa hoc va Cong nghe", root.getId());
        Organization otherAgency = newAgency("So Nong nghiep va Moi truong", root.getId());
        when(organizationRepository.search(any(), any()))
            .thenReturn(pageOf(List.of(root, myAgency, otherAgency)));

        List<LegacyOrganizationTreeNode> tree = useCase.execute(null, true).getData();

        assertThat(tree.get(0).getChildren()).extracting(LegacyOrganizationTreeNode::getText)
            .containsExactlyInAnyOrder("So Khoa hoc va Cong nghe", "So Nong nghiep va Moi truong");
        // organizationAgencyResolver khong duoc goi toi khi includeFullTree=true (khong ton kem truy
        // van AppUser/Organization vo ich).
    }

    @Test
    void execute_withUnresolvableCurrentUser_shouldFailOpenToFullTree() {
        useCase = new GetLegacyOrganizationTreeUseCase(organizationRepository, dynamicDocumentFieldsService, appUserRepository, organizationAgencyResolver, UserDelegationTestSupport.emptyResolver(), OrganizationTreeCache.passThrough());

        Organization root = newAgencyLevel("Tinh Thanh Hoa");
        Organization myAgency = newAgency("So Khoa hoc va Cong nghe", root.getId());
        Organization otherAgency = newAgency("So Nong nghiep va Moi truong", root.getId());
        when(organizationRepository.search(any(), any()))
            .thenReturn(pageOf(List.of(root, myAgency, otherAgency)));
        when(organizationAgencyResolver.resolveCurrentUserAgencyId()).thenReturn(Optional.empty());

        List<LegacyOrganizationTreeNode> tree = useCase.execute(null, false).getData();

        assertThat(tree.get(0).getChildren()).hasSize(2);
    }

    // Bo sung 09/09/2026 (yeu cau nguoi dung - man "Nhom xem lich tong hop" can chon TUNG NGUOI
    // CU THE, khong chi chon theo don vi - xem javadoc execute(jedisKey, includeFullTree, includeUsers)).
    @Test
    void execute_withIncludeUsersTrue_shouldAttachUserLeafNodesUnderTheirOrganization() {
        useCase = new GetLegacyOrganizationTreeUseCase(organizationRepository, dynamicDocumentFieldsService, appUserRepository, organizationAgencyResolver, UserDelegationTestSupport.emptyResolver(), OrganizationTreeCache.passThrough());

        Organization agency = newAgencyLevel("So Khoa hoc va Cong nghe");
        Organization department = newUnit("Phong Quan ly", agency.getId());
        when(organizationRepository.search(any(), any()))
            .thenReturn(pageOf(List.of(agency, department)));
        AppUser truongPhong = new AppUser("sub", "truongphong01", "Nguyen Van A", null,
            department.getId(), null, true, null, 0, null, null, null, null, null, "Truong phong", "MDD01", false, null);
        when(appUserRepository.findByOrganizationIdInAndDeletedOnIsNull(any()))
            .thenReturn(List.of(truongPhong));

        List<LegacyOrganizationTreeNode> tree = useCase.execute(null, true, true).getData();

        LegacyOrganizationTreeNode departmentNode = tree.get(0).getChildren().get(0);
        assertThat(departmentNode.getChildren()).hasSize(1);
        LegacyOrganizationTreeNode userNode = departmentNode.getChildren().get(0);
        assertThat(userNode.getForm()).isEqualTo("user");
        assertThat(userNode.getText()).isEqualTo("Nguyen Van A");
        assertThat(userNode.getData()).containsEntry("username", "truongphong01");
        assertThat(userNode.getData()).containsEntry("identifier", "MDD01");
        assertThat(userNode.getData()).containsEntry("cap_lanh_dao", "Truong phong");
        assertThat(userNode.getChildren()).isEmpty();
    }

    @Test
    void execute_withIncludeUsersFalse_shouldNotQueryOrAttachUsers() {
        useCase = new GetLegacyOrganizationTreeUseCase(organizationRepository, dynamicDocumentFieldsService, appUserRepository, organizationAgencyResolver, UserDelegationTestSupport.emptyResolver(), OrganizationTreeCache.passThrough());

        Organization agency = newAgencyLevel("So Khoa hoc va Cong nghe");
        when(organizationRepository.search(any(), any())).thenReturn(pageOf(List.of(agency)));

        List<LegacyOrganizationTreeNode> tree = useCase.execute(null, true).getData();

        assertThat(tree.get(0).getChildren()).isEmpty();
        verify(appUserRepository, never()).findByOrganizationIdInAndDeletedOnIsNull(any());
    }

    // 15/10/2026 (cache cây cơ cấu): cây dùng chung trong cache KHÔNG được bị sửa khi lọc theo cơ quan
    // người xem — gọi cây đã lọc trước rồi cây đầy đủ, phải còn đủ 2 cơ quan; DB chỉ nạp 1 lần.
    @Test
    void execute_withSharedCache_shouldNotMutateCachedTreeAndShouldLoadOnce() {
        OrganizationTreeCache cache = new OrganizationTreeCache(new InMemoryOrganizationTreeCacheStore(), null);
        useCase = new GetLegacyOrganizationTreeUseCase(organizationRepository, dynamicDocumentFieldsService, appUserRepository, organizationAgencyResolver, UserDelegationTestSupport.emptyResolver(), cache);

        Organization root = newAgencyLevel("Tinh Thanh Hoa");
        Organization myAgency = newAgency("So Khoa hoc va Cong nghe", root.getId());
        Organization otherAgency = newAgency("So Nong nghiep va Moi truong", root.getId());
        when(organizationRepository.search(any(), any()))
            .thenReturn(pageOf(List.of(root, myAgency, otherAgency)));
        when(organizationAgencyResolver.resolveCurrentUserAgencyId()).thenReturn(Optional.of(myAgency.getId()));

        List<LegacyOrganizationTreeNode> scoped = useCase.execute(null, false).getData();
        List<LegacyOrganizationTreeNode> full = useCase.execute(null, true).getData();

        assertThat(scoped.get(0).getChildren()).hasSize(1);
        assertThat(full.get(0).getChildren()).hasSize(2);
        assertThat(full.get(0).getData()).containsKey("delegation_obj");
        verify(organizationRepository, times(1)).search(any(), any());
    }

    private Page<Organization> pageOf(List<Organization> organizations) {
        return new PageImpl<>(organizations);
    }

    private Organization newAgencyLevel(String name) {
        return new Organization("ID-" + UUID.randomUUID(), name, null, null,
            "agency_level", 1, name, 0, 0, null, null, null, null, null, null, null, null, null);
    }

    private Organization newAgency(String name, UUID parentId) {
        return new Organization("ID-" + UUID.randomUUID(), name, null, parentId,
            "agency", 2, name, 0, 0, null, null, null, null, null, null, null, null, null);
    }

    private Organization newUnit(String name, UUID parentId) {
        return new Organization("ID-" + UUID.randomUUID(), name, null, parentId,
            "unit", 3, name, 0, 0, null, null, null, null, null, null, null, null, null);
    }
}
