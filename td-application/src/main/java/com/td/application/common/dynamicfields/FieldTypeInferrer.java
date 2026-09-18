package com.td.application.common.dynamicfields;

import java.util.Set;

/**
 * BL-101 (Dynamic Data Engine): suy luận kiểu field động từ hậu tố tên field, tương đương
 * {@code TDDocumentUlties.getFieldType()} phía Domino. Field không khớp hậu tố nào rơi vào danh
 * sách {@code dateFields}/{@code numberFields}/{@code richTextFields} do FE gửi kèm (tương đương
 * {@code date_fields}/{@code number_fields}/{@code richtext_fields} form-data), mặc định TEXT.
 */
public final class FieldTypeInferrer {

    private FieldTypeInferrer() {
    }

    public static FieldType infer(String fieldName) {
        return infer(fieldName, Set.of(), Set.of(), Set.of());
    }

    public static FieldType infer(
            String fieldName, Set<String> dateFields, Set<String> numberFields, Set<String> richTextFields) {
        String name = fieldName == null ? "" : fieldName.trim().toLowerCase();

        if (name.endsWith("_date") || name.endsWith("_time")) {
            return FieldType.DATETIME;
        }
        if (name.endsWith("_number")) {
            return FieldType.NUMBER;
        }
        if (name.endsWith("_obj") || name.endsWith("_rtext")) {
            return FieldType.JSON;
        }
        if (name.endsWith("_aut") || name.endsWith("_rdr") || name.endsWith("_tlist")) {
            return FieldType.TEXT_LIST;
        }
        if (dateFields.contains(name)) {
            return FieldType.DATETIME;
        }
        if (numberFields.contains(name)) {
            return FieldType.NUMBER;
        }
        if (richTextFields.contains(name)) {
            return FieldType.JSON;
        }
        return FieldType.TEXT;
    }
}
