package com.td.application.vbcddh;

import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.FileDto;
import com.td.application.documents.ListDocumentFilesUseCase;
import com.td.domain.vbcddh.VbcddhDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 30/10/2026 (cutover FE vbcddh khỏi Domino qlvb/get|view/query): đổi {@link VbcddhDocument} sang map
 * phẳng đúng tên field Domino FE đang đọc (cùng công thức ViewQueryService#toFilteredDocumentFields)
 * + danh sách tệp Java {@code files} ({name,size,type,file_id} — Files/index.jsx mở theo file_id).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VbcddhFieldMapper {

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final ListDocumentFilesUseCase listDocumentFilesUseCase;

    public Map<String, Object> toMap(VbcddhDocument document, String items, boolean withFiles) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", document.getId());
        fields.put("title", document.getTitle());
        fields.put("databasetype", document.getDocumentType());
        fields.put("status", document.getStatus());
        fields.put("created_on", document.getCreatedOn());
        fields.put("last_modified_on", document.getLastModifiedOn());
        fields.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        if (withFiles && wantsFiles(items)) {
            fields.put("files", files(document.getId()));
        }
        return dynamicDocumentFieldsService.filterItems(fields, items);
    }

    public Map<String, Object> parse(VbcddhDocument document) {
        return new LinkedHashMap<>(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
    }

    private boolean wantsFiles(String items) {
        if (items == null || items.isBlank() || "getall".equalsIgnoreCase(items.trim())) {
            return true;
        }
        for (String item : items.split(";")) {
            if ("files".equalsIgnoreCase(item.trim()) || "getall".equalsIgnoreCase(item.trim())) {
                return true;
            }
        }
        return false;
    }

    public List<Map<String, Object>> files(UUID documentId) {
        List<Map<String, Object>> files = new ArrayList<>();
        try {
            var page = listDocumentFilesUseCase.execute(documentId);
            List<FileDto> dtos = page == null || page.getItems() == null ? List.of() : page.getItems();
            for (FileDto dto : dtos) {
                Map<String, Object> file = new LinkedHashMap<>();
                String name = dto.getOriginalFileName() == null ? "" : dto.getOriginalFileName();
                file.put("name", name);
                file.put("size", dto.getFileSize());
                int dot = name.lastIndexOf('.');
                file.put("type", dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1).toLowerCase() : "");
                file.put("file_id", dto.getFileId());
                files.add(file);
            }
        } catch (Exception ex) {
            log.warn("Không đọc được danh sách tệp vbcddh {}: {}", documentId, ex.getMessage());
        }
        return files;
    }
}
