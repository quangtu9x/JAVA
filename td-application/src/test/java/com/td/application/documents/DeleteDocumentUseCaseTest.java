package com.td.application.documents;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.sharedcore.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-103 (Audit Log) — Definition of Done (4): xoá document → có log {@code delete}.
 */
@ExtendWith(MockitoExtension.class)
class DeleteDocumentUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentSearchService documentSearchService;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AppUserRepository appUserRepository;

    @Test
    void execute_shouldLogDelete() {
        var useCase = new DeleteDocumentUseCase(
            documentRepository, documentCacheService, documentSearchService, documentLogService,
            currentUserProvider, appUserRepository);

        BusinessDocument existing = new BusinessDocument("Van ban", null, "DRAFT", null, null, null, null);
        UUID id = existing.getId();
        when(documentRepository.findById(id)).thenReturn(Optional.of(existing));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var result = useCase.execute(id);

        assertThat(result.isSuccess()).isTrue();
        verify(documentLogService).logDelete(eq(id), any());
    }

    @Test
    void execute_shouldStampDeletedByWithRealUserId_notRandomUuid() {
        var useCase = new DeleteDocumentUseCase(
            documentRepository, documentCacheService, documentSearchService, documentLogService,
            currentUserProvider, appUserRepository);

        BusinessDocument existing = new BusinessDocument("Van ban", null, "DRAFT", null, null, null, null);
        UUID id = existing.getId();
        AppUser appUser = new AppUser("keycloak-sub", "alice", "Alice Nguyen", "alice@example.com", null, null, true);
        UUID appUserId = appUser.getId();

        when(documentRepository.findById(id)).thenReturn(Optional.of(existing));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("alice"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("alice")).thenReturn(Optional.of(appUser));

        useCase.execute(id);

        assertThat(existing.getDeletedBy()).isEqualTo(appUserId);
    }

    @Test
    void execute_whenCurrentUserUnresolvable_shouldStampNullInsteadOfRandomUuid() {
        var useCase = new DeleteDocumentUseCase(
            documentRepository, documentCacheService, documentSearchService, documentLogService,
            currentUserProvider, appUserRepository);

        BusinessDocument existing = new BusinessDocument("Van ban", null, "DRAFT", null, null, null, null);
        UUID id = existing.getId();
        when(documentRepository.findById(id)).thenReturn(Optional.of(existing));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        useCase.execute(id);

        assertThat(existing.getDeletedBy()).isNull();
    }

    @Test
    void execute_alreadyDeleted_shouldNotLogAgain() {
        var useCase = new DeleteDocumentUseCase(
            documentRepository, documentCacheService, documentSearchService, documentLogService,
            currentUserProvider, appUserRepository);

        BusinessDocument existing = new BusinessDocument("Van ban", null, "DRAFT", null, null, null, null);
        existing.markAsDeleted(UUID.randomUUID());
        UUID id = existing.getId();
        when(documentRepository.findById(id)).thenReturn(Optional.of(existing));

        var result = useCase.execute(id);

        assertThat(result.isSuccess()).isTrue();
        verify(documentLogService, never()).logDelete(any(), any());
    }
}
