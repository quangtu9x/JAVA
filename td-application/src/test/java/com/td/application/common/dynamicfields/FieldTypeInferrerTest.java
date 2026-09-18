package com.td.application.common.dynamicfields;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BL-101 (Dynamic Data Engine) — Definition of Done (4): FieldTypeInferrer nhận diện đúng
 * received_date → datetime, so_tien_number → number (và các hậu tố còn lại theo
 * TDDocumentUlties.getFieldType() phía Domino).
 */
class FieldTypeInferrerTest {

    @ParameterizedTest
    @CsvSource({
        "received_date, DATETIME",
        "deadline_time, DATETIME",
        "so_tien_number, NUMBER",
        "process_obj, JSON",
        "ghi_chu_rtext, JSON",
        "nguoi_ky_aut, TEXT_LIST",
        "nguoi_doc_rdr, TEXT_LIST",
        "danh_sach_tlist, TEXT_LIST",
        "subject, TEXT",
        "RECEIVED_DATE, DATETIME"
    })
    void infer_shouldMapSuffixToExpectedFieldType(String fieldName, FieldType expected) {
        assertThat(FieldTypeInferrer.infer(fieldName)).isEqualTo(expected);
    }

    @Test
    void infer_withoutRecognizedSuffix_shouldFallBackToExplicitFieldLists() {
        assertThat(FieldTypeInferrer.infer("custom_field", Set.of("custom_field"), Set.of(), Set.of()))
            .isEqualTo(FieldType.DATETIME);
        assertThat(FieldTypeInferrer.infer("custom_field", Set.of(), Set.of("custom_field"), Set.of()))
            .isEqualTo(FieldType.NUMBER);
        assertThat(FieldTypeInferrer.infer("custom_field", Set.of(), Set.of(), Set.of("custom_field")))
            .isEqualTo(FieldType.JSON);
    }

    @Test
    void infer_withoutAnyMatch_shouldDefaultToText() {
        assertThat(FieldTypeInferrer.infer("unit_in_charge")).isEqualTo(FieldType.TEXT);
    }

    @Test
    void infer_suffixRule_shouldTakePrecedenceOverExplicitLists() {
        // Hau to _date luon thang, giong getFieldType() phia Domino kiem tra hau to truoc.
        assertThat(FieldTypeInferrer.infer("received_date", Set.of(), Set.of("received_date"), Set.of()))
            .isEqualTo(FieldType.DATETIME);
    }

    @Test
    void infer_withNullFieldName_shouldDefaultToText() {
        assertThat(FieldTypeInferrer.infer(null)).isEqualTo(FieldType.TEXT);
    }
}
