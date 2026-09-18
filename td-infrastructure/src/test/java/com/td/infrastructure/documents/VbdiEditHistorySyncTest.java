package com.td.infrastructure.documents;

import com.td.application.common.models.Result;
import com.td.application.vbdi.AddVbdiEditHistoryRequest;
import com.td.application.vbdi.AddVbdiEditHistoryUseCase;
import com.td.application.vbdi.VbdiDocumentRepository;
import com.td.domain.vbdi.VbdiDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Yêu cầu người dùng 28/08/2026 ("ghi log chỉnh sửa... lưu cả nhật ký thêm, xoá đính kèm"): test
 * {@link VbdiEditHistorySync}.
 */
@ExtendWith(MockitoExtension.class)
class VbdiEditHistorySyncTest {

    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private AddVbdiEditHistoryUseCase addVbdiEditHistoryUseCase;

    private VbdiEditHistorySync newSync() {
        return new VbdiEditHistorySync(vbdiDocumentRepository, addVbdiEditHistoryUseCase);
    }

    @Test
    void logFileAdded_whenDocumentIsVbdi_shouldWriteEditObjWithNewvalueOnly() {
        var sync = newSync();
        var document = new VbdiDocument("Cong van", "vbdi", "DRAFT", null, null, null, null);
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiEditHistoryUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        sync.logFileAdded(document.getId(), "bao_cao.docx");

        ArgumentCaptor<AddVbdiEditHistoryRequest> captor = ArgumentCaptor.forClass(AddVbdiEditHistoryRequest.class);
        verify(addVbdiEditHistoryUseCase).execute(captor.capture());
        var request = captor.getValue();
        assertThat(request.getParentId()).isEqualTo(document.getId());
        @SuppressWarnings("unchecked")
        var diff = (java.util.Map<String, Object>) request.getEditObj().get("tep_dinh_kem");
        assertThat(diff.get("oldvalue")).isNull();
        assertThat(diff.get("newvalue")).isEqualTo("bao_cao.docx");
    }

    @Test
    void logFileRemoved_whenDocumentIsVbdi_shouldWriteEditObjWithOldvalueOnly() {
        var sync = newSync();
        var document = new VbdiDocument("Cong van", "vbdi", "DRAFT", null, null, null, null);
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addVbdiEditHistoryUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        sync.logFileRemoved(document.getId(), "bao_cao.docx");

        ArgumentCaptor<AddVbdiEditHistoryRequest> captor = ArgumentCaptor.forClass(AddVbdiEditHistoryRequest.class);
        verify(addVbdiEditHistoryUseCase).execute(captor.capture());
        @SuppressWarnings("unchecked")
        var diff = (java.util.Map<String, Object>) captor.getValue().getEditObj().get("tep_dinh_kem");
        assertThat(diff.get("oldvalue")).isEqualTo("bao_cao.docx");
        assertThat(diff.get("newvalue")).isNull();
    }

    @Test
    void logFileAdded_whenNotVbdiDocument_shouldDoNothing() {
        var sync = newSync();
        UUID documentId = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(documentId)).thenReturn(Optional.empty());

        sync.logFileAdded(documentId, "bao_cao.docx");

        verify(addVbdiEditHistoryUseCase, never()).execute(any());
    }

    @Test
    void logFileAdded_whenNullDocumentId_shouldDoNothingWithoutQuerying() {
        var sync = newSync();

        sync.logFileAdded(null, "bao_cao.docx");

        verify(vbdiDocumentRepository, never()).findById(any());
    }

    @Test
    void logFileAdded_whenRepositoryThrows_shouldNotPropagateException() {
        var sync = newSync();
        UUID documentId = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(documentId)).thenThrow(new RuntimeException("boom"));

        // Best-effort: khong duoc lam vo luong upload/xoa tep chinh (xem javadoc lop).
        sync.logFileAdded(documentId, "bao_cao.docx");
    }
}
