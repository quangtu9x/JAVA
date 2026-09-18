package com.td.application.dbserver;

import com.td.application.common.crypto.CredentialCipher;
import com.td.domain.dbserver.DbServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// BL-117 (bổ sung 19/08/2026).
@ExtendWith(MockitoExtension.class)
class GetDbServerCredentialUseCaseTest {

    @Mock
    private DbServerRepository dbServerRepository;
    @Mock
    private CredentialCipher credentialCipher;

    @Test
    void execute_withSavedCredential_shouldReturnDecryptedPassword() {
        var useCase = new GetDbServerCredentialUseCase(dbServerRepository, credentialCipher);
        var id = UUID.randomUUID();
        var entity = new DbServer("Server 1", "10.10.10.10", 5432, null, "postgres", "ENCRYPTED");
        when(dbServerRepository.findByIdAndDeletedOnIsNull(id)).thenReturn(Optional.of(entity));
        when(credentialCipher.decrypt("ENCRYPTED")).thenReturn("mat-khau-that");

        var result = useCase.execute(id);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getUsername()).isEqualTo("postgres");
        assertThat(result.getData().getPassword()).isEqualTo("mat-khau-that");
    }

    @Test
    void execute_withoutSavedCredential_shouldFail() {
        var useCase = new GetDbServerCredentialUseCase(dbServerRepository, credentialCipher);
        var id = UUID.randomUUID();
        var entity = new DbServer("Server 1", "10.10.10.10", 5432, null, null, null);
        when(dbServerRepository.findByIdAndDeletedOnIsNull(id)).thenReturn(Optional.of(entity));

        var result = useCase.execute(id);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_notFound_shouldFail() {
        var useCase = new GetDbServerCredentialUseCase(dbServerRepository, credentialCipher);
        var id = UUID.randomUUID();
        when(dbServerRepository.findByIdAndDeletedOnIsNull(id)).thenReturn(Optional.empty());

        var result = useCase.execute(id);

        assertThat(result.isSuccess()).isFalse();
    }
}
