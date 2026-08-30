package com.campus.competition.controller;

import com.campus.competition.common.Result;
import com.campus.competition.dto.CompetitionReq;
import com.campus.competition.entity.Competition;
import com.campus.competition.annotation.OpLog;
import com.campus.competition.service.CompetitionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 竞赛接口
 */
@RestController
@RequestMapping("/api/competitions")
public class CompetitionController {

    private final CompetitionService competitionService;

    public CompetitionController(CompetitionService competitionService) {
        this.competitionService = competitionService;
    }

    @GetMapping
    public Result<List<Competition>> list(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String level,
                                          @RequestParam(required = false) String status) {
        return Result.success(competitionService.list(keyword, level, status));
    }

    @OpLog("新增竞赛")
    @PostMapping
    public Result<Competition> create(@RequestBody CompetitionReq req) {
        return Result.success(competitionService.create(req));
    }

    @OpLog("删除竞赛")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        competitionService.delete(id);
        return Result.success();
    }
}
