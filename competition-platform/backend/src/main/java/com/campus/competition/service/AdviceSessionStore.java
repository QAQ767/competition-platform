package com.campus.competition.service;

import java.time.Duration;
import java.util.Optional;

/** 推荐会话存储抽象。生产使用 Redis；测试可注入内存实现。 */
public interface AdviceSessionStore {

    /** 条件写入结果。 */
    enum SaveOutcome {
        SAVED,
        /** 会话已不存在（过期或被清空），旧请求不能复活它。 */
        MISSING,
        /** 版本号已变化，本次写入没有生效。 */
        VERSION_CONFLICT
    }

    Optional<String> load(String sessionId);

    /** 仅用于新建会话（ID 为随机串，不存在并发写）。 */
    void save(String sessionId, String json, Duration ttl);

    /** 只续期，不重写正文；避免把读取到的旧快照写回覆盖新编辑。 */
    boolean touch(String sessionId, Duration ttl);

    /**
     * 仅当存储中的 version 等于 expectedVersion 时写入，比较与写入必须是原子操作。
     */
    SaveOutcome saveIfVersion(String sessionId, int expectedVersion, String json, Duration ttl);

    void delete(String sessionId);
}
