package com.td.application.vbden;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.domain.documents.BusinessDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bổ sung 14/09/2026 ("ở chi tiết văn bản đến, chức năng Xóa đang gọi API cũ ... kiểm tra nếu chưa
 * có thì port sang JAVA và cutoverFE") — xem javadoc {@link XoaVanBanDenUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class XoaVanBanDenUseCaseTest {

    private static final String OWNER = "H05.02";

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private XoaVanBanDenUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new XoaVanBanDenUseCase(
            documentRepository, dynamicDocumentFieldsService, resolveIdentifierByLevelUseCase,
            currentUserProvider, appUserRepository, documentCacheService, documentLogService);
        lenient().when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());
    }

    private BusinessDocument newParent(String identifierCreator) {
        BusinessDocument document = new BusinessDocument("Noi dung", "vbden", "1", null, null, null, null);
        document.replaceDynamicFields("{\"identifier_creator\":\"" + identifierCreator + "\"}");
        return document;
    }

    private BusinessDocument newSoEntry(String identifierCreator) {
        BusinessDocument entry = new BusinessDocument("So", "vbden_so", "1", null, null, null, null);
        entry.replaceDynamicFields("{\"identifier_creator\":\"" + identifierCreator + "\"}");
        return entry;
    }

    private void stubSiblings(UUID parentId, List<BusinessDocument> siblings) {
        when(documentRepository.findByParentId(eq("vbden_so"), eq(parentId.toString()), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(siblings));
    }

    @Test
    void execute_missingIdvbden_shouldReturnFailure() {
        assertThat(useCase.execute(new XoaVanBanDenRequest()).isSuccess()).isFalse();
        assertThat(useCase.execute(null).isSuccess()).isFalse();
    }

    @Test
    void execute_ownerAndLastSoEntry_shouldDeleteParentAndEntry() {
        BusinessDocument parent = newParent(OWNER);
        BusinessDocument entry = newSoEntry(OWNER);
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.findById(entry.getId())).thenReturn(Optional.of(entry));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of(OWNER));
        stubSiblings(parent.getId(), List.of(entry));

        var request = new XoaVanBanDenRequest();
        request.setIdvbden(parent.getId());
        request.setIdso(entry.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(parent.isDeleted()).isTrue();
        assertThat(entry.isDeleted()).isTrue();
    }

    @Test
    void execute_ownerButOtherSoEntriesRemain_shouldOnlyDeleteEntryNotParent() {
        BusinessDocument parent = newParent(OWNER);
        BusinessDocument entry = newSoEntry(OWNER);
        BusinessDocument otherEntrySameOwner = newSoEntry(OWNER);
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.findById(entry.getId())).thenReturn(Optional.of(entry));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of(OWNER));
        stubSiblings(parent.getId(), List.of(entry, otherEntrySameOwner));

        var request = new XoaVanBanDenRequest();
        request.setIdvbden(parent.getId());
        request.setIdso(entry.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(parent.isDeleted()).isFalse();
        assertThat(entry.isDeleted()).isTrue();
    }

    @Test
    void execute_notOwner_shouldOnlyDeleteEntryNotParent() {
        BusinessDocument parent = newParent(OWNER);
        BusinessDocument entry = newSoEntry("H05.03");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.findById(entry.getId())).thenReturn(Optional.of(entry));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("H05.03"));

        var request = new XoaVanBanDenRequest();
        request.setIdvbden(parent.getId());
        request.setIdso(entry.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(parent.isDeleted()).isFalse();
        assertThat(entry.isDeleted()).isTrue();
        // Khong phai chu so huu - khong duoc dem sibling (khong can goi findByParentId).
        verify(documentRepository, never()).findByParentId(anyString(), anyString(), any());
    }

    @Test
    void execute_parentNotFound_shouldStillDeleteEntryAndSucceed() {
        UUID missingParentId = UUID.randomUUID();
        BusinessDocument entry = newSoEntry(OWNER);
        when(documentRepository.findById(missingParentId)).thenReturn(Optional.empty());
        when(documentRepository.findById(entry.getId())).thenReturn(Optional.of(entry));

        var request = new XoaVanBanDenRequest();
        request.setIdvbden(missingParentId);
        request.setIdso(entry.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(entry.isDeleted()).isTrue();
    }

    @Test
    void execute_alreadyDeletedParent_shouldBeIdempotentNoOp() {
        BusinessDocument parent = newParent(OWNER);
        parent.markAsDeleted(null);
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new XoaVanBanDenRequest();
        request.setIdvbden(parent.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        verify(documentLogService, never()).logDelete(eq(parent.getId()), any());
    }

    @Test
    void execute_withoutIdso_shouldOnlyEvaluateParent() {
        BusinessDocument parent = newParent(OWNER);
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of(OWNER));
        stubSiblings(parent.getId(), List.of());

        var request = new XoaVanBanDenRequest();
        request.setIdvbden(parent.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(parent.isDeleted()).isTrue();
    }
}
