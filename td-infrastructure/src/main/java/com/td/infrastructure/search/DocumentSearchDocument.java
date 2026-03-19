package com.td.infrastructure.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Mapping;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Document(indexName = DocumentSearchDocument.INDEX_NAME)
@Setting(settingPath = "/elasticsearch/documents-settings.json")
@Mapping(mappingPath = "/elasticsearch/documents-mappings.json")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSearchDocument {

    public static final String INDEX_NAME = "documents-search";

    @Id
    private String id;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Keyword)
    private String documentType;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Text)
    private String content;

    @Field(type = FieldType.Keyword)
    @Builder.Default
    private List<String> tags = Collections.emptyList();

    @Field(type = FieldType.Object)
    @Builder.Default
    private Map<String, Object> attributes = Collections.emptyMap();

    @Field(type = FieldType.Object)
    @Builder.Default
    private Map<String, Object> metadata = Collections.emptyMap();

    @Field(type = FieldType.Object)
    @Builder.Default
    private Map<String, Object> extraFields = Collections.emptyMap();

    @Field(type = FieldType.Text)
    private String searchText;

    @Field(type = FieldType.Long)
    private long versionNo;

    @Field(type = FieldType.Long)
    private long createdOnEpochMs;

    @Field(type = FieldType.Long)
    private long lastModifiedOnEpochMs;

    @Field(type = FieldType.Boolean)
    private boolean deleted;
}
