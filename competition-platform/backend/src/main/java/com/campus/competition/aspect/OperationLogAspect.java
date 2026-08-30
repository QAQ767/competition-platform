package com.campus.competition.aspect;

import com.campus.competition.annotation.OpLog;
import com.campus.competition.common.UserContext;
import com.campus.competition.service.OperationLogService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 操作日志切面：标注 @OpLog 的方法成功返回后记录审计日志
 * 详情格式：类名.方法名 + 关键参数（Long/Integer 类型的 id）
 */
@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    private final OperationLogService operationLogService;

    public OperationLogAspect(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @AfterReturning("@annotation(opLog)")
    public void record(JoinPoint joinPoint, OpLog opLog) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return; // 未登录（公开接口）不记录，登录/注册等由 AuthService 显式记录
        }
        StringBuilder detail = new StringBuilder(joinPoint.getSignature().toShortString());
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof Long || arg instanceof Integer) {
                detail.append(" id=").append(arg);
            }
        }
        operationLogService.record(userId, opLog.value(), detail.toString());
    }
}
