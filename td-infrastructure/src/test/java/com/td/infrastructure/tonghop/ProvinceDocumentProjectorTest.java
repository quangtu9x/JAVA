package com.td.infrastructure.tonghop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Mẫu field lấy từ dữ liệu thật h05_01_sokhcn (12/10/2026). */
class ProvinceDocumentProjectorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final OffsetDateTime CREATED = OffsetDateTime.of(2026, 10, 12, 9, 39, 0, 0, ZoneOffset.ofHours(7));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
    private static final Map<String, String> ORGS = Map.of(
        "H05.01", "Sở Khoa học và Công nghệ", "H05.02", "Sở Nông nghiệp và Môi trường",
        "H05.01.05", "Trung tâm CNTT-TT");

    private static JsonNode json(String value) throws Exception {
        return MAPPER.readTree(value);
    }

    @Test
    void vbdenWaitingRegistrationFromNetworkBelongsToReceivingAgency() throws Exception {
        var row = ProvinceDocumentProjector.vbden("H05_01", UUID.randomUUID(), "Giấy mời họp", "0", json("""
                {"contact_unit_identifier":"H05.01","document_internet":"1","delivery_method":"Điện tử",
                 "sender_identifier":"H05.02","unit_in_charge":"Sở Nông nghiệp và Môi trường",
                 "received_date":"12/10/2026 09:39:21","code":"22/GM-SNNMT","document_number":92.0}
                """), CREATED, null, false, ORGS, TODAY);

        assertThat(row.getOwnerIdentifier()).isEqualTo("H05.01");
        assertThat(row.getOwnerName()).isEqualTo("Sở Khoa học và Công nghệ");
        assertThat(row.getProcessState()).isEqualTo("CHO_VAO_SO");
        assertThat(row.isNetwork()).isTrue();
        assertThat(row.isInternal()).isFalse();
        assertThat(row.getDocDate()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(row.getCode()).isEqualTo("22/GM-SNNMT");
        assertThat(row.getSearchText()).contains("22/gm-snnmt").contains("giay moi hop").contains("so nong nghiep");
    }

    @Test
    void vbdenInternalPendingBelongsToFirstProcessingUnit() throws Exception {
        var row = ProvinceDocumentProjector.vbden("H05_01", UUID.randomUUID(), "Báo cáo", "0", json("""
                {"identifier_creator":"H05.01.05","unit_process":["H05.01"],"internal":"1","document_internet":"1"}
                """), CREATED, null, false, ORGS, TODAY);

        assertThat(row.getOwnerIdentifier()).isEqualTo("H05.01");
        assertThat(row.isInternal()).isTrue();
        assertThat(row.isNetwork()).isFalse();
    }

    @Test
    void vbdenPaperRegisteredAndOverdue() throws Exception {
        var row = ProvinceDocumentProjector.vbden("H05_01", UUID.randomUUID(), "Công văn", "2", json("""
                {"identifier":"H05.01","method":"Giấy","paper":"0","received_date":"02/10/2026",
                 "deadline_date":"05/10/2026","document_number":"10/BC-UBND"}
                """), CREATED, null, false, ORGS, TODAY);

        assertThat(row.getProcessState()).isEqualTo("DANG_XU_LY");
        assertThat(row.isPaper()).isTrue();
        assertThat(row.isOverdue()).isTrue();
        assertThat(row.getCode()).isEqualTo("10/BC-UBND");
    }

    @Test
    void vbdiOnlyIssuedIsVisible() throws Exception {
        String fields = """
            {"unit_publish_identifier":"H05.01.05","unit_publish":"Trung tâm CNTT-TT","code_full":"3/TTCNTT",
             "document_number":3.0,"promulgation_date":"09/10/2026","send_agent":"1","dakyso":"1",
             "delivery_method":"Điện tử và giấy"}
            """;
        var issued = ProvinceDocumentProjector.vbdi("H05_01", UUID.randomUUID(), "CV", "ISSUED", json(fields),
            CREATED, null, false, ORGS);
        var draft = ProvinceDocumentProjector.vbdi("H05_01", UUID.randomUUID(), "CV", "DRAFT", json(fields),
            CREATED, null, false, ORGS);

        assertThat(issued.isDeleted()).isFalse();
        assertThat(issued.getProcessState()).isEqualTo("DA_GUI");
        assertThat(issued.isSigned()).isTrue();
        assertThat(issued.isPaper()).isTrue();
        assertThat(issued.getCode()).isEqualTo("3/TTCNTT");
        assertThat(issued.getDocDate()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(draft.isDeleted()).isTrue();
    }

    @Test
    void numericDocumentNumberDropsTrailingZero() throws Exception {
        assertThat(ProvinceText.text(json("{\"n\":92.0}"), "n")).isEqualTo("92");
        assertThat(ProvinceText.text(json("{\"n\":\"[\\\"H05.01\\\"]\"}"), "n")).isEqualTo("H05.01");
        assertThat(ProvinceText.normalize("Sở Khoa học  Đề án")).isEqualTo("so khoa hoc de an");
    }
}
