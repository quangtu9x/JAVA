package com.td.application.vbcddh;

import com.td.domain.vbcddh.VbcddhDocument;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-250 (VBCDDH Core) — DoD (3): cập nhật tiến độ thực hiện nhiệm vụ qua
 * {@link CapNhatTienDoUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class CapNhatTienDoUseCaseTest {

    @Mock
    private VbcddhDocumentRepository vbcddhDocumentRepository;
    @Mock
    private AddVbcddhDynamicDocumentUseCase addVbcddhDynamicDocumentUseCase;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private VbcddhStatusRollupService statusRollupService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private CapNhatTienDoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CapNhatTienDoUseCase(
            vbcddhDocumentRepository, addVbcddhDynamicDocumentUseCase, dynamicDocumentFieldsService,
            currentUserProvider, documentLogService, documentCacheService, statusRollupService);
    }

    @Test
    void execute_withUnknownNhiemVu_shouldReturnFailure() {
        UUID id = UUID.randomUUID();
        when(vbcddhDocumentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new CapNhatTienDoRequest();
        request.setIdNhiemVu(id);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withNonNhiemVuForm_shouldReturnFailure() {
        VbcddhDocument vanBan = newDocument("vbcddh", "{\"form\":\"vanban\"}");
        when(vbcddhDocumentRepository.findById(vanBan.getId())).thenReturn(Optional.of(vanBan));

        var request = new CapNhatTienDoRequest();
        request.setIdNhiemVu(vanBan.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_capNhatTienDo_shouldCreateTienDoNhiemVuChildAndBumpToDangThucHien() {
        VbcddhDocument nhiemVu = newDocument("vbcddh",
            "{\"form\":\"nhiemvu\",\"tinhtrang\":\"Chưa thực hiện\",\"idvanban\":\"" + UUID.randomUUID() + "\"}");
        when(vbcddhDocumentRepository.findById(nhiemVu.getId())).thenReturn(Optional.of(nhiemVu));
        when(vbcddhDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
        UUID childId = UUID.randomUUID();
        when(addVbcddhDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(childId));

        var request = new CapNhatTienDoRequest();
        request.setIdNhiemVu(nhiemVu.getId());
        request.setYKien("Dang thuc hien theo ke hoach");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(childId);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbcddhDynamicDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getDocumentType()).isEqualTo("vbcddh_res");
        assertThat(captor.getValue().getFields()).containsEntry("form", "tiendonhiemvu");
        assertThat(captor.getValue().getFields()).containsEntry("idnhiemvu", nhiemVu.getId().toString());
        assertThat(captor.getValue().getFields()).containsEntry("creator", "nguyenvana");

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(nhiemVu.getDynamicFieldsJson());
        assertThat(updated).containsEntry("tinhtrang", "Đang thực hiện");
        verify(statusRollupService).recomputeParentStatus(any());
    }

    @Test
    void execute_capNhatKetQuaXuLy_shouldSetDaThucHienAndCreateKetQuaChild() {
        UUID idVanBan = UUID.randomUUID();
        VbcddhDocument nhiemVu = newDocument("vbcddh",
            "{\"form\":\"nhiemvu\",\"tinhtrang\":\"Đang thực hiện\",\"idvanban\":\"" + idVanBan + "\"}");
        when(vbcddhDocumentRepository.findById(nhiemVu.getId())).thenReturn(Optional.of(nhiemVu));
        when(vbcddhDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
        when(addVbcddhDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new CapNhatTienDoRequest();
        request.setIdNhiemVu(nhiemVu.getId());
        request.setAction("capnhatketquaxuly");
        request.setYKien("Da hoan thanh");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbcddhDynamicDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("form", "ketquaxulynhiemvu");

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(nhiemVu.getDynamicFieldsJson());
        assertThat(updated).containsEntry("tinhtrang", "Đã thực hiện");
        verify(statusRollupService).recomputeParentStatus(idVanBan);
        verify(documentLogService).logUpdate(any(), any(), any());
    }

    @Test
    void execute_whenChildCreationFails_shouldNotUpdateNhiemVuOrRollup() {
        VbcddhDocument nhiemVu = newDocument("vbcddh", "{\"form\":\"nhiemvu\",\"tinhtrang\":\"Chưa thực hiện\"}");
        when(vbcddhDocumentRepository.findById(nhiemVu.getId())).thenReturn(Optional.of(nhiemVu));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
        when(addVbcddhDynamicDocumentUseCase.execute(any())).thenReturn(Result.failure("loi"));

        var request = new CapNhatTienDoRequest();
        request.setIdNhiemVu(nhiemVu.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(vbcddhDocumentRepository, never()).save(any());
        verify(statusRollupService, never()).recomputeParentStatus(any());
    }

    private VbcddhDocument newDocument(String documentType, String dynamicFieldsJson) {
        VbcddhDocument document = new VbcddhDocument("Nhiem vu", documentType, "1", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }
}
