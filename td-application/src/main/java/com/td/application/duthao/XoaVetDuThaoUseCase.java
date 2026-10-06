package com.td.application.duthao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.sharedcore.UserFullNameResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 03/10/2026 (cutover nút xoá dòng ở "Quá trình dự thảo" — {@code duthaofile.jsx#handleRemove}, thay
 * {@code qlvb/add?databasetype=duthao_res&id=...} với {@code delete=1}): đúng hành vi Domino — KHÔNG
 * xoá bản ghi, chỉ ghi cờ {@code delete="1"} + {@code delete_user}/{@code delete_name}/
 * {@code delele_time} (giữ nguyên key sai chính tả "delele" vì FE hiển thị "X xóa lúc ..." đọc đúng
 * key đó). Chỉ NGƯỜI TẠO dòng được xoá ({@link DraftProcessOwnership}); xoá lại dòng đã xoá = thành
 * công, không ghi đè thời điểm cũ.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class XoaVetDuThaoUseCase {

    static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DraftProcessOwnership draftProcessOwnership;
    private final DraftProcessDocumentRepository draftProcessDocumentRepository;
    private final UserFullNameResolver userFullNameResolver;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;

    public Result<UUID> execute(UUID processId) {
        var owned = draftProcessOwnership.loadOwned(processId);
        if (!owned.isSuccess()) {
            return Result.failure(owned.getError());
        }
        var process = owned.getData().process();
        Map<String, Object> before = owned.getData().fields();
        if ("1".equals(String.valueOf(before.get("delete")))) {
            return Result.success(process.getId());
        }

        String username = owned.getData().username();
        Map<String, Object> after = new LinkedHashMap<>(before);
        after.put("delete", "1");
        after.put("delete_user", username);
        after.put("delete_name", userFullNameResolver.resolveForWrite(username, null));
        after.put("delele_time", LocalDateTime.now().format(TIME_FORMATTER));
        process.replaceDynamicFields(toJson(after));

        var saved = draftProcessDocumentRepository.save(process);
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, after);
        log.info("Xoa vet qua trinh du thao {} boi {}", saved.getId(), username);
        return Result.success(saved.getId());
    }

    static String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
