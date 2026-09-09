package com.campus.competition.controller;

import com.campus.competition.annotation.OpLog;
import com.campus.competition.common.PageVO;
import com.campus.competition.common.Result;
import com.campus.competition.common.UserContext;
import com.campus.competition.entity.TrainingSite;
import com.campus.competition.service.TrainingService;
import com.campus.competition.vo.CheckinVO;
import com.campus.competition.vo.LeaderboardVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 训练模块接口：训练资源（按竞赛/标签）+ 一键打卡 + 收藏 + 排行榜
 */
@RestController
@RequestMapping("/api/training")
public class TrainingController {

    private final TrainingService trainingService;

    public TrainingController(TrainingService trainingService) {
        this.trainingService = trainingService;
    }

    // ===== 训练资源（浏览，公开）=====

    @GetMapping("/sites")
    public Result<PageVO<TrainingSite>> sites(@RequestParam(required = false) String competition,
                                              @RequestParam(required = false) String tag,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "12") long size) {
        return Result.success(trainingService.listSites(competition, tag, keyword, page, size));
    }

    @GetMapping("/tags")
    public Result<List<String>> tags() {
        return Result.success(trainingService.listTags());
    }

    // ===== 收藏 =====

    @PostMapping("/sites/{id}/favorite")
    public Result<Boolean> toggleFavorite(@PathVariable Long id) {
        return Result.success(trainingService.toggleFavorite(UserContext.getUserId(), id));
    }

    @GetMapping("/favorites")
    public Result<List<TrainingSite>> favorites() {
        return Result.success(trainingService.myFavorites(UserContext.getUserId()));
    }

    @GetMapping("/favorites/ids")
    public Result<Set<Long>> favoriteIds() {
        return Result.success(trainingService.myFavoriteIds(UserContext.getUserId()));
    }

    // ===== 打卡 =====

    @OpLog("训练打卡")
    @PostMapping("/checkin")
    public Result<Void> checkin() {
        trainingService.checkin(UserContext.getUserId());
        return Result.success();
    }

    @GetMapping("/checkin/today")
    public Result<CheckinVO> checkinToday() {
        return Result.success(trainingService.checkinToday(UserContext.getUserId()));
    }

    @GetMapping("/checkin/leaderboard")
    public Result<List<LeaderboardVO>> leaderboard(@RequestParam(defaultValue = "all") String range) {
        return Result.success(trainingService.leaderboard(range));
    }

    // ===== 管理员：训练资源管理 =====

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/sites")
    public Result<PageVO<TrainingSite>> adminList(@RequestParam(required = false) String keyword,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size) {
        return Result.success(trainingService.adminList(keyword, page, size));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @OpLog("新增训练资源")
    @PostMapping("/admin/sites")
    public Result<TrainingSite> createSite(@RequestBody TrainingSite site) {
        return Result.success(trainingService.createSite(site));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @OpLog("修改训练资源")
    @PutMapping("/admin/sites/{id}")
    public Result<TrainingSite> updateSite(@PathVariable Long id, @RequestBody TrainingSite site) {
        return Result.success(trainingService.updateSite(id, site));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @OpLog("删除训练资源")
    @DeleteMapping("/admin/sites/{id}")
    public Result<Void> deleteSite(@PathVariable Long id) {
        trainingService.deleteSite(id);
        return Result.success();
    }
}
