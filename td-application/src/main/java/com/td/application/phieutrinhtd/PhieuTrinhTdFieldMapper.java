package com.td.application.phieutrinhtd;

import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.ListDocumentFilesUseCase;
import com.td.application.documents.FileDto;
import com.td.domain.phieutrinhtd.PhieuTrinhTdDocument;
import com.td.domain.phieutrinhtd.PhieuTrinhTdProcessDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 06/10/2026 (cutover FE phiếu trình) - dựng Map trả về cho FE (chi tiết, danh sách, quá trình / ý kiến)
 * theo đúng khuôn field Domino FE đang đọc + danh sách tệp Java ({@code files}: {name, size, type,
 * file_id} - cùng khuôn {@code ViewQueryService#toFileMap}, component Files/index.jsx đọc).
 */
@Component
@RequiredArgsConstructor
public class PhieuTrinhTdFieldMapper {

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final ListDocumentFilesUseCase listDocumentFilesUseCase;

    public Map<String, Object> toDocumentMap(PhieuTrinhTdDocument document, boolean includeFiles) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", document.getId());
        fields.put("title", document.getTitle());
        fields.put("databasetype", "phieutrinhtd");
        fields.put("status", document.getStatus());
        fields.put("created_on", document.getCreatedOn());
        fields.put("last_modified_on", document.getLastModifiedOn());
        fields.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        // status là cột tĩnh - giá trị trong dynamic_fields (nếu còn sót) không được ghi đè.
        fields.put("status", document.getStatus());
        // Domino đổi Form sang "submission_dossier_Deleted" khi xoá - FE dựa vào đó hiện nút "Phục hồi".
        if (document.isDeleted()) {
            fields.put("form", "submission_dossier_Deleted");
        }
        if (includeFiles) {
            fields.put("files", files(document.getId()));
        }
        return fields;
    }

    public Map<String, Object> toProcessMap(PhieuTrinhTdProcessDocument process, boolean includeFiles) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", process.getId());
        fields.put("created_on", process.getCreatedOn());
        fields.putAll(dynamicDocumentFieldsService.parseDynamicFields(process.getDynamicFieldsJson()));
        if (includeFiles) {
            fields.put("files", files(process.getId()));
        }
        return fields;
    }

    public Map<String, Object> filter(Map<String, Object> fields, String items) {
        return dynamicDocumentFieldsService.filterItems(fields, items);
    }

    public List<Map<String, Object>> files(java.util.UUID documentId) {
        List<Map<String, Object>> files = new ArrayList<>();
        for (FileDto dto : listDocumentFilesUseCase.execute(documentId).getItems()) {
            Map<String, Object> file = new LinkedHashMap<>();
            file.put("name", dto.getOriginalFileName());
            file.put("size", dto.getFileSize());
            String name = dto.getOriginalFileName() == null ? "" : dto.getOriginalFileName();
            int dot = name.lastIndexOf('.');
            file.put("type", dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1).toLowerCase() : "");
            file.put("file_id", dto.getFileId());
            files.add(file);
        }
        return files;
    }
}
