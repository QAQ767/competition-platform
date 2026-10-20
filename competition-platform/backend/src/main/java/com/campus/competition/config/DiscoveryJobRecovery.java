package com.campus.competition.config;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.competition.entity.CompetitionDiscoveryJob;
import com.campus.competition.mapper.CompetitionDiscoveryJobMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 进程重启后，把遗留的 QUEUED/RUNNING 作业标记为可重试失败，避免管理端永久转圈。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class DiscoveryJobRecovery implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryJobRecovery.class);

    private final CompetitionDiscoveryJobMapper jobMapper;

    public DiscoveryJobRecovery(CompetitionDiscoveryJobMapper jobMapper) {
        this.jobMapper = jobMapper;
    }

    @Override
    public void run(String... args) {
        try {
            int updated = jobMapper.update(null, new LambdaUpdateWrapper<CompetitionDiscoveryJob>()
                    .in(CompetitionDiscoveryJob::getStatus, "QUEUED", "RUNNING")
                    .set(CompetitionDiscoveryJob::getStatus, "FAILED")
                    .set(CompetitionDiscoveryJob::getMessage, "服务重启，任务已中断，可重新发起")
                    .set(CompetitionDiscoveryJob::getFinishedAt, LocalDateTime.now()));
            if (updated > 0) {
                log.warn("已将 {} 个遗留发现作业标记为失败，可重新发起", updated);
            }
        } catch (Exception e) {
            // 旧库尚未迁移时不要阻止应用启动；迁移完成后下次重启会再次尝试。
            log.warn("检查遗留发现作业失败: {}", e.getMessage());
        }
    }
}
