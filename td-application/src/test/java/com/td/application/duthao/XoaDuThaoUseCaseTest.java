package com.td.application.duthao;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.duthao.DuThaoDocument;
import org.junit.jupiter.api.BeforeEach;
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

/** 22/10/2026 — thùng rác dự thảo: xoá mềm/khôi phục, chỉ người tạo (xem javadoc {@link XoaDuThaoUseCase}). */
@ExtendWith(MockitoExtension.class)
class XoaDuThaoUseCaseTest {

    @Mock
    private DuThaoDocumentRepository duThaoDocumentRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AppUserRepository appUserRepository;

    private XoaDuThaoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new XoaDuThaoUseCase(duThaoDocumentRepository, new DynamicDocumentFieldsService(),
            documentCacheService, documentLogService, currentUserProvider, appUserRepository);
    }

    private DuThaoDocument newDraft(String userCreator) {
        DuThaoDocument draft = new DuThaoDocument("Du thao", "duthao", "0", null, null, null, null);
        draft.replaceDynamicFields("{\"user_creator\":\"" + userCreator + "\"}");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        return draft;
    }

    @Test
    void execute_byCreator_shouldSoftDeleteAndLog() {
        DuThaoDocument draft = newDraft("chuyenvien01");
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("ChuyenVien01"));
        when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = useCase.execute(draft.getId());

        assertThat(result.isSuccess()).isTrue();
        assertThat(draft.isDeleted()).isTrue();
        verify(duThaoDocumentRepository, never()).delete(any());
        verify(documentLogService).logDelete(eq(draft.getId()), any());
        verify(documentCacheService).evictAllListCaches();
    }

    @Test
    void execute_byOtherUser_shouldRefuse() {
        DuThaoDocument draft = newDraft("chuyenvien01");
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("lanhdao01"));

        var result = useCase.execute(draft.getId());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo(XoaDuThaoUseCase.NOT_OWNER_MESSAGE);
        assertThat(draft.isDeleted()).isFalse();
        verify(duThaoDocumentRepository, never()).save(any());
    }

    @Test
    void execute_unknownId_shouldFail() {
        UUID id = UUID.randomUUID();
        when(duThaoDocumentRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(useCase.execute(id).isSuccess()).isFalse();
    }

    @Test
    void khoiPhuc_deletedDraftByCreator_shouldRestore() {
        DuThaoDocument draft = newDraft("chuyenvien01");
        draft.markAsDeleted(null);
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("chuyenvien01"));
        when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = useCase.khoiPhuc(draft.getId());

        assertThat(result.isSuccess()).isTrue();
        assertThat(draft.isDeleted()).isFalse();
        verify(documentLogService).logUpdate(eq(draft.getId()), any(), any());
    }

    @Test
    void khoiPhuc_notDeleted_shouldBeIdempotent() {
        DuThaoDocument draft = newDraft("chuyenvien01");
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("chuyenvien01"));

        assertThat(useCase.khoiPhuc(draft.getId()).isSuccess()).isTrue();
        verify(duThaoDocumentRepository, never()).save(any());
    }
}
