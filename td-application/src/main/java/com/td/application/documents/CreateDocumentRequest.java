package com.td.application.documents;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class CreateDocumentRequest {

    @NotBlank(message = "Title is required")
    @Size(min = 2, max = 300, message = "Title must be between 2 and 300 characters")
    private String title;

    @Size(max = 100, message = "Document type cannot exceed 100 characters")
    private String documentType;

    @Size(max = 50, message = "Status cannot exceed 50 characters")
    private String status;

    private String content;

    private List<String> tags = new ArrayList<>();

    private Map<String, Object> attributes = new HashMap<>();

    private Map<String, Object> metadata = new HashMap<>();
}
