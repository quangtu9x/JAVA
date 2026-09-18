package com.td.application.documents;

import lombok.Data;

import java.util.UUID;

/**
 * BL-101: request cho {@code POST /api/v1/documents/{id}/get} — tương thích API legacy
 * {@code qlvb/get} ({@code items="getall"} hoặc {@code items="field1;field2"}).
 */
@Data
public class GetDynamicDocumentRequest {

    private UUID id;

    private String items = "getall";
}
