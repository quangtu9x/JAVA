package com.td.application.vbden;

import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.domain.documents.BusinessDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Bổ sung 14/09/2026 (đối xứng {@link XoaVanBanDenUseCase}) — xem javadoc
 * {@link PhucHoiVanBanDenUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class PhucHoiVanBanDenUseCaseTest {

    private static final String OWNER = "H05.02";

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private PhucHoiVanBanDenUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PhucHoiVanBanDenUseCase(
            documentRepository, dynamicDocumentFieldsService, resolveIdentifierByLevelUseCase,
            documentCacheService, documentLogService, com.td.application.realtime.RealtimeNotifierTestSupport.noop());
        lenient().when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private BusinessDocument newDeletedParent(String identifierCreator) {
        BusinessDocument document = new BusinessDocument("Noi dung", "vbden", "1", null, null, null, null);
        document.replaceDynamicFields("{\"identifier_creator\":\"" + identifierCreator + "\"}");
        document.markAsDeleted(null);
        return document;
    }

    private BusinessDocument newDeletedSoEntry() {
        BusinessDocument entry = new BusinessDocument("So", "vbden_so", "1", null, null, null, null);
        entry.markAsDeleted(null);
        return entry;
    }

    @Test
    void execute_missingIdvbden_shouldReturnFailure() {
        assertThat(useCase.execute(new PhucHoiVanBanDenRequest()).isSuccess()).isFalse();
        assertThat(useCase.execute(null).isSuccess()).isFalse();
    }

    @Test
    void execute_ownerOfDeletedParent_shouldRestoreParentAndEntry() {
        BusinessDocument parent = newDeletedParent(OWNER);
        BusinessDocument entry = newDeletedSoEntry();
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.findById(entry.getId())).thenReturn(Optional.of(entry));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of(OWNER));

        var request = new PhucHoiVanBanDenRequest();
        request.setIdvbden(parent.getId());
        request.setIdso(entry.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(parent.isDeleted()).isFalse();
        assertThat(entry.isDeleted()).isFalse();
    }

    @Test
    void execute_notOwner_shouldOnlyRestoreEntryNotParent() {
        BusinessDocument parent = newDeletedParent(OWNER);
        BusinessDocument entry = newDeletedSoEntry();
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.findById(entry.getId())).thenReturn(Optional.of(entry));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("H05.03"));

        var request = new PhucHoiVanBanDenRequest();
        request.setIdvbden(parent.getId());
        request.setIdso(entry.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(parent.isDeleted()).isTrue();
        assertThat(entry.isDeleted()).isFalse();
    }

    @Test
    void execute_parentNotDeleted_shouldBeIdempotentNoOp() {
        BusinessDocument parent = new BusinessDocument("Noi dung", "vbden", "1", null, null, null, null);
        parent.replaceDynamicFields("{\"identifier_creator\":\"" + OWNER + "\"}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new PhucHoiVanBanDenRequest();
        request.setIdvbden(parent.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(parent.isDeleted()).isFalse();
    }

    @Test
    void execute_parentNotFound_shouldStillSucceed() {
        UUID missingId = UUID.randomUUID();
        when(documentRepository.findById(missingId)).thenReturn(Optional.empty());

        var request = new PhucHoiVanBanDenRequest();
        request.setIdvbden(missingId);

        assertThat(useCase.execute(request).isSuccess()).isTrue();
    }
}
