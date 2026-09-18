package com.td.application.hosocv;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.hosocv.HoSoCVDocument;
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

/**
 * Mở rộng BL-240 (HoSoCV Core) — G6: {@link DeleteHoSoUseCase}. Xoá nhiều id 1 lần, KHÔNG kiểm tra
 * phần tử con (đúng hành vi thật), {@code deletedBy} lấy đúng người dùng hiện tại (không
 * {@code UUID.randomUUID()}, đúng {@code docs/architecture/04} mục 3).
 */
@ExtendWith(MockitoExtension.class)
class DeleteHoSoUseCaseTest {

    @Mock
    private HoSoCVDocumentRepository hoSoCVDocumentRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AppUserRepository appUserRepository;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private DeleteHoSoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeleteHoSoUseCase(
            hoSoCVDocumentRepository, dynamicDocumentFieldsService, documentCacheService,
            documentLogService, currentUserProvider, appUserRepository);
    }

    @Test
    void execute_withEmptyList_shouldReturnFailure() {
        assertThat(useCase.execute(List.of()).isSuccess()).isFalse();
    }

    @Test
    void execute_withAllUnknownIds_shouldReturnFailure() {
        UUID id = UUID.randomUUID();
        when(hoSoCVDocumentRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(useCase.execute(List.of(id)).isSuccess()).isFalse();
    }

    @Test
    void execute_shouldMarkAsDeletedWithResolvedUserIdNotRandomUuid() {
        HoSoCVDocument document = newDocument(UUID.randomUUID());
        UUID userId = UUID.randomUUID();
        AppUser appUser = mockAppUser(userId);
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(hoSoCVDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("thanhtra01"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("thanhtra01")).thenReturn(Optional.of(appUser));

        var result = useCase.execute(List.of(document.getId()));

        assertThat(result.isSuccess()).isTrue();
        assertThat(document.isDeleted()).isTrue();
        assertThat(document.getDeletedBy()).isEqualTo(userId);
    }

    @Test
    void execute_withUnresolvableUser_shouldUseNullNotRandomUuid() {
        HoSoCVDocument document = newDocument(UUID.randomUUID());
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(hoSoCVDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        useCase.execute(List.of(document.getId()));

        assertThat(document.getDeletedBy()).isNull();
    }

    @Test
    void execute_alreadyDeleted_shouldBeIdempotent() {
        HoSoCVDocument document = newDocument(UUID.randomUUID());
        document.markAsDeleted(UUID.randomUUID());
        when(hoSoCVDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var result = useCase.execute(List.of(document.getId()));

        assertThat(result.isSuccess()).isTrue();
    }

    private AppUser mockAppUser(UUID id) {
        AppUser appUser = org.mockito.Mockito.mock(AppUser.class);
        lenient().when(appUser.getId()).thenReturn(id);
        return appUser;
    }

    private HoSoCVDocument newDocument(UUID id) {
        HoSoCVDocument document = new HoSoCVDocument("Test", "hosocv", "0", null, null, null, null);
        document.replaceDynamicFields("{}");
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }
}
