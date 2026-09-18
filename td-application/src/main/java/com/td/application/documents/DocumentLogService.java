package com.td.application.documents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.security.CurrentUserProvider;
import com.td.domain.documents.DocumentLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * BL-103 (Audit Log): ghi log append-only cho document — {@code {documentId, logType, logData
 * (JSONB), createdBy, createdAt}}. Lỗi ghi log không được làm hỏng thao tác nghiệp vụ chính (chỉ
 * log lỗi nội bộ), vì audit trail là thứ yếu so với chính thao tác CRUD.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentLogService {

    public static final String LOG_TYPE_CREATE = "create";
    public static final String LOG_TYPE_UPDATE = "update";
    public static final String LOG_TYPE_DELETE = "delete";
    public static final String LOG_TYPE_HARD_DELETE = "hard_delete";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DocumentLogRepository documentLogRepository;
    private final CurrentUserProvider currentUserProvider;

    public void logCreate(UUID documentId, Object newValue) {
        append(documentId, LOG_TYPE_CREATE, Map.of("newValue", newValue));
    }

    public void logUpdate(UUID documentId, Object oldValue, Object newValue) {
        append(documentId, LOG_TYPE_UPDATE, Map.of("oldValue", oldValue, "newValue", newValue));
    }

    public void logDelete(UUID documentId, Object oldValue) {
        append(documentId, LOG_TYPE_DELETE, Map.of("oldValue", oldValue));
    }

    public void logHardDelete(UUID documentId, Object oldValue) {
        append(documentId, LOG_TYPE_HARD_DELETE, Map.of("oldValue", oldValue));
    }

    private void append(UUID documentId, String logType, Map<String, Object> logData) {
        try {
            String createdBy = currentUserProvider.currentUsername().orElse("system");
            String logDataJson = OBJECT_MAPPER.writeValueAsString(logData);
            documentLogRepository.save(new DocumentLog(documentId, logType, logDataJson, createdBy));
        } catch (Exception ex) {
            log.error("Ghi audit log that bai cho document {} (logType={}): {}", documentId, logType, ex.getMessage(), ex);
        }
    }
}
