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

    @NotBlank(message = "Tiêu đề không được để trống")
    @Size(min = 2, max = 300, message = "Tiêu đề phải từ 2 đến 300 ký tự")
    private String title;

    @Size(max = 100, message = "Loại tài liệu không vượt quá 100 ký tự")
    private String documentType;

    @Size(max = 50, message = "Trạng thái không vượt quá 50 ký tự")
    private String status;

    private String content;

    private List<String> tags = new ArrayList<>();

    private Map<String, Object> attributes = new HashMap<>();

    private Map<String, Object> metadata = new HashMap<>();
}
