package com.campus.competition.controller;

import com.campus.competition.common.Result;
import com.campus.competition.service.StatsService;
import com.campus.competition.vo.StatsVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据统计接口
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/overview")
    public Result<StatsVO> overview() {
        return Result.success(statsService.overview());
    }
}
