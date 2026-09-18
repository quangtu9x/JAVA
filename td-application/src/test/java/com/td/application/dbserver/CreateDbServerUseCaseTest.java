package com.td.application.dbserver;

import com.td.application.common.crypto.CredentialCipher;
import com.td.domain.dbserver.DbServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// BL-117 (bổ sung 19/08/2026).
@ExtendWith(MockitoExtension.class)
class CreateDbServerUseCaseTest {

    @Mock
    private DbServerRepository dbServerRepository;
    @Mock
    private CredentialCipher credentialCipher;

    private CreateDbServerRequest validRequest() {
        var request = new CreateDbServerRequest();
        request.setName("Postgres chính - 10.10.10.116");
        request.setHost("10.10.10.116");
        request.setPort(5432);
        request.setDescription("Máy chủ Postgres production");
        return request;
    }

    @Test
    void execute_happyPath_shouldSave() {
        var useCase = new CreateDbServerUseCase(dbServerRepository, credentialCipher);
        when(dbServerRepository.existsByHostAndPortAndDeletedOnIsNull("10.10.10.116", 5432)).thenReturn(false);
        when(dbServerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = useCase.execute(validRequest());

        assertThat(result.isSuccess()).isTrue();
        var captor = org.mockito.ArgumentCaptor.forClass(DbServer.class);
        verify(dbServerRepository).save(captor.capture());
        assertThat(captor.getValue().getHost()).isEqualTo("10.10.10.116");
        assertThat(captor.getValue().getPort()).isEqualTo(5432);
        assertThat(captor.getValue().isActive()).isTrue();
        assertThat(captor.getValue().hasCredential()).as("khong gui username/password thi khong co credential").isFalse();
    }

    @Test
    void execute_withCredential_shouldEncryptPassword() {
        var useCase = new CreateDbServerUseCase(dbServerRepository, credentialCipher);
        when(dbServerRepository.existsByHostAndPortAndDeletedOnIsNull(anyString(), anyInt())).thenReturn(false);
        when(dbServerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(credentialCipher.encrypt("mat-khau-that")).thenReturn("ENCRYPTED");

        var request = validRequest();
        request.setUsername("postgres");
        request.setPassword("mat-khau-that");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        var captor = org.mockito.ArgumentCaptor.forClass(DbServer.class);
        verify(dbServerRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("postgres");
        assertThat(captor.getValue().getEncryptedPassword()).isEqualTo("ENCRYPTED");
        assertThat(captor.getValue().hasCredential()).isTrue();
    }

    @Test
    void execute_shouldRejectHalfCredential_usernameWithoutPassword() {
        var useCase = new CreateDbServerUseCase(dbServerRepository, credentialCipher);
        var request = validRequest();
        request.setUsername("postgres");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(dbServerRepository, never()).save(any());
    }

    @Test
    void execute_shouldRejectBlankName() {
        var useCase = new CreateDbServerUseCase(dbServerRepository, credentialCipher);
        var request = validRequest();
        request.setName("   ");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(dbServerRepository, never()).save(any());
    }

    @Test
    void execute_shouldRejectInvalidPort() {
        var useCase = new CreateDbServerUseCase(dbServerRepository, credentialCipher);
        var request = validRequest();
        request.setPort(0);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(dbServerRepository, never()).save(any());
    }

    @Test
    void execute_shouldRejectDuplicateHostAndPort() {
        var useCase = new CreateDbServerUseCase(dbServerRepository, credentialCipher);
        when(dbServerRepository.existsByHostAndPortAndDeletedOnIsNull(anyString(), anyInt())).thenReturn(true);

        var result = useCase.execute(validRequest());

        assertThat(result.isSuccess()).isFalse();
        verify(dbServerRepository, never()).save(any());
    }
}
