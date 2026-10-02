package com.td.application.hosocv;

import com.td.application.common.models.Result;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.FileDto;
import com.td.application.documents.ListDocumentFilesUseCase;
import com.td.domain.hosocv.HoSoCVDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 02/10/2026 (cutover tab "VĂN BẢN LIÊN QUAN" Hồ sơ công việc): đọc 1 "văn bản khác" cho modal
 * {@code VBKhacModal.jsx}, thay {@code qlvb/get?databasetype=hosocv}. Trả toàn bộ field động + tệp
 * đính kèm ({@code files}, từ {@code file_metadata}) — xem {@link LuuVanBanKhacUseCase}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class XemVanBanKhacUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản khác";

    private final HoSoCVDocumentRepository hoSoCVDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final ListDocumentFilesUseCase listDocumentFilesUseCase;

    public Result<Map<String, Object>> execute(UUID id) {
        if (id == null) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        return hoSoCVDocumentRepository.findById(id)
            .filter(document -> !document.isDeleted()
                && LuuVanBanKhacUseCase.DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType()))
            .map(this::toResponse)
            .map(Result::success)
            .orElseGet(() -> Result.failure(NOT_FOUND_MESSAGE));
    }

    private Map<String, Object> toResponse(HoSoCVDocument document) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", document.getId());
        data.put("title", document.getTitle());
        data.put("databasetype", document.getDocumentType());
        data.put("created_on", document.getCreatedOn());
        data.put("last_modified_on", document.getLastModifiedOn());
        data.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        // Cung shape {name,size,type,file_id} voi cot "files" o danh sach (ViewQueryService#toFileMap) -
        // ListIconFiles/Files tai tep Java theo file_id.
        List<Map<String, Object>> files = new ArrayList<>();
        for (FileDto file : listDocumentFilesUseCase.execute(document.getId()).getItems()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", file.getOriginalFileName());
            item.put("size", file.getFileSize());
            item.put("type", extensionOf(file.getOriginalFileName()));
            item.put("file_id", file.getFileId());
            files.add(item);
        }
        data.put("files", files);
        return data;
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1).toLowerCase() : "";
    }
}
