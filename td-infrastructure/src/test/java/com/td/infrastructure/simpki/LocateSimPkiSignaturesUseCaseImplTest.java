package com.td.infrastructure.simpki;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.simpki.LocateSimPkiSignaturesRequest;
import com.td.application.simpki.SimPkiSignatureLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Tự đặt chữ ký theo tên người ký (29/09/2026) — xem javadoc {@link LocateSimPkiSignaturesUseCaseImpl}. */
@ExtendWith(MockitoExtension.class)
class LocateSimPkiSignaturesUseCaseImplTest {

    private static final byte[] PDF = "%PDF-1.4 gia".getBytes();

    @Mock
    private TdSimPkiClient client;

    @InjectMocks
    private LocateSimPkiSignaturesUseCaseImpl useCase;

    private static String group1(String regex, String text) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    @Test
    void signerNameRegexMatchesVietnameseNameAcrossWhitespaceAndUppercase() {
        String regex = LocateSimPkiSignaturesUseCaseImpl.buildSignerNameRegex("  Nguyễn   Văn Anh ");

        assertThat(group1(regex, "KT. GIÁM ĐỐC PHÓ GIÁM ĐỐC Nguyễn Văn Anh")).isEqualTo("Nguyễn Văn Anh");
        assertThat(group1(regex, "Nguyễn  Văn   Anh")).isEqualTo("Nguyễn  Văn   Anh");
        assertThat(group1(regex, "NGUYỄN VĂN ANH")).isEqualTo("NGUYỄN VĂN ANH");
    }

    @Test
    void signerNameRegexUsesUnicodeWordBoundaries() {
        String regex = LocateSimPkiSignaturesUseCaseImpl.buildSignerNameRegex("Trần Văn An");

        // "An" không được khớp nhầm vào "Anh" (ranh giới từ Unicode tường minh).
        assertThat(group1(regex, "Trần Văn Anh")).isNull();
        assertThat(group1(regex, "ông Trần Văn An.")).isEqualTo("Trần Văn An");
    }

    @Test
    void signerNameRegexEscapesMetacharactersAndNormalizesToNfc() {
        String regex = LocateSimPkiSignaturesUseCaseImpl.buildSignerNameRegex("A.B (C)");
        assertThat(group1(regex, "x A.B (C) y")).isEqualTo("A.B (C)");
        assertThat(group1(regex, "x AxB (C) y")).isNull();

        // Tên nhập dạng tổ hợp (NFD) vẫn khớp text PDF dạng dựng sẵn (NFC).
        String nfd = java.text.Normalizer.normalize("Lê Thị Hồng", java.text.Normalizer.Form.NFD);
        assertThat(group1(LocateSimPkiSignaturesUseCaseImpl.buildSignerNameRegex(nfd), "Lê Thị Hồng"))
            .isEqualTo("Lê Thị Hồng");
    }

    @Test
    void signerNameRegexStaysWithinTdSimPkiLimitsAndRejectsBlank() {
        assertThat(LocateSimPkiSignaturesUseCaseImpl.buildSignerNameRegex("  ")).isNull();
        assertThat(LocateSimPkiSignaturesUseCaseImpl.buildSignerNameRegex(null)).isNull();
        String longName = "Nguyễn ".repeat(60).trim();
        String regex = LocateSimPkiSignaturesUseCaseImpl.buildSignerNameRegex(longName);
        assertThat(regex == null || regex.length() <= LocateSimPkiSignaturesUseCaseImpl.MAX_REGEX_LENGTH).isTrue();
        // Không chứa lượng tử lồng nhau kiểu "(...+)+" mà TDSimPki từ chối.
        String normal = LocateSimPkiSignaturesUseCaseImpl.buildSignerNameRegex("Nguyễn Văn A");
        assertThat(normal).doesNotContainPattern("\\)\\s*[+*{]");
    }

    @Test
    void executeSendsSignerRegexWithAllPagesOccurrenceByDefaultAndMapsResult() throws Exception {
        var json = new ObjectMapper().readTree(
            "[{\"regex\":\"r\",\"matchedText\":\"Nguyễn Văn A\",\"page\":2,\"x\":300.5,\"y\":120,\"width\":90,\"height\":14,\"clearanceAbove\":80.5,\"aloneOnLine\":true}]");
        when(client.locateSignatures(eq(PDF), eq("vb.pdf"), any(), eq("ALL_PAGES"), eq(36f), eq(true), eq(150f))).thenReturn(json);

        var result = useCase.execute(LocateSimPkiSignaturesRequest.builder()
            .pdfBytes(PDF).fileName("vb.pdf").signerName("Nguyễn Văn A").build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsExactly(
            new SimPkiSignatureLocation("r", "Nguyễn Văn A", 2, 300.5f, 120f, 90f, 14f, 80.5f, true));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> regexes = ArgumentCaptor.forClass(List.class);
        verify(client).locateSignatures(eq(PDF), eq("vb.pdf"), regexes.capture(), eq("ALL_PAGES"), eq(36f), eq(true), eq(150f));
        assertThat(regexes.getValue()).hasSize(1);
        assertThat(group1(regexes.getValue().get(0), "Nguyễn Văn A")).isEqualTo("Nguyễn Văn A");
    }

    @Test
    void executeReturnsEmptySuccessWhenNothingFound() {
        when(client.locateSignatures(any(), any(), any(), anyString(), any(), eq(true), any()))
            .thenReturn(new ObjectMapper().createArrayNode());

        var result = useCase.execute(LocateSimPkiSignaturesRequest.builder()
            .pdfBytes(PDF).signerName("Không Có").build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEmpty();
    }

    @Test
    void executeUsesFirstOccurrenceForCustomRegexOnlyAndFailsCleanlyOnClientError() {
        when(client.locateSignatures(any(), any(), eq(List.of("Hop dong")), eq("FIRST"), eq(null), eq(false), eq(null)))
            .thenThrow(new TdSimPkiException("TDSimPki locate-signatures that bai: HTTP 500"));

        var result = useCase.execute(LocateSimPkiSignaturesRequest.builder()
            .pdfBytes(PDF).signatureLocators(List.of("Hop dong", " ")).build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("HTTP 500");
    }

    @Test
    void executeRejectsMissingPdfOrMissingLocatorWithoutCallingTdSimPki() {
        assertThat(useCase.execute(LocateSimPkiSignaturesRequest.builder().signerName("A").build()).isSuccess())
            .isFalse();
        assertThat(useCase.execute(LocateSimPkiSignaturesRequest.builder().pdfBytes(PDF).signerName(" ").build())
            .isSuccess()).isFalse();
        verifyNoInteractions(client);
    }
}
