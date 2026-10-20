package com.campus.competition.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/** 赛事发现作业线程池：限制并发，避免管理员连续点击拖垮服务。 */
@Configuration
public class DiscoveryExecutorConfig {

    @Bean(name = "discoveryExecutor")
    public ThreadPoolTaskExecutor discoveryExecutor(DiscoveryProperties properties) {
        int concurrency = Math.max(1, properties.getJob().getConcurrency());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("competition-discovery-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
