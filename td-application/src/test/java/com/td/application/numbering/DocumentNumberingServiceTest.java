package com.td.application.numbering;

import com.td.domain.numbering.NumberingConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-104 (Document Numbering Service) — Definition of Done (1)/(2): sinh số theo format cấu hình,
 * tăng dần theo năm (và theo scope khi cần).
 */
@ExtendWith(MockitoExtension.class)
class DocumentNumberingServiceTest {

    @Mock
    private NumberingConfigRepository numberingConfigRepository;
    @Mock
    private NumberingCounterRepository numberingCounterRepository;

    private DocumentNumberingService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new DocumentNumberingService(numberingConfigRepository, numberingCounterRepository);
    }

    @Test
    void generateNumber_shouldFormatSequenceAndYear() {
        int year = LocalDate.now().getYear();
        when(numberingConfigRepository.findByDocumentType("vbden"))
            .thenReturn(Optional.of(new NumberingConfig("vbden", "{seq}/{year}", "YEARLY")));
        when(numberingCounterRepository.incrementAndGet("vbden", year, DocumentNumberingService.DEFAULT_SCOPE))
            .thenReturn(15L);

        Optional<String> result = service.generateNumber("VBDen");

        assertThat(result).contains("15/" + year);
    }

    @Test
    void generateNumber_shouldSupportZeroPaddedSequence() {
        int year = LocalDate.now().getYear();
        when(numberingConfigRepository.findByDocumentType("vbdi"))
            .thenReturn(Optional.of(new NumberingConfig("vbdi", "{seq:04}/{year}-VBD", "YEARLY")));
        when(numberingCounterRepository.incrementAndGet(eq("vbdi"), anyInt(), anyString())).thenReturn(7L);

        Optional<String> result = service.generateNumber("vbdi");

        assertThat(result).contains("0007/" + year + "-VBD");
    }

    @Test
    void generateNumber_withoutConfig_shouldReturnEmpty() {
        when(numberingConfigRepository.findByDocumentType("khongtontai")).thenReturn(Optional.empty());

        Optional<String> result = service.generateNumber("khongtontai");

        assertThat(result).isEmpty();
        verify(numberingCounterRepository, never()).incrementAndGet(any(), anyInt(), any());
    }

    @Test
    void generateNumber_withInactiveConfig_shouldReturnEmpty() {
        NumberingConfig inactive = new NumberingConfig("vbden", "{seq}/{year}", "YEARLY").update(null, false);
        when(numberingConfigRepository.findByDocumentType("vbden")).thenReturn(Optional.of(inactive));

        Optional<String> result = service.generateNumber("vbden");

        assertThat(result).isEmpty();
        verify(numberingCounterRepository, never()).incrementAndGet(any(), anyInt(), any());
    }

    @Test
    void generateNumber_withBlankDocumentType_shouldReturnEmpty() {
        assertThat(service.generateNumber(null)).isEmpty();
        assertThat(service.generateNumber("  ")).isEmpty();
    }

    @Test
    void generateNumber_withScopeKey_shouldPassScopeToCounter() {
        int year = LocalDate.now().getYear();
        when(numberingConfigRepository.findByDocumentType("vbdi"))
            .thenReturn(Optional.of(new NumberingConfig("vbdi", "{seq}/{year}-VBD", "YEARLY")));
        when(numberingCounterRepository.incrementAndGet("vbdi", year, "unit-a")).thenReturn(1L);

        service.generateNumber("vbdi", "unit-a");

        verify(numberingCounterRepository).incrementAndGet("vbdi", year, "unit-a");
    }
}
