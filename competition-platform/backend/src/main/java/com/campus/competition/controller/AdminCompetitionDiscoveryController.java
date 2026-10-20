package com.campus.competition.controller;

import com.campus.competition.annotation.OpLog;
import com.campus.competition.common.Result;
import com.campus.competition.common.UserContext;
import com.campus.competition.dto.DiscoveryJobReq;
import com.campus.competition.service.CompetitionDiscoveryService;
import com.campus.competition.vo.DiscoveryJobVO;
import com.campus.competition.vo.DiscoveryStatusVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员近期赛事发现。服务端强制 ADMIN，前端按钮隐藏不算权限。 */
@RestController
@RequestMapping("/api/admin/competition-discovery")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCompetitionDiscoveryController {

    private final CompetitionDiscoveryService discoveryService;

    public AdminCompetitionDiscoveryController(CompetitionDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @GetMapping("/status")
    public Result<DiscoveryStatusVO> status() {
        return Result.success(discoveryService.status());
    }

    @OpLog("启动赛事发现作业")
    @PostMapping("/jobs")
    public Result<Long> create(@RequestBody DiscoveryJobReq req) {
        return Result.success(discoveryService.createJob(UserContext.getUserId(), req));
    }

    @GetMapping("/jobs/{id}")
    public Result<DiscoveryJobVO> get(@PathVariable Long id) {
        return Result.success(discoveryService.get(id));
    }
}
