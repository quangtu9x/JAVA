package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.vbden.XinYKienDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * N08 (31/08/2026 — "cho ý kiến" TVUB/TVNB/BCH/BTV/TTĐU) — xem javadoc {@link ChoYKienXinYKienUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class ChoYKienXinYKienUseCaseTest {

    @Mock
    private XinYKienDocumentRepository xinYKienDocumentRepository;
    @Mock
    private AddXinYKienDynamicDocumentUseCase addXinYKienDynamicDocumentUseCase;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    @Mock
    private com.td.application.documents.AuthorAclFieldsService authorAclFieldsService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private ChoYKienXinYKienUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ChoYKienXinYKienUseCase(
            xinYKienDocumentRepository, addXinYKienDynamicDocumentUseCase, dynamicDocumentFieldsService,
            currentUserProvider, appUserRepository, documentLogService, documentCacheService,
            resolveIdentifierByLevelUseCase, authorAclFieldsService);
        lenient().when(xinYKienDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(addXinYKienDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        lenient().when(appUserRepository.findByUsernameAndDeletedOnIsNull(any())).thenReturn(Optional.empty());
    }

    @Test
    void execute_withUnknownParent_shouldReturnFailure() {
        UUID parentId = UUID.randomUUID();
        when(xinYKienDocumentRepository.findById(parentId)).thenReturn(Optional.empty());

        var request = new ChoYKienXinYKienRequest();
        request.setParentid(parentId);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    // Xac nhan cach ly: document_type khac "vbden_xinykien" (vd du lieu hong/nham loai) PHAI bi tu
    // choi.
    @Test
    void execute_withWrongDocumentType_shouldReturnFailure() {
        XinYKienDocument parent = newDocument("vbden_xinykien_res", "{}");
        when(xinYKienDocumentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new ChoYKienXinYKienRequest();
        request.setParentid(parent.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withDeletedDocument_shouldReturnFailure() {
        XinYKienDocument parent = newDocument("vbden_xinykien", "{}");
        parent.markAsDeleted(null);
        when(xinYKienDocumentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new ChoYKienXinYKienRequest();
        request.setParentid(parent.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withoutCurrentUser_shouldReturnFailure() {
        XinYKienDocument parent = newDocument("vbden_xinykien", "{}");
        when(xinYKienDocumentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var request = new ChoYKienXinYKienRequest();
        request.setParentid(parent.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_shouldMoveUsernameFromUserProcessToUserProcessedAndUserDelivered() {
        XinYKienDocument parent = newDocument(
            "vbden_xinykien", "{\"user_process\":[\"nguyenvana\",\"nguyenvanb\"]}");
        when(xinYKienDocumentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));

        var request = new ChoYKienXinYKienRequest();
        request.setParentid(parent.getId());
        request.setStatus("Đồng ý");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updatedFields.get("user_process")).isEqualTo(List.of("nguyenvanb"));
        assertThat(updatedFields.get("user_processed")).isEqualTo(List.of("nguyenvana"));
        assertThat(updatedFields.get("user_delivered")).isEqualTo(List.of("nguyenvana"));
    }

    // Doi chieu data_xinykien_mau.txt: field DONG TEN = username, va "tralois" tich luy log. Gia tri
    // ghi la "status" (nut Dong y/Khong dong y bam), KHONG PHAI "opinion" (o nhap tu do rieng) - sua
    // 01/09/2026 sau khi doc lai TraLoiVanBanXinYKien.java: docVBden.replaceItemValue(username,
    // docQTXL.status), khong phai docQTXL.opinion.
    @Test
    void execute_shouldWriteUsernameKeyedFieldAndTraloisLog() {
        XinYKienDocument parent = newDocument("vbden_xinykien", "{\"user_process\":[\"hopln.snv\"]}");
        when(xinYKienDocumentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("hopln.snv"));
        AppUser appUser = new AppUser(null, "hopln.snv", "Lê Ngọc Hợp", null, null, null, true);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("hopln.snv")).thenReturn(Optional.of(appUser));

        var request = new ChoYKienXinYKienRequest();
        request.setParentid(parent.getId());
        request.setStatus("Đồng ý");
        request.setOpinion("Ghi chú tự do không liên quan tới status");

        useCase.execute(request);

        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updatedFields.get("hopln.snv")).isEqualTo("Đồng ý");
        assertThat(updatedFields.get("tralois")).isEqualTo("Lê Ngọc Hợp: Đồng ý");
        // opinion (tu do) KHONG duoc ghi len van ban cha - chi luu tren ban ghi con.
        assertThat(updatedFields).doesNotContainKey("opinion");
    }

    @Test
    void execute_withoutStatus_shouldDefaultToDongY() {
        XinYKienDocument parent = newDocument("vbden_xinykien", "{\"user_process\":[\"nguyenvana\"]}");
        when(xinYKienDocumentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));

        var request = new ChoYKienXinYKienRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addXinYKienDynamicDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("status", "Đồng ý");
        assertThat(captor.getValue().getFields()).containsEntry("action", "dongy");
        assertThat(captor.getValue().getFields()).containsEntry("opinion", "");
    }

    @Test
    void execute_shouldCreateProcessingRecordInXinYkienResWithStatusAndOpinion() {
        XinYKienDocument parent = newDocument("vbden_xinykien", "{\"user_process\":[\"nguyenvana\"]}");
        when(xinYKienDocumentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));

        var request = new ChoYKienXinYKienRequest();
        request.setParentid(parent.getId());
        request.setStatus("Không đồng ý");
        request.setOpinion("Chua du cang cu");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addXinYKienDynamicDocumentUseCase).execute(captor.capture());
        AddDynamicDocumentRequest childRequest = captor.getValue();
        assertThat(childRequest.getDocumentType()).isEqualTo("vbden_xinykien_res");
        assertThat(childRequest.getFields()).containsEntry("parentid", parent.getId().toString());
        assertThat(childRequest.getFields()).containsEntry("action", "khongdongy");
        assertThat(childRequest.getFields()).containsEntry("nguoi_thuc_hien", "nguyenvana");
        assertThat(childRequest.getFields()).containsEntry("status", "Không đồng ý");
        assertThat(childRequest.getFields()).containsEntry("opinion", "Chua du cang cu");
    }

    // Bo sung 03/09/2026 (cung gap voi ChuyenXuLyVbdenUseCase - xem javadoc lop do).
    @Test
    void execute_withResolvableIdentifier_shouldWriteIdentifierOntoChildProcessingRecord() {
        XinYKienDocument parent = newDocument("vbden_xinykien", "{\"user_process\":[\"nguyenvana\"]}");
        when(xinYKienDocumentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("G10.08"));

        var request = new ChoYKienXinYKienRequest();
        request.setParentid(parent.getId());
        request.setStatus("Đồng ý");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addXinYKienDynamicDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("identifier", "G10.08");
    }

    @Test
    void execute_withUnresolvableIdentifier_shouldNotWriteIdentifierField() {
        XinYKienDocument parent = newDocument("vbden_xinykien", "{\"user_process\":[\"nguyenvana\"]}");
        when(xinYKienDocumentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.empty());

        var request = new ChoYKienXinYKienRequest();
        request.setParentid(parent.getId());
        request.setStatus("Đồng ý");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addXinYKienDynamicDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).doesNotContainKey("identifier");
    }

    private XinYKienDocument newDocument(String documentType, String dynamicFieldsJson) {
        XinYKienDocument document = new XinYKienDocument("Van ban xin y kien", documentType, "0", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }
}
