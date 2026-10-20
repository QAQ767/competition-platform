package com.campus.competition.service;

import com.campus.competition.common.BusinessException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Redis 会话存储。Redis 不可用时明确报错，不能退回“把别人的旧会话当成本次结果”。
 *
 * <p>写入使用 Lua 做“比较版本 + 写入”的原子操作：版本比较和 SET 之间不能插入其他请求，
 * 否则旧快照会覆盖新需求。</p>
 */
@Service
public class RedisAdviceSessionStore implements AdviceSessionStore {

    private static final String PREFIX = "ai:advice:session:";

    /** 返回 1 写入成功；0 版本不一致；-1 会话不存在或数据不可解析。 */
    private static final DefaultRedisScript<Long> SAVE_IF_VERSION = new DefaultRedisScript<>(
            "local current = redis.call('GET', KEYS[1])\n"
                    + "if not current then return -1 end\n"
                    + "local ok, obj = pcall(cjson.decode, current)\n"
                    + "if not ok or type(obj) ~= 'table' then return -1 end\n"
                    + "local version = obj['version']\n"
                    + "if type(version) ~= 'number' then return -1 end\n"
                    + "if version ~= tonumber(ARGV[1]) then return 0 end\n"
                    + "redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])\n"
                    + "return 1",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisAdviceSessionStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<String> load(String sessionId) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(PREFIX + sessionId));
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    @Override
    public void save(String sessionId, String json, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(PREFIX + sessionId, json, ttl);
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    @Override
    public boolean touch(String sessionId, Duration ttl) {
        try {
            Boolean renewed = redisTemplate.expire(PREFIX + sessionId, ttl);
            return Boolean.TRUE.equals(renewed);
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    @Override
    public SaveOutcome saveIfVersion(String sessionId, int expectedVersion, String json, Duration ttl) {
        try {
            Long result = redisTemplate.execute(SAVE_IF_VERSION, List.of(PREFIX + sessionId),
                    String.valueOf(expectedVersion), json, String.valueOf(ttl.toMillis()));
            if (result == null) return SaveOutcome.MISSING;
            if (result == 1L) return SaveOutcome.SAVED;
            return result == 0L ? SaveOutcome.VERSION_CONFLICT : SaveOutcome.MISSING;
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    @Override
    public void delete(String sessionId) {
        try {
            redisTemplate.delete(PREFIX + sessionId);
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(503, "推荐会话存储暂不可用（Redis 未连接），请稍后重试");
    }
}
