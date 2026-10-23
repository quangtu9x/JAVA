package com.td.infrastructure.tonghop.national;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Yêu cầu người dùng 22/10/2026: tên cơ quan Trục tra Redis trước SQL. */
class RedisNationalAgencyNamesTest {

    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hash = mock(HashOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    private RedisNationalAgencyNames names() {
        when(redis.opsForHash()).thenReturn(hash);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("redis", redis);
        return new RedisNationalAgencyNames(beans.getBeanProvider(StringRedisTemplate.class), "tdgateway:national-agency");
    }

    @Test
    void returnsOnlyCodesFoundInRedis() {
        RedisNationalAgencyNames names = names();
        when(hash.multiGet(eq("tdgateway:national-agency:names"), any())).thenReturn(Arrays.asList("Bộ A", null));

        assertThat(names.lookup(List.of("A", "B"))).containsExactlyEntriesOf(java.util.Map.of("A", "Bộ A"));
    }

    @Test
    void redisFailureReturnsEmptyAndBacksOff() {
        RedisNationalAgencyNames names = names();
        when(hash.multiGet(any(), any())).thenThrow(new IllegalStateException("redis down"));

        assertThat(names.lookup(List.of("A"))).isEmpty();
        assertThat(names.lookup(List.of("A"))).isEmpty();
        verify(hash, times(1)).multiGet(any(), any()); // lần 2 không gọi Redis (đang tạm ngắt)
    }

    @Test
    void noRedisConfiguredReturnsEmpty() {
        RedisNationalAgencyNames names = new RedisNationalAgencyNames(
            new StaticListableBeanFactory().getBeanProvider(StringRedisTemplate.class), "p");
        assertThat(names.lookup(List.of("A"))).isEmpty();
    }
}
