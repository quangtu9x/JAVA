package com.td.infrastructure.persistence.repository;

import com.td.application.documents.FileAccessLogRepository;
import com.td.domain.documents.FileAccessLog;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 09/09/2026 (2): adapter cho {@code file_access_logs} — {@code recordAccess} UPSERT bằng
 * {@code INSERT ... ON CONFLICT DO UPDATE ... RETURNING id}, 1 câu SQL nguyên tử duy nhất (đúng
 * tiền lệ {@code PostgresNumberingCounterRepository}, row lock của Postgres đảm bảo an toàn dưới
 * concurrent access — vd 2 tab cùng xem 1 tệp) — không cần SELECT-rồi-UPDATE 2 bước.
 *
 * <p><b>Merge khoá {@code "actions"} bên trong {@code dynamic_fields} (JSONB) NGAY trong UPSERT</b>:
 * khoá xung đột đã bỏ {@code action} (chỉ còn {@code module, document_id, file_id, username}) — 1
 * người dùng xem RỒI tải cùng 1 tệp vẫn là 1 dòng, {@code dynamic_fields->'actions'} là mảng JSON
 * các loại thao tác đã xảy ra (DÙNG LẠI đúng cột {@code dynamic_fields} chung, KHÔNG phải cột riêng
 * "actions" — xem javadoc {@link com.td.domain.documents.FileAccessLog}). {@code jsonb_set} ghi lại
 * ĐÚNG khoá {@code "actions"}, giữ nguyên mọi khoá khác (nếu sau này thêm field biến thiên khác);
 * toán tử {@code @>} kiểm tra "đã có action này trong mảng chưa", {@code ||} nối mảng nếu chưa có —
 * cả hai chạy ngay trong câu {@code DO UPDATE}, không cần đọc dòng cũ ra ứng dụng rồi ghi lại (tránh
 * thêm round-trip + race condition khi 2 request cùng lúc thêm 2 action khác nhau).</p>
 */
@Repository
public class PostgresFileAccessLogRepository implements FileAccessLogRepository {

    private static final String UPSERT_AND_RETURN_ID_SQL = """
        INSERT INTO file_access_logs
            (id, module, document_id, file_id, file_name, dynamic_fields, username, fullname, unit,
             first_accessed_on, last_accessed_on, access_count)
        VALUES
            (uuid_generate_v4(), :module, :documentId, :fileId, :fileName,
             jsonb_build_object('actions', jsonb_build_array(CAST(:action AS text))),
             :username, :fullname, :unit, NOW(), NOW(), 1)
        ON CONFLICT (module, document_id, file_id, username)
        DO UPDATE SET
            last_accessed_on = NOW(),
            access_count = file_access_logs.access_count + 1,
            file_name = EXCLUDED.file_name,
            fullname = EXCLUDED.fullname,
            unit = EXCLUDED.unit,
            dynamic_fields = jsonb_set(
                file_access_logs.dynamic_fields,
                '{actions}',
                CASE
                    WHEN COALESCE(file_access_logs.dynamic_fields->'actions', '[]'::jsonb)
                        @> jsonb_build_array(CAST(:action AS text))
                        THEN COALESCE(file_access_logs.dynamic_fields->'actions', '[]'::jsonb)
                    ELSE COALESCE(file_access_logs.dynamic_fields->'actions', '[]'::jsonb)
                        || jsonb_build_array(CAST(:action AS text))
                END,
                true
            )
        RETURNING id
        """;

    @PersistenceContext
    private EntityManager entityManager;

    private final FileAccessLogJpaRepository jpaRepository;

    public PostgresFileAccessLogRepository(FileAccessLogJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public UUID recordAccess(String module, UUID documentId, UUID fileId, String fileName, String action,
            String username, String fullname, String unit) {
        Query query = entityManager.createNativeQuery(UPSERT_AND_RETURN_ID_SQL);
        query.setParameter("module", module);
        query.setParameter("documentId", documentId);
        query.setParameter("fileId", fileId);
        query.setParameter("fileName", fileName);
        query.setParameter("action", action);
        query.setParameter("username", username);
        query.setParameter("fullname", fullname);
        query.setParameter("unit", unit);
        return (UUID) query.getSingleResult();
    }

    @Override
    public Page<FileAccessLog> findByModuleAndDocumentId(String module, UUID documentId, Pageable pageable) {
        return jpaRepository.findByModuleAndDocumentIdOrderByLastAccessedOnDesc(module, documentId, pageable);
    }
}
