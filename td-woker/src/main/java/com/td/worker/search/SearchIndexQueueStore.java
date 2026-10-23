package com.td.worker.search;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Truy cập bảng {@code search_index_queue} (migration td-web V1.0.72) và bảng dữ liệu trên DB TENANT.
 * Mọi method nhận {@link Connection} do người gọi quản lý transaction. Tên bảng ghép vào SQL đã được
 * kiểm tra bằng {@link SearchIndexNaming#requireSqlIdentifier}.
 */
public class SearchIndexQueueStore {

    public record QueueItem(long id, String entityType, UUID entityId, int attempts) {
    }

    /**
     * Lấy lô đến hạn và KHOÁ các dòng (worker khác bỏ qua nhờ SKIP LOCKED — chạy nhiều instance an toàn). Bỏ qua dòng
     * {@code entity_type='file'} — làn trích nội dung tệp riêng xử lý ({@link FileExtractService}).
     */
    public List<QueueItem> claim(Connection connection, int batchSize) throws SQLException {
        String sql = "SELECT id, entity_type, entity_id, attempts FROM search_index_queue "
            + "WHERE next_try_at <= now() AND entity_type <> 'file' ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED";
        List<QueueItem> items = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, batchSize);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new QueueItem(rs.getLong(1), rs.getString(2), (UUID) rs.getObject(3), rs.getInt(4)));
                }
            }
        }
        return items;
    }

    /** Trạng thái HIỆN TẠI của các dòng (JSON từ to_jsonb); id không có trong kết quả = dòng đã bị xoá. */
    public Map<UUID, String> loadRows(Connection connection, String table, Collection<UUID> ids) throws SQLException {
        String sql = "SELECT t.id, to_jsonb(t)::text FROM " + SearchIndexNaming.requireSqlIdentifier(table)
            + " t WHERE t.id = ANY(?)";
        Map<UUID, String> rows = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setArray(1, uuidArray(connection, ids));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.put((UUID) rs.getObject(1), rs.getString(2));
                }
            }
        }
        return rows;
    }

    /** Kiểu dữ liệu từng cột (udt_name: text, varchar, jsonb, timestamptz, int4, bool, uuid, _text...). */
    public Map<String, String> columnTypes(Connection connection, String table) throws SQLException {
        String sql = "SELECT column_name, udt_name FROM information_schema.columns "
            + "WHERE table_schema = current_schema() AND table_name = ? ORDER BY ordinal_position";
        Map<String, String> types = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, SearchIndexNaming.requireSqlIdentifier(table));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    types.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        return types;
    }

    public void delete(Connection connection, Collection<Long> queueIds) throws SQLException {
        if (queueIds.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM search_index_queue WHERE id = ANY(?)")) {
            ps.setArray(1, connection.createArrayOf("bigint", queueIds.toArray()));
            ps.executeUpdate();
        }
    }

    /** Lỗi theo dòng: tăng attempts, lùi lịch theo cấp số nhân (trần retryMaxBackoff). Không bao giờ bỏ dòng. */
    public void markRetry(Connection connection, Collection<Long> queueIds, String error,
                          Duration initialBackoff, Duration maxBackoff) throws SQLException {
        if (queueIds.isEmpty()) {
            return;
        }
        String sql = "UPDATE search_index_queue SET attempts = attempts + 1, last_error = ?, "
            + "next_try_at = now() + make_interval(secs => LEAST(? * power(2, LEAST(attempts, 20)), ?)) "
            + "WHERE id = ANY(?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, error == null ? null : ElasticsearchHttpClient.abbreviate(error));
            ps.setDouble(2, initialBackoff.toSeconds());
            ps.setDouble(3, maxBackoff.toSeconds());
            ps.setArray(4, connection.createArrayOf("bigint", queueIds.toArray()));
            ps.executeUpdate();
        }
    }

    /** Đưa TOÀN BỘ dòng của bảng vào hàng đợi (nạp lần đầu / nạp lại sau đổi mapping / đối soát). */
    public int enqueueAll(Connection connection, String entityType, String table) throws SQLException {
        String sql = "INSERT INTO search_index_queue (entity_type, entity_id, operation) SELECT ?, t.id, 'R' FROM "
            + SearchIndexNaming.requireSqlIdentifier(table) + " t";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, entityType);
            return ps.executeUpdate();
        }
    }

    public int enqueueIds(Connection connection, String entityType, Collection<UUID> ids) throws SQLException {
        if (ids.isEmpty()) {
            return 0;
        }
        String sql = "INSERT INTO search_index_queue (entity_type, entity_id, operation) SELECT ?, x, 'R' FROM unnest(?) AS x";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, entityType);
            ps.setArray(2, uuidArray(connection, ids));
            return ps.executeUpdate();
        }
    }

    public long pending(Connection connection, String entityType) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT count(*) FROM search_index_queue WHERE entity_type = ?")) {
            ps.setString(1, entityType);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** Thời điểm đến hạn sớm nhất trong hàng đợi (null = rỗng) — để hẹn giờ thử lại dòng lỗi. Dùng index next_try_at. */
    public java.time.Instant nextDueAt(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT min(next_try_at) FROM search_index_queue WHERE entity_type <> 'file'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            java.sql.Timestamp ts = rs.getTimestamp(1);
            return ts == null ? null : ts.toInstant();
        }
    }

    /** Tổng hàng đợi và số dòng đang lỗi (attempts > 0) — cho metric/health. */
    public long[] queueStats(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT count(*), count(*) FILTER (WHERE attempts > 0) FROM search_index_queue");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return new long[] {rs.getLong(1), rs.getLong(2)};
        }
    }

    public long countRows(Connection connection, String table) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT count(*) FROM " + SearchIndexNaming.requireSqlIdentifier(table));
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    public Set<UUID> existingIds(Connection connection, String table, Collection<UUID> ids) throws SQLException {
        Set<UUID> existing = new HashSet<>();
        if (ids.isEmpty()) {
            return existing;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT t.id FROM " + SearchIndexNaming.requireSqlIdentifier(table) + " t WHERE t.id = ANY(?)")) {
            ps.setArray(1, uuidArray(connection, ids));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    existing.add((UUID) rs.getObject(1));
                }
            }
        }
        return existing;
    }

    /** Khoá advisory theo transaction — 1 instance làm bootstrap/đối soát cho 1 (tenant, entity) tại 1 thời điểm. */
    public boolean tryAdvisoryLock(Connection connection, String key) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT pg_try_advisory_xact_lock(hashtext(?))")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private static Array uuidArray(Connection connection, Collection<UUID> ids) throws SQLException {
        return connection.createArrayOf("uuid", ids.toArray());
    }
}
