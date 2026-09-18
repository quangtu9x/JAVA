package com.td.application.vbden;

import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.documents.BusinessDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * BL-210/211 (Nhập mới VBDen) — tương thích {@code qlvb/vbden/KiemTrung} (Domino thật).
 */
@ExtendWith(MockitoExtension.class)
class KiemTrungVbdenUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DynamicDocumentFieldsService dynamicDocumentFieldsService;

    private KiemTrungVbdenUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new KiemTrungVbdenUseCase(documentRepository, dynamicDocumentFieldsService);
    }

    @Test
    void execute_missingCodeOrDate_shouldReturnEmptyWithoutQuerying() {
        var request = new KiemTrungVbdenRequest();
        request.setCode("");
        request.setPromulgationDate("01/01/2026");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEmpty();
    }

    @Test
    void execute_matchingCodeDateAndIdentifier_shouldReturnDuplicate() {
        BusinessDocument doc = new BusinessDocument("Van ban trung", "vbden", "0", null, null, null, null);
        when(documentRepository.findByFieldValue(eq("vbden"), eq("code"), eq("123/QD"), any()))
            .thenReturn(new PageImpl<>(java.util.List.of(doc)));
        when(dynamicDocumentFieldsService.parseDynamicFields(any())).thenReturn(Map.of(
            "code", "123/QD",
            "promulgation_date", "01/01/2026",
            "identifier_creator", "G10",
            "unit_in_charge", "So Noi Vu",
            "subject", "Trich yeu",
            // Bugfix 07/09/2026 (xem javadoc lớp): chỉ candidate ĐÃ vào sổ mới được coi là trùng.
            "document_register_id", "so-1"
        ));

        var request = new KiemTrungVbdenRequest();
        request.setCode("123/QD");
        request.setPromulgationDate("01/01/2026");
        request.setIdentifier("G10");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getUnitInCharge()).isEqualTo("So Noi Vu");
    }

    /**
     * Bugfix 07/09/2026 (báo lỗi người dùng: "snnmt" chưa có văn bản nào vẫn bị báo trùng mỗi lần
     * vào sổ — xem javadoc lớp): candidate cùng (code, promulgation_date) nhưng CHƯA vào sổ (không
     * có {@code document_register_id}) — vd. văn bản vừa đến qua điện tử, chưa ai xử lý — KHÔNG được
     * tính là trùng, đúng ngữ nghĩa Domino thật (chỉ tra {@code vbden_so}, không tra {@code vbden}).
     */
    @Test
    void execute_candidateNotYetRegistered_shouldNotMatch() {
        BusinessDocument doc = new BusinessDocument("Van ban chua vao so", "vbden", "0", null, null, null, null);
        when(documentRepository.findByFieldValue(eq("vbden"), eq("code"), eq("123/QD"), any()))
            .thenReturn(new PageImpl<>(java.util.List.of(doc)));
        when(dynamicDocumentFieldsService.parseDynamicFields(any())).thenReturn(Map.of(
            "code", "123/QD",
            "promulgation_date", "01/01/2026",
            "identifier_creator", "G10"
            // KHÔNG có "document_register_id" -> chưa vào sổ.
        ));

        var request = new KiemTrungVbdenRequest();
        request.setCode("123/QD");
        request.setPromulgationDate("01/01/2026");
        request.setIdentifier("G10");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEmpty();
    }

    @Test
    void execute_sameDocumentId_shouldBeExcluded() {
        BusinessDocument doc = new BusinessDocument("Van ban dang sua", "vbden", "0", null, null, null, null);
        when(documentRepository.findByFieldValue(any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(java.util.List.of(doc)));
        // Loại trừ theo id xảy ra TRƯỚC khi đọc dynamicFields — không stub parseDynamicFields ở đây.

        var request = new KiemTrungVbdenRequest();
        request.setId(doc.getId());
        request.setCode("123/QD");
        request.setPromulgationDate("01/01/2026");

        var result = useCase.execute(request);

        assertThat(result.getData()).isEmpty();
    }

    @Test
    void execute_differentPromulgationDate_shouldNotMatch() {
        BusinessDocument doc = new BusinessDocument("Van ban khac ngay", "vbden", "0", null, null, null, null);
        when(documentRepository.findByFieldValue(any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(java.util.List.of(doc)));
        when(dynamicDocumentFieldsService.parseDynamicFields(any())).thenReturn(Map.of(
            "code", "123/QD", "promulgation_date", "02/02/2026"
        ));

        var request = new KiemTrungVbdenRequest();
        request.setCode("123/QD");
        request.setPromulgationDate("01/01/2026");

        var result = useCase.execute(request);

        assertThat(result.getData()).isEmpty();
    }
}
