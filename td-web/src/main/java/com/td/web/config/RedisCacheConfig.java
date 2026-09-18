package com.td.web.config;

import com.td.application.categories.CategoryCacheService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.workcalendars.WorkCalendarCacheService;
import com.td.infrastructure.security.CurrentUserContextResolver;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer valueSerializer = buildValueSerializer();

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .disableCachingNullValues()
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer))
            .entryTtl(Duration.ofMinutes(30));

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put(
            DocumentCacheService.DOCUMENT_BY_ID_CACHE,
            defaultConfig.entryTtl(Duration.ofMinutes(60))
        );
        cacheConfigurations.put(
            DocumentCacheService.DOCUMENT_LIST_CACHE,
            defaultConfig.entryTtl(Duration.ofMinutes(10))
        );
        cacheConfigurations.put(
            CategoryCacheService.CATEGORY_BY_ID_CACHE,
            defaultConfig.entryTtl(Duration.ofMinutes(60))
        );
        cacheConfigurations.put(
            CategoryCacheService.CATEGORY_LIST_CACHE,
            defaultConfig.entryTtl(Duration.ofMinutes(10))
        );
        cacheConfigurations.put(
            CurrentUserContextResolver.USER_CONTEXT_CACHE,
            defaultConfig.entryTtl(Duration.ofMinutes(5))
        );
        // Port lichct (08/09/2026) - lich doi nhanh (Domino ghi/doc "lich_<key>_<thu2>" moi lan
        // luu), TTL ngan hon cac region khac - xem docs/analysis/09-... muc 7.6.8.
        cacheConfigurations.put(
            WorkCalendarCacheService.WORK_CALENDAR_BY_WEEK_CACHE,
            defaultConfig.entryTtl(Duration.ofMinutes(10))
        );

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build();
    }

    private GenericJackson2JsonRedisSerializer buildValueSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new ParameterNamesModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );

        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }
}
