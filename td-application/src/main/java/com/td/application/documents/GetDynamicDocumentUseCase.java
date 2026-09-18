package com.td.application.documents;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BL-101 (Dynamic Data Engine): đọc field (tĩnh + động) theo {@code items}, tương đương
 * {@code ShowItems(doc, showItems)} phía Domino ({@code qlvb/get}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetDynamicDocumentUseCase {

    private final DocumentRepository documentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<Map<String, Object>> execute(GetDynamicDocumentRequest request) {
        if (request == null || request.getId() == null) {
            return Result.failure("Document ID is required");
        }

        var documentOptional = documentRepository.findById(request.getId());
        if (documentOptional.isEmpty()) {
            return Result.failure("Document not found with ID: " + request.getId());
        }

        var document = documentOptional.get();
        if (document.isDeleted()) {
            return Result.failure("Document was deleted");
        }

        Map<String, Object> allFields = new LinkedHashMap<>();
        allFields.put("id", document.getId());
        allFields.put("title", document.getTitle());
        // Bugfix 24/08/2026 (nguoi dung phat hien): "document_type" la field NGHIEP VU thuc su
        // ("Loai van ban" - Cong van/Bao cao/..., xem docs/migration-backlog/04-migration-status.md
        // muc "tiep, 7 mo rong 2"), khac hoan toan module discriminator noi bo cua entity (hardcode
        // "vbden"/"vbdi"/... trong tung use case). Doi ten key sang "databasetype" (dung dung thuat
        // ngu Domino - qlvb/add?databasetype=vbden) de KHONG con dung ten voi field nghiep vu nua.
        allFields.put("databasetype", document.getDocumentType());
        allFields.put("status", document.getStatus());
        allFields.put("content", document.getContent());
        allFields.put("created_on", document.getCreatedOn());
        allFields.put("last_modified_on", document.getLastModifiedOn());
        allFields.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));

        Map<String, Object> filtered = dynamicDocumentFieldsService.filterItems(allFields, request.getItems());
        return Result.success(filtered);
    }
}
