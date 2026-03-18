package com.td.application.documents;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class DocumentDto {

    private UUID id;
    private String title;
    private String documentType;
    private String status;
    private String content;
    private List<String> tags;
    private Map<String, Object> attributes;
    private Map<String, Object> metadata;
    private long versionNo;
    private LocalDateTime createdOn;
    private LocalDateTime lastModifiedOn;
    private boolean deleted;

    @JsonIgnore
    private Map<String, Object> extraFields = new LinkedHashMap<>();

    @JsonAnyGetter
    public Map<String, Object> extraFieldsForResponse() {
        return extraFields == null ? Collections.emptyMap() : extraFields;
    }
}
