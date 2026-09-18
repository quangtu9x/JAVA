package com.td.application.sharedcore;

import com.td.domain.sharedcore.Organization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bugfix 03/09/2026 (cùng lỗi phát hiện ở {@link UpdateOrganizationUseCaseTest} —
 * {@code CreateOrganizationUseCase} cũng chưa bao giờ đọc {@code request.getDynamicFields()}).
 */
@ExtendWith(MockitoExtension.class)
class CreateOrganizationUseCaseTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Test
    void execute_withDynamicFields_shouldPersistThemOnNewOrganization() {
        var useCase = new CreateOrganizationUseCase(organizationRepository);
        when(organizationRepository.existsByIdentifierAndDeletedOnIsNull(any())).thenReturn(false);
        when(organizationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new CreateOrganizationRequest();
        request.setName("UBND Tinh");
        request.setIdentifier("UBND_TINH");
        request.setForm("agency_level");
        Map<String, String> dynamicFields = new LinkedHashMap<>();
        dynamicFields.put("clerical", "0");
        dynamicFields.put("mailin", "vd@vd.gov.vn");
        request.setDynamicFields(dynamicFields);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        var captor = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(captor.capture());
        assertThat(captor.getValue().getDynamicFieldsJson())
            .contains("\"clerical\":\"0\"")
            .contains("\"mailin\":\"vd@vd.gov.vn\"");
    }

    // Bugfix 03/09/2026 (báo lỗi người dùng: "level = 0, không có cấp 0, bắt đầu tính từ 1").
    @Test
    void execute_withoutParent_shouldAssignLevelOneToRootNode() {
        var useCase = new CreateOrganizationUseCase(organizationRepository);
        when(organizationRepository.existsByIdentifierAndDeletedOnIsNull(any())).thenReturn(false);
        when(organizationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new CreateOrganizationRequest();
        request.setName("UBND Tinh");
        request.setIdentifier("UBND_TINH");
        request.setForm("agency_level");

        useCase.execute(request);

        var captor = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(captor.capture());
        assertThat(captor.getValue().getLevel()).isEqualTo(1);
    }

    // Bugfix 03/09/2026 (báo lỗi người dùng, tự test bằng payload {"linked_tenant_id":"H05_02"} sau
    // khi khởi tạo 1 đơn vị mới — giá trị không lưu): cùng lỗi với dynamicFields ở trên.
    @Test
    void execute_withLinkedTenantId_shouldPersistItOnNewOrganization() {
        var useCase = new CreateOrganizationUseCase(organizationRepository);
        when(organizationRepository.existsByIdentifierAndDeletedOnIsNull(any())).thenReturn(false);
        when(organizationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new CreateOrganizationRequest();
        request.setName("UBND Tinh");
        request.setIdentifier("UBND_TINH");
        request.setForm("agency_level");
        request.setLinked_tenant_id("H05_02");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        var captor = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(captor.capture());
        assertThat(captor.getValue().getLinkedTenantId()).isEqualTo("H05_02");
    }

    @Test
    void execute_withoutLinkedTenantId_shouldLeaveItNull() {
        var useCase = new CreateOrganizationUseCase(organizationRepository);
        when(organizationRepository.existsByIdentifierAndDeletedOnIsNull(any())).thenReturn(false);
        when(organizationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new CreateOrganizationRequest();
        request.setName("UBND Tinh");
        request.setIdentifier("UBND_TINH");
        request.setForm("agency_level");
        // linked_tenant_id = null (mac dinh, khong gui) => khong lien ket gi ca.

        useCase.execute(request);

        var captor = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(captor.capture());
        assertThat(captor.getValue().getLinkedTenantId()).isNull();
    }

    @Test
    void execute_withEmptyDynamicFields_shouldNotFail() {
        var useCase = new CreateOrganizationUseCase(organizationRepository);
        when(organizationRepository.existsByIdentifierAndDeletedOnIsNull(any())).thenReturn(false);
        when(organizationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new CreateOrganizationRequest();
        request.setName("UBND Tinh");
        request.setIdentifier("UBND_TINH");
        request.setForm("agency_level");
        // dynamicFields mặc định = map rỗng (không null) — nhánh !isEmpty() phải bỏ qua an toàn.

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
    }
}
