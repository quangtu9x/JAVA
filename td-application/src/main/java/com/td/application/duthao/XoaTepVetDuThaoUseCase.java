package com.td.application.duthao;

import com.td.application.common.models.Result;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.FileDto;
import com.td.application.documents.ListDocumentFilesByProcessUseCase;
import com.td.application.documents.ListFilesByProcessRequest;
import com.td.application.documents.SupersedeDocumentFilesUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 03/10/2026 (cutover nút "Xoá tệp" trên tệp đính kèm của 1 dòng "Quá trình dự thảo" —
 * {@code iconlistduthao.jsx#removeFileDetail}, thay {@code qlvb/add?databasetype=duthao_res} (ghi
 * {@code file_xoa}) + {@code qlvb/removefile}).
 *
 * <ul>
 *   <li>Chỉ NGƯỜI TẠO dòng được xoá ({@link DraftProcessOwnership}).</li>
 *   <li>Tệp phải đúng thuộc dòng đó: lưu theo {@code document_id} = dự thảo cha ({@code parentid} của
 *   dòng) + {@code process_id} = id dòng (xem {@code GanKemTepDuThaoUseCase}).</li>
 *   <li>Xoá MỀM (quy ước chung của dự án): đánh dấu "đã thay thế" qua
 *   {@link SupersedeDocumentFilesUseCase} — tệp bị ẩn khỏi mọi truy vấn nhưng không mất dữ liệu.</li>
 *   <li>Ghi thêm 1 dòng vào {@code file_xoa} của dòng quá trình, đúng định dạng Domino:
 *   {@code "<tài khoản> đã xóa file <tên> vào lúc dd/MM/yyyy HH:mm:ss"}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class XoaTepVetDuThaoUseCase {

    private final DraftProcessOwnership draftProcessOwnership;
    private final DraftProcessDocumentRepository draftProcessDocumentRepository;
    private final ListDocumentFilesByProcessUseCase listDocumentFilesByProcessUseCase;
    private final SupersedeDocumentFilesUseCase supersedeDocumentFilesUseCase;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;

    public Result<UUID> execute(XoaTepVetDuThaoRequest request) {
        if (request == null || request.getFileId() == null) {
            return Result.failure("Thiếu tệp cần xoá");
        }
        var owned = draftProcessOwnership.loadOwned(request.getProcessId());
        if (!owned.isSuccess()) {
            return Result.failure(owned.getError());
        }
        var process = owned.getData().process();
        Map<String, Object> before = owned.getData().fields();
        UUID draftId = parseUuid(before.get("parentid"));
        if (draftId == null) {
            return Result.failure("Dòng quá trình dự thảo không gắn với dự thảo nào");
        }

        List<FileDto> processFiles = listDocumentFilesByProcessUseCase.execute(
            ListFilesByProcessRequest.builder().documentId(draftId).processId(process.getId()).build()).getItems();
        FileDto file = processFiles.stream()
            .filter(item -> request.getFileId().equals(item.getFileId()))
            .findFirst()
            .orElse(null);
        if (file == null) {
            return Result.failure("Tệp không thuộc dòng quá trình dự thảo này");
        }

        supersedeDocumentFilesUseCase.execute(draftId, List.of(file.getFileId()), process.getId());

        String username = owned.getData().username();
        String entry = username + " đã xóa file " + file.getOriginalFileName() + " vào lúc "
            + LocalDateTime.now().format(XoaVetDuThaoUseCase.TIME_FORMATTER);
        Object oldLog = before.get("file_xoa");
        Map<String, Object> after = new LinkedHashMap<>(before);
        after.put("file_xoa", oldLog == null || String.valueOf(oldLog).isBlank() ? entry : oldLog + "\n" + entry);
        process.replaceDynamicFields(XoaVetDuThaoUseCase.toJson(after));
        var saved = draftProcessDocumentRepository.save(process);

        documentCacheService.evict(draftId);
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, after);
        log.info("Xoa tep {} cua vet qua trinh du thao {} boi {}", file.getFileId(), saved.getId(), username);
        return Result.success(file.getFileId());
    }

    private UUID parseUuid(Object value) {
        try {
            return value == null ? null : UUID.fromString(String.valueOf(value).trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
