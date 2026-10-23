package com.td.application.sharedcore;

import java.time.Duration;

/**
 * 15/10/2026: kho lưu cache cây cơ cấu tổ chức dùng chung giữa các instance (cài đặt Redis ở td-web,
 * {@code RedisOrganizationTreeCacheStore}) — td-application không phụ thuộc Redis. Xem
 * {@link OrganizationTreeCache}.
 *
 * <p>Mọi method được phép ném exception khi Redis lỗi/mất kết nối; {@link OrganizationTreeCache} tự
 * bắt và chuyển sang chế độ chỉ cache trong JVM.</p>
 */
public interface OrganizationTreeCacheStore {

    /** Phiên bản cây hiện tại (0 nếu chưa có). Tăng mỗi lần tổ chức/người dùng thay đổi. */
    long currentVersion();

    /** Tăng phiên bản (vô hiệu mọi bản cây cũ), trả về phiên bản mới. */
    long bumpVersion();

    byte[] get(String key);

    void put(String key, byte[] value, Duration ttl);

    /** Khoá phân tán (SET NX + TTL). Trả về true nếu giành được khoá với {@code token}. */
    boolean tryLock(String key, String token, Duration ttl);

    /** Nhả khoá chỉ khi vẫn đang giữ bởi {@code token}. */
    void unlock(String key, String token);
}
