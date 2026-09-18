package com.td.application.sharedcore;

import com.td.domain.sharedcore.Organization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Bugfix 03/09/2026 (báo lỗi người dùng, tự test bằng payload {@code {"dynamicFields":
 * {"clerical":"0"}}} — xem chi tiết vẫn không đổi VÀ trong CSDL cũng không có): {@link
 * UpdateOrganizationUseCase} chưa bao giờ đọc {@code request.getDynamicFields()} dù field này đã có
 * sẵn trên request + FE ({@code organizationApi.js}) đã tách đúng payload gửi field kiểu Domino cũ
 * (mailin, clerical, identifier_other, short_name, roles, cfg_*...) qua đây.
 */
@ExtendWith(MockitoExtension.class)
class UpdateOrganizationUseCaseTest {

    @Mock
    private OrganizationRepository organizationRepository;

    private Organization newAgencyLevel(String dynamicFieldsJson) {
        var organization = new Organization(
            "ROOT", "UBND Tinh", null, null, "agency_level", 0, "UBND Tinh", 0,
            0, null, null, null, null, null, null, null, null, null
        );
        if (dynamicFieldsJson != null) {
            organization.replaceDynamicFields(dynamicFieldsJson);
        }
        return organization;
    }

    @Test
    void execute_withDynamicFields_shouldMergeIntoExistingDynamicFieldsAndPersist() {
        var useCase = new UpdateOrganizationUseCase(organizationRepository);
        UUID id = UUID.randomUUID();
        var current = newAgencyLevel("{\"mailin\":\"cu@vd.gov.vn\"}");
        when(organizationRepository.findByIdAndDeletedOnIsNull(id)).thenReturn(Optional.of(current));
        when(organizationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new UpdateOrganizationRequest();
        request.setForm("agency_level");
        Map<String, String> dynamicFields = new LinkedHashMap<>();
        dynamicFields.put("clerical", "0");
        request.setDynamicFields(dynamicFields);

        var result = useCase.execute(id, request);

        assertThat(result.isSuccess()).isTrue();
        // Field cũ ("mailin") KHÔNG bị xoá — đúng ngữ nghĩa MERGE, không phải thay thế toàn bộ.
        assertThat(current.getDynamicFieldsJson())
            .contains("\"mailin\":\"cu@vd.gov.vn\"")
            .contains("\"clerical\":\"0\"");
    }

    @Test
    void execute_withDynamicFields_shouldOverwriteExistingKeyWithSameName() {
        var useCase = new UpdateOrganizationUseCase(organizationRepository);
        UUID id = UUID.randomUUID();
        var current = newAgencyLevel("{\"clerical\":\"1\"}");
        when(organizationRepository.findByIdAndDeletedOnIsNull(id)).thenReturn(Optional.of(current));
        when(organizationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new UpdateOrganizationRequest();
        request.setForm("agency_level");
        Map<String, String> dynamicFields = new LinkedHashMap<>();
        dynamicFields.put("clerical", "0");
        request.setDynamicFields(dynamicFields);

        useCase.execute(id, request);

        assertThat(current.getDynamicFieldsJson()).contains("\"clerical\":\"0\"").doesNotContain("\"clerical\":\"1\"");
    }

    // Bugfix 03/09/2026 (báo lỗi người dùng: "level = 0, không có cấp 0, bắt đầu tính từ 1").
    @Test
    void execute_onRootNode_shouldRecomputeLevelAsOneNotZero() {
        var useCase = new UpdateOrganizationUseCase(organizationRepository);
        UUID id = UUID.randomUUID();
        var current = newAgencyLevel(null); // level=0 khi tạo (mô phỏng dữ liệu cũ trước bugfix)
        when(organizationRepository.findByIdAndDeletedOnIsNull(id)).thenReturn(Optional.of(current));
        when(organizationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new UpdateOrganizationRequest();
        request.setForm("agency_level");
        request.setName("Ten moi"); // co doi 1 field de vuot qua guard "khong doi gi" o FE tuong duong

        useCase.execute(id, request);

        assertThat(current.getLevel()).isEqualTo(1);
    }

    @Test
    void execute_withoutDynamicFields_shouldLeaveExistingDynamicFieldsUnchanged() {
        var useCase = new UpdateOrganizationUseCase(organizationRepository);
        UUID id = UUID.randomUUID();
        var current = newAgencyLevel("{\"mailin\":\"cu@vd.gov.vn\"}");
        when(organizationRepository.findByIdAndDeletedOnIsNull(id)).thenReturn(Optional.of(current));
        when(organizationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new UpdateOrganizationRequest();
        request.setForm("agency_level");
        request.setName("Ten moi");
        // dynamicFields = null (mặc định) => "không đổi", đúng hợp đồng đã ghi trong javadoc field.

        var result = useCase.execute(id, request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(current.getDynamicFieldsJson()).isEqualTo("{\"mailin\":\"cu@vd.gov.vn\"}");
    }

    // Bugfix 03/09/2026 (báo lỗi người dùng, tự test bằng payload {"linked_tenant_id":"H05_02"} sau
    // khi khởi tạo 1 đơn vị mới — giá trị không lưu): CÙNG DẠNG lỗi với dynamicFields ở trên —
    // Organization.updateLinkedTenant() đã có sẵn nhưng use case chưa bao giờ gọi tới.
    @Test
    void execute_withLinkedTenantId_shouldPersistIt() {
        var useCase = new UpdateOrganizationUseCase(organizationRepository);
        UUID id = UUID.randomUUID();
        var current = newAgencyLevel(null);
        when(organizationRepository.findByIdAndDeletedOnIsNull(id)).thenReturn(Optional.of(current));
        when(organizationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new UpdateOrganizationRequest();
        // Node mock la ROOT (khong cha) -> giu "agency_level" de khop OrganizationHierarchyRules
        // (chi agency_level duoc phep lam root) - ngu canh that cua nguoi dung la "agency" nhung co
        // cha (khong root), khong lien quan gi toi bug linked_tenant_id dang test o day.
        request.setForm("agency_level");
        request.setSort_order(1);
        request.setLinked_tenant_id("H05_02");

        var result = useCase.execute(id, request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(current.getLinkedTenantId()).isEqualTo("H05_02");
    }

    @Test
    void execute_withLinkedTenantIdEmptyString_shouldUnlinkExisting() {
        var useCase = new UpdateOrganizationUseCase(organizationRepository);
        UUID id = UUID.randomUUID();
        var current = newAgencyLevel(null);
        current.updateLinkedTenant("H05_01");
        when(organizationRepository.findByIdAndDeletedOnIsNull(id)).thenReturn(Optional.of(current));
        when(organizationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new UpdateOrganizationRequest();
        request.setForm("agency_level");
        // Chuỗi rỗng = GỠ liên kết hiện có — khác null (không đổi) — đúng javadoc Organization#updateLinkedTenant.
        request.setLinked_tenant_id("");

        useCase.execute(id, request);

        assertThat(current.getLinkedTenantId()).isEmpty();
    }

    @Test
    void execute_withoutLinkedTenantId_shouldLeaveExistingLinkUnchanged() {
        var useCase = new UpdateOrganizationUseCase(organizationRepository);
        UUID id = UUID.randomUUID();
        var current = newAgencyLevel(null);
        current.updateLinkedTenant("H05_01");
        when(organizationRepository.findByIdAndDeletedOnIsNull(id)).thenReturn(Optional.of(current));
        when(organizationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new UpdateOrganizationRequest();
        request.setForm("agency_level");
        request.setName("Ten moi");
        // linked_tenant_id = null (mac dinh) => khong doi.

        useCase.execute(id, request);

        assertThat(current.getLinkedTenantId()).isEqualTo("H05_01");
    }
}
