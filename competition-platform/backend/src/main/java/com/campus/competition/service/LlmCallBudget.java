package com.campus.competition.service;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import com.campus.competition.common.BusinessException;

/** 一个用户回合共享截止时刻；取消会立即中断当前 HTTP 等待。 */
public final class LlmCallBudget implements AutoCloseable {
    private static final ThreadLocal<LlmCallBudget> CURRENT = new ThreadLocal<>();
    private final long deadline;
    private volatile boolean cancelled;
    private LlmService.LlmResult failure;
    static LlmService.LlmResult previousFailure() { LlmCallBudget b = CURRENT.get(); return b == null ? null : b.failure; }
    static LlmService.LlmResult failure(LlmService.LlmResult result) {
        LlmCallBudget b = CURRENT.get();
        if (b != null && (result.getError().startsWith("HTTP_") || java.util.Set.of("LLM_TIMEOUT", "LLM_UNAVAILABLE", "CANCELLED").contains(result.getError()))) b.failure = result;
        return result;
    }
    private final AtomicReference<Future<?>> pending = new AtomicReference<>();
    public LlmCallBudget(long seconds) { deadline = System.nanoTime() + seconds * 1_000_000_000L; CURRENT.set(this); }
    static long remainingMillis(long fallbackSeconds) {
        LlmCallBudget b = CURRENT.get();
        if (b == null) return fallbackSeconds * 1000;
        b.check();
        return Math.max(1, Math.min(fallbackSeconds * 1000, (b.deadline - System.nanoTime()) / 1_000_000));
    }
    static void track(Future<?> f) { LlmCallBudget b = CURRENT.get(); if (b != null) { b.pending.set(f); if (b.cancelled) f.cancel(true); } }
    public void check() { if (cancelled) throw new BusinessException(409, "本轮已停止，请重试"); }
    public void cancel() { cancelled = true; Future<?> f = pending.get(); if (f != null) f.cancel(true); }
    public void close() { pending.set(null); CURRENT.remove(); }
}
