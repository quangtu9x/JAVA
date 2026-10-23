package com.td.application.vbdi;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.vbdi.VbdiDocument;
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

/**
 * 11/09/2026 (yêu cầu người dùng "port qlvb/delete (xóa văn bản) ở văn bản đi sang Java nhưng CHỈ soft
 * delete") — test {@link DeleteVbdiDocumentUseCase}. Use case NÀY đã tồn tại sẵn trong code (viết cho
 * {@link com.td.application.duthao.ThuHoiVanBanDiUseCase} tái dùng) nhưng CHƯA có test riêng lẫn CHƯA
 * được wire vào bất kỳ controller endpoint nào cho luồng "Xóa văn bản đi" trực tiếp — bổ sung cả 2.
 *
 * <p><b>LUẬT chung ghi nhớ (xem MEMORY.md "delete luôn soft-delete")</b>: MỌI chức năng "xóa" port từ
 * Domino sang Java — kể cả khi Domino gốc gọi {@code deleteoption=hard}/xoá cứng — mặc định CHỈ soft
 * delete ({@code markAsDeleted}, giữ nguyên dữ liệu + set {@code deletedOn}/{@code deletedBy}) trừ khi
 * người dùng yêu cầu rõ ràng khác. Không hard-delete (xoá cứng khỏi DB) trừ khi có endpoint ADMIN riêng
 * biệt được yêu cầu tường minh (tiền lệ {@code DocumentsController#hardDeleteDocument},
 * {@code DELETE /{id}/permanent}, role ADMIN riêng) — KHÔNG áp dụng ở đây vì ngoài phạm vi yêu cầu.</p>
 */
@ExtendWith(MockitoExtension.class)
class DeleteVbdiDocumentUseCaseTest {

    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AppUserRepository appUserRepository;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private DeleteVbdiDocumentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeleteVbdiDocumentUseCase(
            vbdiDocumentRepository, dynamicDocumentFieldsService, documentCacheService,
            documentLogService, currentUserProvider, appUserRepository);
    }

    private VbdiDocument newVbdiDocument() {
        return new VbdiDocument("CV so 1", "vbdi", "2", null, null, null, null);
    }

    @Test
    void execute_withUnknownId_shouldReturnFailure() {
        UUID id = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(id)).thenReturn(Optional.empty());

        var result = useCase.execute(id);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_shouldSoftDeleteNotHardDelete() {
        VbdiDocument vbdi = newVbdiDocument();
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        when(vbdiDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var result = useCase.execute(vbdi.getId());

        assertThat(result.isSuccess()).isTrue();
        // Soft delete: document van con "song" (chi bi danh dau deletedOn), KHONG bi xoa khoi
        // repository that (khong co lenh delete()/deleteById() nao duoc goi).
        assertThat(vbdi.isDeleted()).isTrue();
        verify(vbdiDocumentRepository, never()).delete(any());
        verify(vbdiDocumentRepository).save(vbdi);
        verify(documentCacheService).evict(vbdi.getId());
        verify(documentCacheService).evictAllListCaches();
        verify(documentLogService).logDelete(eq(vbdi.getId()), any());
    }

    @Test
    void execute_shouldStampDeletedByWithRealUserId_notRandomUuid() {
        VbdiDocument vbdi = newVbdiDocument();
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        when(vbdiDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AppUser appUser = new AppUser("keycloak-sub", "vanthu01", "Van Thu", "vanthu01@example.com", null, null, true);
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(appUser));

        useCase.execute(vbdi.getId());

        assertThat(vbdi.getDeletedBy()).isEqualTo(appUser.getId());
    }

    @Test
    void execute_whenCurrentUserUnresolvable_shouldStampNullInsteadOfRandomUuid() {
        VbdiDocument vbdi = newVbdiDocument();
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        when(vbdiDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        useCase.execute(vbdi.getId());

        assertThat(vbdi.getDeletedBy()).isNull();
    }

    @Test
    void execute_alreadyDeleted_shouldBeIdempotentAndNotLogAgain() {
        VbdiDocument vbdi = newVbdiDocument();
        vbdi.markAsDeleted(UUID.randomUUID());
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));

        var result = useCase.execute(vbdi.getId());

        assertThat(result.isSuccess()).isTrue();
        verify(vbdiDocumentRepository, never()).save(any());
        verify(documentLogService, never()).logDelete(any(), any());
        // Idempotent van goi lai search/cache evict de dam bao dong bo (khop nhanh "da xoa" trong use case).
        verify(documentCacheService).evict(vbdi.getId());
    }
}
