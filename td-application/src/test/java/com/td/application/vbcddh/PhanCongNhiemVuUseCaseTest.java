package com.td.application.vbcddh;

import com.td.domain.vbcddh.VbcddhDocument;

import com.td.application.common.models.Result;
import com.td.application.documents.AddDynamicDocumentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-250 (VBCDDH Core) — DoD (2): phân công nhiệm vụ cho 1..N đơn vị thực hiện qua
 * {@link PhanCongNhiemVuUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class PhanCongNhiemVuUseCaseTest {

    @Mock
    private VbcddhDocumentRepository vbcddhDocumentRepository;
    @Mock
    private AddVbcddhDynamicDocumentUseCase addVbcddhDynamicDocumentUseCase;
    @Mock
    private VbcddhStatusRollupService statusRollupService;

    private PhanCongNhiemVuUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PhanCongNhiemVuUseCase(vbcddhDocumentRepository, addVbcddhDynamicDocumentUseCase, statusRollupService);
    }

    @Test
    void execute_withUnknownVanBan_shouldReturnFailure() {
        UUID idVanBan = UUID.randomUUID();
        when(vbcddhDocumentRepository.findById(idVanBan)).thenReturn(Optional.empty());

        var request = new PhanCongNhiemVuRequest();
        request.setIdVanBan(idVanBan);
        request.getAssignments().add(assignment("Don vi A", "donvi_a"));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withNonVbcddhVanBan_shouldReturnFailure() {
        VbcddhDocument vanBan = newDocument("vbden");
        when(vbcddhDocumentRepository.findById(vanBan.getId())).thenReturn(Optional.of(vanBan));

        var request = new PhanCongNhiemVuRequest();
        request.setIdVanBan(vanBan.getId());
        request.getAssignments().add(assignment("Don vi A", "donvi_a"));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_shouldCreateOneNhiemVuPerAssignmentAndTriggerRollup() {
        VbcddhDocument vanBan = newDocument("vbcddh");
        when(vbcddhDocumentRepository.findById(vanBan.getId())).thenReturn(Optional.of(vanBan));
        when(addVbcddhDynamicDocumentUseCase.execute(any()))
            .thenReturn(Result.success(UUID.randomUUID()))
            .thenReturn(Result.success(UUID.randomUUID()));

        var request = new PhanCongNhiemVuRequest();
        request.setIdVanBan(vanBan.getId());
        request.getAssignments().add(assignment("Don vi A", "donvi_a"));
        request.getAssignments().add(assignment("Don vi B", "donvi_b"));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).hasSize(2);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbcddhDynamicDocumentUseCase, times(2)).execute(captor.capture());
        List<AddDynamicDocumentRequest> captured = captor.getAllValues();
        assertThat(captured.get(0).getDocumentType()).isEqualTo("vbcddh");
        assertThat(captured.get(0).getFields()).containsEntry("form", "nhiemvu");
        assertThat(captured.get(0).getFields()).containsEntry("idvanban", vanBan.getId().toString());
        assertThat(captured.get(0).getFields()).containsEntry("donvithuchienhide", "donvi_a");
        assertThat(captured.get(0).getFields()).containsEntry("tinhtrang", "Chưa thực hiện");
        assertThat(captured.get(1).getFields()).containsEntry("donvithuchienhide", "donvi_b");

        verify(statusRollupService).recomputeParentStatus(vanBan.getId());
    }

    @Test
    void execute_whenChildCreationFails_shouldThrow() {
        VbcddhDocument vanBan = newDocument("vbcddh");
        when(vbcddhDocumentRepository.findById(vanBan.getId())).thenReturn(Optional.of(vanBan));
        when(addVbcddhDynamicDocumentUseCase.execute(any())).thenReturn(Result.failure("loi"));

        var request = new PhanCongNhiemVuRequest();
        request.setIdVanBan(vanBan.getId());
        request.getAssignments().add(assignment("Don vi A", "donvi_a"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> useCase.execute(request));
    }

    private NhiemVuAssignment assignment(String donVi, String donViHide) {
        var assignment = new NhiemVuAssignment();
        assignment.setDonViThucHien(donVi);
        assignment.setDonViThucHienHide(donViHide);
        return assignment;
    }

    private VbcddhDocument newDocument(String documentType) {
        VbcddhDocument document = new VbcddhDocument("Van ban chi dao", documentType, "1", null, null, null, null);
        document.replaceDynamicFields("{}");
        return document;
    }
}
