package com.td.application.documents;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BL-101: request cho {@code POST /api/v1/documents/add} — tương thích API legacy {@code qlvb/add}
 * (multipart/form-data với field động + {@code date_fields}/{@code number_fields}/{@code richtext_fields}).
 */
@Data
public class AddDynamicDocumentRequest {

    private String title;

    private String documentType;

    private String status;

    private Map<String, String> fields = new LinkedHashMap<>();

    private String dateFields;

    private String numberFields;

    private String richtextFields;
}
