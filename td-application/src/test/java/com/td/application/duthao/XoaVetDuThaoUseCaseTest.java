package com.td.application.duthao;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.FileDto;
import com.td.application.documents.ListDocumentFilesByProcessUseCase;
import com.td.application.documents.SupersedeDocumentFilesUseCase;
import com.td.application.sharedcore.UserFullNameResolver;
import com.td.domain.duthao.DraftProcessDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 03/10/2026 — {@link XoaVetDuThaoUseCase} + {@link XoaTepVetDuThaoUseCase} (cutover xoá vết / xoá tệp
 * ở "Quá trình dự thảo"): chỉ người tạo dòng được xoá, giữ đúng các field Domino FE đang đọc.
 */
@ExtendWith(MockitoExtension.class)
class XoaVetDuThaoUseCaseTest {

    @Mock
    private DraftProcessDocumentRepository draftProcessDocumentRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private UserFullNameResolver userFullNameResolver;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private ListDocumentFilesByProcessUseCase listDocumentFilesByProcessUseCase;
    @Mock
    private SupersedeDocumentFilesUseCase supersedeDocumentFilesUseCase;

    private final DynamicDocumentFieldsService fieldsService = new DynamicDocumentFieldsService();
    private XoaVetDuThaoUseCase xoaVet;
    private XoaTepVetDuThaoUseCase xoaTepVet;

    @BeforeEach
    void setUp() {
        var ownership = new DraftProcessOwnership(draftProcessDocumentRepository, fieldsService, currentUserProvider);
        xoaVet = new XoaVetDuThaoUseCase(ownership, draftProcessDocumentRepository, userFullNameResolver,
            documentCacheService, documentLogService);
        xoaTepVet = new XoaTepVetDuThaoUseCase(ownership, draftProcessDocumentRepository,
            listDocumentFilesByProcessUseCase, supersedeDocumentFilesUseCase, documentCacheService, documentLogService);
        lenient().when(draftProcessDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void xoaVet_byCreator_shouldFlagDeletedWithDominoKeys() {
        DraftProcessDocument process = process("{\"user_creator\":\"lpha\",\"creator\":\"Lê Phú Hà\"}");
        when(draftProcessDocumentRepository.findById(process.getId())).thenReturn(Optional.of(process));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("lpha"));
        when(userFullNameResolver.resolveForWrite("lpha", null)).thenReturn("Lê Phú Hà");

        var result = xoaVet.execute(process.getId());

        Map<String, Object> fields = fieldsService.parseDynamicFields(process.getDynamicFieldsJson());
        assertThat(result.isSuccess()).isTrue();
        assertThat(fields).containsEntry("delete", "1").containsEntry("delete_user", "lpha")
            .containsEntry("delete_name", "Lê Phú Hà").containsKey("delele_time");
    }

    @Test
    void xoaVet_byOtherUser_shouldFailWithoutSaving() {
        DraftProcessDocument process = process("{\"user_creator\":\"lpha\"}");
        when(draftProcessDocumentRepository.findById(process.getId())).thenReturn(Optional.of(process));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("demo1"));

        assertThat(xoaVet.execute(process.getId()).isSuccess()).isFalse();
        verify(draftProcessDocumentRepository, never()).save(any());
    }

    // Du lieu cu: user_creator trong, creator = tai khoan.
    @Test
    void xoaVet_legacyCreatorUsername_shouldBeTreatedAsOwner() {
        DraftProcessDocument process = process("{\"creator\":\"lpha\"}");
        when(draftProcessDocumentRepository.findById(process.getId())).thenReturn(Optional.of(process));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("lpha"));

        assertThat(xoaVet.execute(process.getId()).isSuccess()).isTrue();
    }

    @Test
    void xoaTepVet_shouldSupersedeProcessFileAndAppendFileXoa() {
        UUID draftId = UUID.randomUUID();
        DraftProcessDocument process = process("{\"user_creator\":\"lpha\",\"parentid\":\"" + draftId + "\",\"file_xoa\":\"cu\"}");
        UUID fileId = UUID.randomUUID();
        when(draftProcessDocumentRepository.findById(process.getId())).thenReturn(Optional.of(process));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("lpha"));
        when(listDocumentFilesByProcessUseCase.execute(any())).thenReturn(new PaginationResponse<>(
            List.of(FileDto.builder().fileId(fileId).originalFileName("y-kien.pdf").build()), 0, 1, 1L, 1, true, true));

        var result = xoaTepVet.execute(request(process.getId(), fileId));

        assertThat(result.isSuccess()).isTrue();
        verify(supersedeDocumentFilesUseCase).execute(eq(draftId), eq(List.of(fileId)), eq(process.getId()));
        String fileXoa = String.valueOf(fieldsService.parseDynamicFields(process.getDynamicFieldsJson()).get("file_xoa"));
        assertThat(fileXoa).startsWith("cu\nlpha đã xóa file y-kien.pdf vào lúc ");
    }

    @Test
    void xoaTepVet_fileNotOfThisProcess_shouldFail() {
        UUID draftId = UUID.randomUUID();
        DraftProcessDocument process = process("{\"user_creator\":\"lpha\",\"parentid\":\"" + draftId + "\"}");
        when(draftProcessDocumentRepository.findById(process.getId())).thenReturn(Optional.of(process));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("lpha"));
        when(listDocumentFilesByProcessUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.of(), 0, 1, 0L, 0, true, true));

        assertThat(xoaTepVet.execute(request(process.getId(), UUID.randomUUID())).isSuccess()).isFalse();
        verify(supersedeDocumentFilesUseCase, never()).execute(any(), anyCollection(), any());
    }

    private DraftProcessDocument process(String dynamicFieldsJson) {
        return new DraftProcessDocument("Vết", "draft_process", "1", null, null, null, null)
            .replaceDynamicFields(dynamicFieldsJson);
    }

    private XoaTepVetDuThaoRequest request(UUID processId, UUID fileId) {
        XoaTepVetDuThaoRequest request = new XoaTepVetDuThaoRequest();
        request.setProcessId(processId);
        request.setFileId(fileId);
        return request;
    }
}
