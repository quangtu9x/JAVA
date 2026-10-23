package com.td.application.sharedcore;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Store giả trong bộ nhớ cho test (thay Redis). Đặt {@link #failing} = true để giả lập Redis lỗi. */
class InMemoryOrganizationTreeCacheStore implements OrganizationTreeCacheStore {

    final AtomicLong version = new AtomicLong();
    final Map<String, byte[]> values = new ConcurrentHashMap<>();
    final Map<String, String> locks = new ConcurrentHashMap<>();
    volatile boolean failing;

    @Override
    public long currentVersion() {
        check();
        return version.get();
    }

    @Override
    public long bumpVersion() {
        check();
        return version.incrementAndGet();
    }

    @Override
    public byte[] get(String key) {
        check();
        return values.get(key);
    }

    @Override
    public void put(String key, byte[] value, Duration ttl) {
        check();
        values.put(key, value);
    }

    @Override
    public boolean tryLock(String key, String token, Duration ttl) {
        check();
        return locks.putIfAbsent(key, token) == null;
    }

    @Override
    public void unlock(String key, String token) {
        check();
        locks.remove(key, token);
    }

    private void check() {
        if (failing) {
            throw new IllegalStateException("redis down");
        }
    }
}
