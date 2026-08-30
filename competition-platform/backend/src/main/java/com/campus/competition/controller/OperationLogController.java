package com.campus.competition.controller;

import com.campus.competition.common.PageVO;
import com.campus.competition.common.Result;
import com.campus.competition.common.UserContext;
import com.campus.competition.service.OperationLogService;
import com.campus.competition.vo.OperationLogVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志接口（管理员）
 */
@RestController
@RequestMapping("/api/admin")
public class OperationLogController {

    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/logs")
    public Result<PageVO<OperationLogVO>> page(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size) {
        return Result.success(operationLogService.page(UserContext.getUserId(), page, size));
    }
}
