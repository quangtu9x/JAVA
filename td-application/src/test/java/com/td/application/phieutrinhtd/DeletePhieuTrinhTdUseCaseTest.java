package com.td.application.phieutrinhtd;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.phieutrinhtd.PhieuTrinhTdDocument;
import com.td.domain.sharedcore.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeletePhieuTrinhTdUseCaseTest {

    @Mock
    private PhieuTrinhTdDocumentRepository phieuTrinhTdDocumentRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AppUserRepository appUserRepository;

    private DeletePhieuTrinhTdUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeletePhieuTrinhTdUseCase(phieuTrinhTdDocumentRepository, currentUserProvider, appUserRepository);
        lenient().when(phieuTrinhTdDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
    }

    @Test
    void execute_withEmptyList_shouldReturnFailure() {
        assertThat(useCase.execute(List.of()).isSuccess()).isFalse();
    }

    @Test
    void execute_withUnknownIds_shouldReturnFailure() {
        UUID id = UUID.randomUUID();
        when(phieuTrinhTdDocumentRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(useCase.execute(List.of(id)).isSuccess()).isFalse();
    }

    @Test
    void execute_withExistingDocument_shouldMarkDeletedUsingResolvedUserId() {
        UUID id = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        PhieuTrinhTdDocument document = new PhieuTrinhTdDocument("Phieu trinh A", PhieuTrinhTdDocument.STATUS_DANG_SOAN_THAO, null);
        ReflectionTestUtils.setField(document, "id", id);
        when(phieuTrinhTdDocumentRepository.findById(id)).thenReturn(Optional.of(document));

        AppUser appUser = org.mockito.Mockito.mock(AppUser.class);
        when(appUser.getId()).thenReturn(currentUserId);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(appUser));

        var result = useCase.execute(List.of(id));

        assertThat(result.isSuccess()).isTrue();
        assertThat(document.isDeleted()).isTrue();
        assertThat(document.getDeletedBy()).isEqualTo(currentUserId);
    }
}
