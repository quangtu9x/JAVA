package com.td.worker.search;

import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LISTEN kênh thông báo ở DB TỪNG tenant (migration td-web V1.0.73: trigger {@code pg_notify} khi COMMIT) và
 * đánh thức {@link SearchSyncCoordinator} cho đúng tenant đó — tenant không có thay đổi không tốn truy vấn nào.
 *
 * <p>Mỗi tenant 1 kết nối RIÊNG (ngoài pool, LISTEN cần giữ phiên) trên 1 luồng nền stack nhỏ, chỉ chờ dữ
 * liệu từ socket (worker build Java 17 nên chưa dùng virtual thread). Kết nối rảnh quá {@code listenerPingInterval} thì gửi {@code SELECT 1} để phát hiện
 * kết nối chết; đứt thì tự nối lại sau {@code listenerReconnectDelay}. Mỗi lần (nối lại) LISTEN xong đều yêu cầu
 * xử lý tenant 1 lần — bù những thay đổi commit trong lúc chưa nghe.</p>
 */
@Slf4j
public class SearchNotificationListener implements AutoCloseable {

    /** Mở kết nối JDBC mới tới DB tenant (không lấy từ pool). */
    @FunctionalInterface
    public interface TenantConnectionFactory {
        Connection open(String tenantId) throws SQLException;
    }

    private final TenantConnectionFactory connections;
    /** Gọi khi tenant có thay đổi (đánh thức làn đồng bộ văn bản + làn trích nội dung tệp). */
    private final java.util.function.Consumer<String> onChange;
    private final String channel;
    private final Duration pingInterval;
    private final Duration reconnectDelay;
    private final Map<String, Thread> listeners = new ConcurrentHashMap<>();
    private final Map<String, Boolean> connected = new ConcurrentHashMap<>();
    // Đóng kết nối khi dừng: interrupt KHÔNG cắt được lệnh đọc socket đang chờ thông báo.
    private final Map<String, Connection> openConnections = new ConcurrentHashMap<>();
    private volatile boolean closed;
    private static final long LISTENER_STACK_BYTES = 256 * 1024;

    public SearchNotificationListener(TenantConnectionFactory connections, java.util.function.Consumer<String> onChange,
                                      String channel, Duration pingInterval, Duration reconnectDelay) {
        this.connections = connections;
        this.onChange = onChange;
        this.channel = SearchIndexNaming.requireSqlIdentifier(channel);
        this.pingInterval = pingInterval;
        this.reconnectDelay = reconnectDelay;
    }

    /** Đồng bộ danh sách tenant đang nghe với {@code tenantIds}: mở cho tenant mới, dừng tenant đã bỏ. */
    public synchronized void listenTo(Collection<String> tenantIds) {
        if (closed) {
            return;
        }
        Set<String> wanted = Set.copyOf(tenantIds);
        for (String tenantId : wanted) {
            if (!listeners.containsKey(tenantId)) {
                // Lưu vào map TRƯỚC khi start: vòng lặp tự kiểm tra mình còn là luồng nghe của tenant.
                // Worker chạy Java 17 (chưa có virtual thread): luồng nền stack nhỏ — chỉ ngủ chờ socket.
                Thread thread = new Thread(null, () -> listenLoop(tenantId), "search-listen-" + tenantId, LISTENER_STACK_BYTES);
                thread.setDaemon(true);
                listeners.put(tenantId, thread);
                thread.start();
            }
        }
        for (String tenantId : Set.copyOf(listeners.keySet())) {
            if (!wanted.contains(tenantId)) {
                Thread thread = listeners.remove(tenantId);
                if (thread != null) {
                    thread.interrupt();
                }
                closeQuietly(openConnections.remove(tenantId));
            }
        }
    }

    public int connectedCount() {
        return (int) connected.values().stream().filter(Boolean::booleanValue).count();
    }

    public int listenerCount() {
        return listeners.size();
    }

    private void listenLoop(String tenantId) {
        boolean warned = false;
        while (!closed && listeners.get(tenantId) == Thread.currentThread()) {
            try (Connection connection = connections.open(tenantId)) {
                openConnections.put(tenantId, connection);
                connection.setAutoCommit(true);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("LISTEN " + channel);
                }
                connected.put(tenantId, Boolean.TRUE);
                if (warned) {
                    log.info("LISTEN {} tenant {} đã nối lại", channel, tenantId);
                    warned = false;
                }
                onChange.accept(tenantId); // bù thay đổi commit trong lúc chưa nghe
                PGConnection pg = connection.unwrap(PGConnection.class);
                while (!closed && !Thread.currentThread().isInterrupted()) {
                    PGNotification[] notifications = pg.getNotifications((int) pingInterval.toMillis());
                    if (notifications != null && notifications.length > 0) {
                        onChange.accept(tenantId);
                    } else {
                        try (Statement ping = connection.createStatement()) {
                            ping.execute("SELECT 1"); // kết nối chết → SQLException → nối lại
                        }
                    }
                }
            } catch (SQLException ex) {
                if (closed || Thread.currentThread().isInterrupted()) {
                    break;
                }
                if (!warned) {
                    log.warn("LISTEN {} tenant {} mất kết nối/không mở được, thử lại sau {}: {}",
                        channel, tenantId, reconnectDelay, ex.getMessage());
                    warned = true;
                }
            } finally {
                connected.put(tenantId, Boolean.FALSE);
                openConnections.remove(tenantId);
            }
            try {
                Thread.sleep(reconnectDelay.toMillis());
            } catch (InterruptedException ex) {
                break;
            }
        }
        connected.remove(tenantId);
    }

    @Override
    public synchronized void close() {
        closed = true;
        listeners.values().forEach(Thread::interrupt);
        listeners.clear();
        openConnections.values().forEach(SearchNotificationListener::closeQuietly);
        openConnections.clear();
    }

    private static void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // đang dừng
            }
        }
    }
}
