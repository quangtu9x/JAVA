package com.td.application.tenant;

import com.td.domain.tenant.Tenant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// BL-117.
@ExtendWith(MockitoExtension.class)
class CreateTenantUseCaseTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private TenantProvisioningService tenantProvisioningService;

    private CreateTenantRequest validRequest() {
        var request = new CreateTenantRequest();
        request.setTenantId("K05");
        request.setDisplayName("Tinh K");
        request.setHost("10.10.10.10");
        request.setPort(5432);
        request.setUsername("postgres");
        request.setPassword("mat-khau-that");
        return request;
    }

    @Test
    void execute_happyPath_shouldProvisionThenSaveEncryptedPassword() {
        var useCase = new CreateTenantUseCase(tenantRepository, tenantProvisioningService);
        when(tenantRepository.existsByTenantIdAndDeletedOnIsNull("K05")).thenReturn(false);
        when(tenantRepository.existsByDatabaseNameAndDeletedOnIsNull("k05")).thenReturn(false);
        when(tenantProvisioningService.provision(any())).thenReturn("ENCRYPTED-PASSWORD");
        when(tenantRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = useCase.execute(validRequest());

        assertThat(result.isSuccess()).isTrue();
        var captor = org.mockito.ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(captor.capture());
        assertThat(captor.getValue().getEncryptedPassword()).isEqualTo("ENCRYPTED-PASSWORD");
        assertThat(captor.getValue().getDatabaseName())
            .as("databaseName tu sinh tu tenantId (chuyen thuong) khi khong gui")
            .isEqualTo("k05");
    }

    @Test
    void execute_shouldRejectInvalidTenantId_containingSpecialCharacters() {
        var useCase = new CreateTenantUseCase(tenantRepository, tenantProvisioningService);
        var request = validRequest();
        request.setTenantId("k05; DROP TABLE tenants;--");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(tenantProvisioningService, never()).provision(any());
    }

    @Test
    void execute_shouldRejectReservedDatabaseName() {
        var useCase = new CreateTenantUseCase(tenantRepository, tenantProvisioningService);
        var request = validRequest();
        request.setDatabaseName("postgres");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("postgres");
        verify(tenantProvisioningService, never()).provision(any());
    }

    @Test
    void execute_shouldRejectDuplicateTenantId() {
        var useCase = new CreateTenantUseCase(tenantRepository, tenantProvisioningService);
        when(tenantRepository.existsByTenantIdAndDeletedOnIsNull("K05")).thenReturn(true);

        var result = useCase.execute(validRequest());

        assertThat(result.isSuccess()).isFalse();
        verify(tenantProvisioningService, never()).provision(any());
    }

    @Test
    void execute_shouldRejectDuplicateDatabaseName() {
        var useCase = new CreateTenantUseCase(tenantRepository, tenantProvisioningService);
        when(tenantRepository.existsByTenantIdAndDeletedOnIsNull("K05")).thenReturn(false);
        when(tenantRepository.existsByDatabaseNameAndDeletedOnIsNull("k05")).thenReturn(true);

        var result = useCase.execute(validRequest());

        assertThat(result.isSuccess()).isFalse();
        verify(tenantProvisioningService, never()).provision(any());
    }

    @Test
    void execute_shouldFail_whenProvisioningThrows() {
        var useCase = new CreateTenantUseCase(tenantRepository, tenantProvisioningService);
        when(tenantRepository.existsByTenantIdAndDeletedOnIsNull("K05")).thenReturn(false);
        when(tenantRepository.existsByDatabaseNameAndDeletedOnIsNull("k05")).thenReturn(false);
        when(tenantProvisioningService.provision(any())).thenThrow(new IllegalStateException("khong the tao database"));

        var result = useCase.execute(validRequest());

        assertThat(result.isSuccess()).isFalse();
        verify(tenantRepository, never()).save(any());
    }
}
