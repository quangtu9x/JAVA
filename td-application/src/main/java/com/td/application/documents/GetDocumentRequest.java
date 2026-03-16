package com.td.application.documents;

import lombok.Data;

import java.util.UUID;

@Data
public class GetDocumentRequest {

    private UUID id;

    public GetDocumentRequest(UUID id) {
        this.id = id;
    }
}