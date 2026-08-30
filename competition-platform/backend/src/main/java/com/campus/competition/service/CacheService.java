package com.campus.competition.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 基于 Redis 的通用 JSON 缓存服务
 * - 自动序列化/反序列化
 * - 支持 TTL 抖动（防止缓存雪崩）
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public CacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public <T> T get(String key, Class<T> type) {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("缓存反序列化失败, key={}", key, e);
            redisTemplate.delete(key);
            return null;
        }
    }

    public <T> T get(String key, TypeReference<T> type) {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("缓存反序列化失败, key={}", key, e);
            redisTemplate.delete(key);
            return null;
        }
    }

    public void put(String key, Object value, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value),
                    Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.warn("缓存写入失败, key={}", key, e);
        }
    }

    /**
     * 写入带 TTL 抖动的缓存（基础 TTL + 随机 0~jitter 秒），
     * 避免大量 key 同时过期导致缓存雪崩
     */
    public void putJittered(String key, Object value, long baseTtlSeconds, int jitterSeconds) {
        long ttl = baseTtlSeconds + ThreadLocalRandom.current().nextInt(jitterSeconds + 1);
        put(key, value, ttl);
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }
}
