package com.td.application.tenant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

// BL-117.
@ExtendWith(MockitoExtension.class)
class TestTenantConnectionUseCaseTest {

    @Mock
    private TenantProvisioningService tenantProvisioningService;

    private TestTenantConnectionRequest validRequest() {
        var request = new TestTenantConnectionRequest();
        request.setHost("10.10.10.10");
        request.setPort(5432);
        request.setDatabaseName("tdwebapi");
        request.setUsername("postgres");
        request.setPassword("mat-khau-that");
        return request;
    }

    @Test
    void execute_shouldSucceed_whenConnectionOk() {
        doNothing().when(tenantProvisioningService).testConnection(any());
        var useCase = new TestTenantConnectionUseCase(tenantProvisioningService);

        var result = useCase.execute(validRequest());

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void execute_shouldFail_whenConnectionThrows() {
        doThrow(new IllegalStateException("Khong the ket noi toi database tenant: Connection refused"))
            .when(tenantProvisioningService).testConnection(any());
        var useCase = new TestTenantConnectionUseCase(tenantProvisioningService);

        var result = useCase.execute(validRequest());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("Connection refused");
    }
}
