package com.td.application.common.dynamicfields;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicFieldValueConverterTest {

    @Test
    void convert_number_shouldParseToDouble() {
        assertThat(DynamicFieldValueConverter.convert(FieldType.NUMBER, "150000.5")).isEqualTo(150000.5d);
    }

    @Test
    void convert_number_withInvalidValue_shouldFallBackToRawString() {
        assertThat(DynamicFieldValueConverter.convert(FieldType.NUMBER, "not-a-number")).isEqualTo("not-a-number");
    }

    @Test
    @SuppressWarnings("unchecked")
    void convert_json_shouldParseToMap() {
        Object result = DynamicFieldValueConverter.convert(FieldType.JSON, "{\"step\":1,\"name\":\"xu ly\"}");

        assertThat(result).isInstanceOf(Map.class);
        Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map).containsEntry("step", 1).containsEntry("name", "xu ly");
    }

    @Test
    void convert_json_withInvalidValue_shouldFallBackToRawString() {
        assertThat(DynamicFieldValueConverter.convert(FieldType.JSON, "not-json")).isEqualTo("not-json");
    }

    @Test
    void convert_textList_shouldSplitOnSemicolon() {
        Object result = DynamicFieldValueConverter.convert(FieldType.TEXT_LIST, ";user1;user2;");

        assertThat(result).isEqualTo(List.of("user1", "user2"));
    }

    @Test
    void convert_dateTime_shouldKeepRawString() {
        assertThat(DynamicFieldValueConverter.convert(FieldType.DATETIME, "2026-08-04")).isEqualTo("2026-08-04");
    }

    @Test
    void convert_text_shouldKeepRawString() {
        assertThat(DynamicFieldValueConverter.convert(FieldType.TEXT, "Ha Noi")).isEqualTo("Ha Noi");
    }

    @Test
    void convert_withNullValue_shouldReturnNull() {
        assertThat(DynamicFieldValueConverter.convert(FieldType.TEXT, null)).isNull();
    }
}
