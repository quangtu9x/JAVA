package com.td.web.config;

import com.td.application.sharedcore.OrganizationTreeCacheStore;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 15/10/2026: cài đặt Redis cho {@link OrganizationTreeCacheStore} — cache cây cơ cấu tổ chức (xem
 * {@code OrganizationTreeCache}). Dữ liệu cây là byte[] gzip, nên dùng RedisTemplate byte[] riêng thay vì
 * {@code RedisCacheManager} (serializer JSON có default typing, không hợp với khối dữ liệu lớn).
 *
 * <p>Khoá: {@code orgtree:version} (INCR), {@code orgtree:tree:v{n}:{variant}}, {@code orgtree:lock:v{n}:{variant}}.</p>
 */
@Component
public class RedisOrganizationTreeCacheStore implements OrganizationTreeCacheStore {

    private static final String PREFIX = "orgtree:";
    private static final String VERSION_KEY = PREFIX + "version";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
        Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, byte[]> bytesRedisTemplate;

    public RedisOrganizationTreeCacheStore(RedisConnectionFactory connectionFactory) {
        this.stringRedisTemplate = new StringRedisTemplate(connectionFactory);
        this.bytesRedisTemplate = new RedisTemplate<>();
        this.bytesRedisTemplate.setConnectionFactory(connectionFactory);
        this.bytesRedisTemplate.setKeySerializer(RedisSerializer.string());
        this.bytesRedisTemplate.setValueSerializer(RedisSerializer.byteArray());
        this.bytesRedisTemplate.afterPropertiesSet();
    }

    @Override
    public long currentVersion() {
        String value = stringRedisTemplate.opsForValue().get(VERSION_KEY);
        return value == null ? 0L : Long.parseLong(value);
    }

    @Override
    public long bumpVersion() {
        Long value = stringRedisTemplate.opsForValue().increment(VERSION_KEY);
        return value == null ? 0L : value;
    }

    @Override
    public byte[] get(String key) {
        return bytesRedisTemplate.opsForValue().get(PREFIX + key);
    }

    @Override
    public void put(String key, byte[] value, Duration ttl) {
        bytesRedisTemplate.opsForValue().set(PREFIX + key, value, ttl);
    }

    @Override
    public boolean tryLock(String key, String token, Duration ttl) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(PREFIX + key, token, ttl));
    }

    @Override
    public void unlock(String key, String token) {
        stringRedisTemplate.execute(UNLOCK_SCRIPT, List.of(PREFIX + key), token);
    }
}
