package com.td.application.phieutrinhtd;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.phieutrinhtd.PhieuTrinhTdDocument;
import com.td.domain.phieutrinhtd.PhieuTrinhTdProcessDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ChuyenTrangThaiPhieuTrinhTdUseCase} — cơ chế chung tương đương
 * {@code ChiTietPage.jsx#handleTrinh(type)} (xem docs/analysis/20-...md mục 2.1/6.2).
 */
@ExtendWith(MockitoExtension.class)
class ChuyenTrangThaiPhieuTrinhTdUseCaseTest {

    @Mock
    private PhieuTrinhTdDocumentRepository phieuTrinhTdDocumentRepository;
    @Mock
    private PhieuTrinhTdProcessDocumentRepository phieuTrinhTdProcessDocumentRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AppUserRepository appUserRepository;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private ChuyenTrangThaiPhieuTrinhTdUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ChuyenTrangThaiPhieuTrinhTdUseCase(
            phieuTrinhTdDocumentRepository, phieuTrinhTdProcessDocumentRepository,
            dynamicDocumentFieldsService, currentUserProvider, appUserRepository, com.td.application.realtime.RealtimeNotifierTestSupport.noop());
        lenient().when(phieuTrinhTdDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(phieuTrinhTdProcessDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
        lenient().when(appUserRepository.findByUsernameAndDeletedOnIsNull(any())).thenReturn(Optional.empty());
    }

    @Test
    void execute_withMissingId_shouldReturnFailure() {
        var request = new ChuyenTrangThaiPhieuTrinhTdRequest();
        request.setTargetStatus("5");

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withUnknownDocument_shouldReturnFailure() {
        UUID id = UUID.randomUUID();
        when(phieuTrinhTdDocumentRepository.findById(id)).thenReturn(Optional.empty());
        var request = new ChuyenTrangThaiPhieuTrinhTdRequest();
        request.setId(id);
        request.setTargetStatus("5");

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_trinhDuyetCap1_shouldSetStatusAndResolveLanhdao1AsReceiverAndWriteLog() {
        UUID id = UUID.randomUUID();
        PhieuTrinhTdDocument document = new PhieuTrinhTdDocument(
            "Phieu trinh A", PhieuTrinhTdDocument.STATUS_DANG_SOAN_THAO, null);
        document.replaceDynamicFields("{\"lanhdao1\":\"Nguyen Van B\",\"user_lanhdao1\":\"nguyenvanb\"}");
        ReflectionTestUtils.setField(document, "id", id);
        when(phieuTrinhTdDocumentRepository.findById(id)).thenReturn(Optional.of(document));

        var request = new ChuyenTrangThaiPhieuTrinhTdRequest();
        request.setId(id);
        request.setTargetStatus(PhieuTrinhTdDocument.STATUS_TRINH_DUYET_CAP1);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(document.getStatus()).isEqualTo(PhieuTrinhTdDocument.STATUS_TRINH_DUYET_CAP1);

        ArgumentCaptor<PhieuTrinhTdProcessDocument> logCaptor = ArgumentCaptor.forClass(PhieuTrinhTdProcessDocument.class);
        verify(phieuTrinhTdProcessDocumentRepository).save(logCaptor.capture());
        Map<String, Object> logFields = dynamicDocumentFieldsService.parseDynamicFields(
            logCaptor.getValue().getDynamicFieldsJson());
        assertThat(logFields.get("form")).isEqualTo("quatrinh");
        assertThat(logFields.get("action_type")).isEqualTo("5");
        assertThat(logFields.get("receiver")).isEqualTo("Nguyen Van B");
        assertThat(logFields.get("user_receiver")).isEqualTo("nguyenvanb");
        assertThat(logFields.get("parentid")).isEqualTo(id.toString());
    }

    @Test
    void execute_thuHoi_shouldResetDownstreamDatesAndOpinions() {
        UUID id = UUID.randomUUID();
        PhieuTrinhTdDocument document = new PhieuTrinhTdDocument(
            "Phieu trinh A", PhieuTrinhTdDocument.STATUS_TRINH_DUYET_CAP1, null);
        document.replaceDynamicFields("{\"trinh5_date\":\"09/09/2026 10:00:00\",\"ykientrinh5\":\"da xem\"}");
        ReflectionTestUtils.setField(document, "id", id);
        when(phieuTrinhTdDocumentRepository.findById(id)).thenReturn(Optional.of(document));

        var request = new ChuyenTrangThaiPhieuTrinhTdRequest();
        request.setId(id);
        request.setTargetStatus(PhieuTrinhTdDocument.STATUS_DANG_SOAN_THAO);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(document.getStatus()).isEqualTo(PhieuTrinhTdDocument.STATUS_DANG_SOAN_THAO);
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(fields.get("trinh5_date")).isEqualTo("");
        assertThat(fields.get("ykientrinh5")).isEqualTo("");
    }
}
