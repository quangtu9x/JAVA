package com.td.worker.search;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 17/10/2026 — JDBC cho làn "trích nội dung tệp" (docs/architecture/14-trich-noi-dung-tep.md) trên DB tenant: dòng
 * hàng đợi {@code entity_type='file'} (trigger file_metadata, migration V1.0.74), bảng {@code file_content_extracts}.
 */
public class FileExtractStore {

    public static final String ENTITY_FILE = "file";

    public record LeasedFile(long queueId, UUID fileId, int attempts) {
    }

    public record FileRow(UUID id, UUID documentId, String filePath, String bucket, String filename,
                          String contentType, long size, boolean superseded) {
    }

    /**
     * Thuê tối đa {@code batchSize} tệp đến hạn: đẩy {@code next_try_at} ra sau {@code lease} rồi COMMIT ngay (không giữ
     * transaction trong lúc trích). Xử lý xong thì xoá dòng; worker chết giữa chừng → hết hạn thuê → thử lại.
     */
    public List<LeasedFile> lease(Connection connection, int batchSize, Duration lease) throws SQLException {
        String sql = "UPDATE search_index_queue q SET next_try_at = now() + make_interval(secs => ?) "
            + "WHERE q.id IN (SELECT id FROM search_index_queue WHERE entity_type = ? AND next_try_at <= now() "
            + "ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED) RETURNING q.id, q.entity_id, q.attempts";
        List<LeasedFile> leased = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setDouble(1, lease.toSeconds());
            ps.setString(2, ENTITY_FILE);
            ps.setInt(3, batchSize);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    leased.add(new LeasedFile(rs.getLong(1), (UUID) rs.getObject(2), rs.getInt(3)));
                }
            }
        }
        return leased;
    }

    public Optional<FileRow> loadFile(Connection connection, UUID fileId) throws SQLException {
        String sql = "SELECT id, document_id, file_path, bucket_name, original_filename, content_type, file_size, "
            + "superseded_at IS NOT NULL FROM file_metadata WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, fileId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new FileRow((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3),
                    rs.getString(4), rs.getString(5), rs.getString(6), rs.getLong(7), rs.getBoolean(8)));
            }
        }
    }

    public void upsert(Connection connection, UUID fileId, UUID documentId, FileContentExtractor.Result result)
            throws SQLException {
        String sql = "INSERT INTO file_content_extracts (file_id, document_id, status, content, content_chars, truncated, "
            + "ocr_used, content_type, extractor, error, extracted_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now()) "
            + "ON CONFLICT (file_id) DO UPDATE SET document_id = EXCLUDED.document_id, status = EXCLUDED.status, "
            + "content = EXCLUDED.content, content_chars = EXCLUDED.content_chars, truncated = EXCLUDED.truncated, "
            + "ocr_used = EXCLUDED.ocr_used, content_type = EXCLUDED.content_type, extractor = EXCLUDED.extractor, "
            + "error = EXCLUDED.error, extracted_at = now()";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, fileId);
            ps.setObject(2, documentId);
            ps.setString(3, result.status());
            ps.setString(4, result.content());
            ps.setInt(5, result.content() == null ? 0 : result.content().length());
            ps.setBoolean(6, result.truncated());
            ps.setBoolean(7, false); // ocr_used: chưa có OCR (17/10/2026) — giữ cột cho lúc tích hợp OCR
            ps.setString(8, abbreviate(result.contentType(), 255));
            ps.setString(9, FileContentExtractor.EXTRACTOR);
            ps.setString(10, abbreviate(result.error(), 2000));
            ps.executeUpdate();
        }
    }

    /** Xoá kết quả trích của tệp đã bị xoá; trả document_id cũ (để nạp lại văn bản cha). */
    public Optional<UUID> deleteExtract(Connection connection, UUID fileId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM file_content_extracts WHERE file_id = ? RETURNING document_id")) {
            ps.setObject(1, fileId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable((UUID) rs.getObject(1)) : Optional.empty();
            }
        }
    }

    /** document_id cũ trong kết quả trích (tệp chuyển sang văn bản khác → nạp lại cả văn bản cũ). */
    public Optional<UUID> extractDocumentId(Connection connection, UUID fileId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT document_id FROM file_content_extracts WHERE file_id = ?")) {
            ps.setObject(1, fileId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable((UUID) rs.getObject(1)) : Optional.empty();
            }
        }
    }

    /**
     * Đưa văn bản cha vào hàng đợi đồng bộ ES (để nạp lại kèm nội dung tệp). document_id không có loại → dò lần lượt
     * các bảng đã khai (id UUID duy nhất giữa các bảng). Trả entity_type tìm được.
     */
    public Optional<String> enqueueParent(Connection connection, UUID documentId, Map<String, String> entities)
            throws SQLException {
        if (documentId == null) {
            return Optional.empty();
        }
        for (Map.Entry<String, String> entity : entities.entrySet()) {
            String table = SearchIndexNaming.requireSqlIdentifier(entity.getValue());
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO search_index_queue (entity_type, entity_id, operation) "
                        + "SELECT ?, t.id, 'U' FROM " + table + " t WHERE t.id = ? LIMIT 1")) {
                ps.setString(1, entity.getKey());
                ps.setObject(2, documentId);
                if (ps.executeUpdate() > 0) {
                    return Optional.of(entity.getKey());
                }
            }
        }
        return Optional.empty();
    }

    /** Tệp chưa có kết quả trích và chưa nằm trong hàng đợi → đưa vào (bật tính năng lần đầu / bù). */
    public int enqueueMissing(Connection connection) throws SQLException {
        String sql = "INSERT INTO search_index_queue (entity_type, entity_id, operation) "
            + "SELECT ?, f.id, 'R' FROM file_metadata f "
            + "WHERE NOT EXISTS (SELECT 1 FROM file_content_extracts e WHERE e.file_id = f.id) "
            + "AND NOT EXISTS (SELECT 1 FROM search_index_queue q WHERE q.entity_type = ? AND q.entity_id = f.id)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ENTITY_FILE);
            ps.setString(2, ENTITY_FILE);
            return ps.executeUpdate();
        }
    }

    /** Bảng file_content_extracts đã có (tenant đã chạy migration V1.0.74) — kiểm tra TRƯỚC để không làm hỏng transaction. */
    public boolean extractsTableExists(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT to_regclass('file_content_extracts') IS NOT NULL");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getBoolean(1);
        }
    }

    public Instant nextDueAt(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT min(next_try_at) FROM search_index_queue WHERE entity_type = ?")) {
            ps.setString(1, ENTITY_FILE);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                Timestamp ts = rs.getTimestamp(1);
                return ts == null ? null : ts.toInstant();
            }
        }
    }

    /**
     * Nội dung tệp (DONE, tệp chưa bị thay thế) của các văn bản — gộp "tên tệp: nội dung", tối đa {@code maxChars}
     * ký tự / văn bản, để đưa vào trường files_text trên Elasticsearch.
     */
    public Map<UUID, String> filesText(Connection connection, Collection<UUID> documentIds, int maxChars) throws SQLException {
        Map<UUID, StringBuilder> texts = new LinkedHashMap<>();
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        String sql = "SELECT e.document_id, f.original_filename, e.content FROM file_content_extracts e "
            + "JOIN file_metadata f ON f.id = e.file_id "
            + "WHERE e.document_id = ANY(?) AND e.status = 'DONE' AND f.superseded_at IS NULL "
            + "ORDER BY e.document_id, f.uploaded_at, f.id";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setArray(1, connection.createArrayOf("uuid", documentIds.toArray()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StringBuilder text = texts.computeIfAbsent((UUID) rs.getObject(1), k -> new StringBuilder());
                    if (text.length() >= maxChars) {
                        continue;
                    }
                    String part = (rs.getString(2) == null ? "" : rs.getString(2) + ": ") + rs.getString(3);
                    if (!text.isEmpty()) {
                        text.append("\n\n");
                    }
                    text.append(part, 0, Math.min(part.length(), Math.max(0, maxChars - text.length())));
                }
            }
        }
        Map<UUID, String> result = new LinkedHashMap<>();
        texts.forEach((id, text) -> result.put(id, text.toString()));
        return result;
    }

    private static String abbreviate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
