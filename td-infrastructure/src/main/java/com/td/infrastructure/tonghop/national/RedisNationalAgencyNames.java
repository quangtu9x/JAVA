package com.td.infrastructure.tonghop.national;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tra tên cơ quan Trục trên Redis (yêu cầu người dùng 22/10/2026 — danh bạ có thể > 100k): gateway
 * ({@code NationalAgencyRedisCache}) giữ HASH {@code <prefix>:names} mã → tên, ở đây chỉ {@code HMGET}. Redis lỗi →
 * trả rỗng (bên gọi tra SQL) và tạm bỏ qua Redis {@link #BACKOFF} để màn hình không chờ timeout ở mọi request.
 */
@Slf4j
public class RedisNationalAgencyNames {

    static final Duration BACKOFF = Duration.ofSeconds(60);

    private final ObjectProvider<StringRedisTemplate> redis;
    private final String namesKey;
    private volatile long skipUntil;

    public RedisNationalAgencyNames(ObjectProvider<StringRedisTemplate> redis, String keyPrefix) {
        this.redis = redis;
        this.namesKey = keyPrefix + ":names";
    }

    public Map<String, String> lookup(List<String> codes) {
        StringRedisTemplate r = redis.getIfAvailable();
        if (r == null || codes.isEmpty() || System.currentTimeMillis() < skipUntil) {
            return Map.of();
        }
        try {
            List<Object> values = r.opsForHash().multiGet(namesKey, new ArrayList<>(codes));
            Map<String, String> found = new HashMap<>();
            for (int i = 0; i < codes.size() && values != null && i < values.size(); i++) {
                if (values.get(i) != null) {
                    found.put(codes.get(i), values.get(i).toString());
                }
            }
            return found;
        } catch (RuntimeException ex) {
            skipUntil = System.currentTimeMillis() + BACKOFF.toMillis();
            log.warn("Không tra được tên cơ quan Trục trên Redis (tra SQL, bỏ qua Redis {}s): {}",
                BACKOFF.toSeconds(), ex.getMessage());
            return Map.of();
        }
    }
}
