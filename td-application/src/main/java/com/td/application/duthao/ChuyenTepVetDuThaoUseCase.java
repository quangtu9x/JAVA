package com.td.application.duthao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.FileDto;
import com.td.application.documents.ListDocumentFilesByProcessUseCase;
import com.td.application.documents.ListFilesByProcessRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 08/10/2026 (cutover FE "Quá trình dự thảo" -> nút "Tệp ban hành"/"Tệp kèm theo"): thay Domino
 * {@code qlvb/movefile?databasetype=duthao} ({@code TDAttachment#MoveAttachment}: chép tệp từ document
 * quá trình sang kho tệp dự thảo + thêm tên tệp vào trường {@code fieldname} của dự thảo, loại trùng).
 *
 * <p>Ở Java tệp của dòng quá trình ĐÃ nằm dưới {@code document_id} = id dự thảo (kèm {@code process_id}),
 * và danh sách "Tệp ban hành/Tệp kèm theo" xác định theo TÊN trong {@code tepbanhanh}/{@code tepkemtheo}
 * (cùng cách luồng lưu tệp đã ký {@code uploadSignedFileJava} và {@code ChuyenThanhVanBanDiUseCase} chép
 * tệp theo tên) — nên KHÔNG cần chép tệp, chỉ cần kiểm tra tệp đúng là của dòng quá trình rồi thêm tên
 * vào trường đích của dự thảo.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ChuyenTepVetDuThaoUseCase {

    static final Set<String> ALLOWED_FIELDS = Set.of("tepbanhanh", "tepkemtheo", "tepphieutrinh");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DraftProcessDocumentRepository draftProcessDocumentRepository;
    private final DuThaoDocumentRepository duThaoDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final ListDocumentFilesByProcessUseCase listDocumentFilesByProcessUseCase;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;

    public Result<String> execute(ChuyenTepVetDuThaoRequest request) {
        if (request == null || request.getProcessId() == null || request.getFileId() == null) {
            return Result.failure("Thiếu tệp cần chuyển");
        }
        String fieldname = request.getFieldname() == null ? "" : request.getFieldname().trim().toLowerCase();
        if (!ALLOWED_FIELDS.contains(fieldname)) {
            return Result.failure("Trường đích không hợp lệ: " + request.getFieldname());
        }
        var process = draftProcessDocumentRepository.findById(request.getProcessId()).orElse(null);
        if (process == null || process.isDeleted()) {
            return Result.failure("Không tìm thấy dòng quá trình dự thảo");
        }
        Map<String, Object> processFields = dynamicDocumentFieldsService.parseDynamicFields(process.getDynamicFieldsJson());
        UUID draftId = parseUuid(processFields.get("parentid"));
        var draft = draftId == null ? null : duThaoDocumentRepository.findById(draftId).orElse(null);
        if (draft == null || draft.isDeleted()) {
            return Result.failure("Dòng quá trình dự thảo không gắn với dự thảo nào");
        }

        List<FileDto> processFiles = listDocumentFilesByProcessUseCase.execute(
            ListFilesByProcessRequest.builder().documentId(draftId).processId(process.getId()).build()).getItems();
        FileDto file = processFiles.stream()
            .filter(item -> request.getFileId().equals(item.getFileId()))
            .findFirst()
            .orElse(null);
        if (file == null || file.getOriginalFileName() == null || file.getOriginalFileName().isBlank()) {
            return Result.failure("Tệp không thuộc dòng quá trình dự thảo này");
        }
        String fileName = file.getOriginalFileName().trim();

        Map<String, Object> before = dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson());
        Set<String> names = splitNames(before.get(fieldname));
        if (!names.add(fileName)) {
            return Result.success(fileName); // đã có - giống Domino (Set loại trùng), không ghi lại
        }
        Map<String, Object> after = new LinkedHashMap<>(before);
        after.put(fieldname, String.join(";", names) + ";");
        draft.replaceDynamicFields(toJson(after));
        var saved = duThaoDocumentRepository.save(draft);

        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, after);
        log.info("Chuyen tep '{}' cua vet qua trinh {} vao {} cua du thao {}", fileName, process.getId(), fieldname, draftId);
        return Result.success(fileName);
    }

    // "a.pdf;b.pdf;" (quy ước FE) hoặc mảng — giữ thứ tự, bỏ phần tử rỗng.
    static Set<String> splitNames(Object value) {
        Set<String> names = new LinkedHashSet<>();
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                addName(names, item);
            }
        } else if (value != null) {
            for (String part : String.valueOf(value).split(";")) {
                addName(names, part);
            }
        }
        return names;
    }

    private static void addName(Set<String> names, Object item) {
        String text = item == null ? "" : String.valueOf(item).trim();
        if (!text.isEmpty()) {
            names.add(text);
        }
    }

    private UUID parseUuid(Object value) {
        try {
            return value == null ? null : UUID.fromString(String.valueOf(value).trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Khong the ghi field dong du thao", ex);
        }
    }
}
