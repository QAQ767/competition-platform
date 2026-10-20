package com.campus.competition.controller;

import com.campus.competition.common.Result;
import com.campus.competition.common.UserContext;
import com.campus.competition.dto.AdviceMessageReq;
import com.campus.competition.dto.AdvicePreferencesReq;
import com.campus.competition.dto.AdviceRecommendationReq;
import com.campus.competition.service.AdviceSessionService;
import com.campus.competition.vo.AdviceMessageResultVO;
import com.campus.competition.vo.AdviceSessionVO;
import com.campus.competition.vo.StudentRecommendationVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生参赛推荐。所有接口都从登录态取 userId，客户端传入其他用户 ID 无效。
 */
@RestController
@RequestMapping("/api/ai/advice")
public class AdviceController {

    private final AdviceSessionService sessionService;

    public AdviceController(AdviceSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/sessions")
    public Result<AdviceSessionVO> create() {
        return Result.success(sessionService.create(UserContext.getUserId()));
    }

    @GetMapping("/sessions/{id}")
    public Result<AdviceSessionVO> get(@PathVariable String id) {
        return Result.success(sessionService.get(UserContext.getUserId(), id));
    }

    @PostMapping("/sessions/{id}/messages")
    public Result<AdviceMessageResultVO> message(@PathVariable String id,
                                                 @RequestBody AdviceMessageReq req) {
        return Result.success(sessionService.message(UserContext.getUserId(), id,
                req == null ? null : req.getMessage()));
    }

    @PutMapping("/sessions/{id}/preferences")
    public Result<AdviceSessionVO> preferences(@PathVariable String id,
                                               @RequestBody AdvicePreferencesReq req) {
        return Result.success(sessionService.updatePreferences(
                UserContext.getUserId(), id, req == null ? null : req.getVersion(),
                req == null ? null : req.getPreferences()));
    }

    @PostMapping("/sessions/{id}/recommendations")
    public Result<StudentRecommendationVO> recommend(@PathVariable String id,
                                                     @RequestBody(required = false) AdviceRecommendationReq req) {
        return Result.success(sessionService.recommend(UserContext.getUserId(), id,
                req == null ? null : req.getWindowDays()));
    }

    @DeleteMapping("/sessions/{id}")
    public Result<Void> delete(@PathVariable String id) {
        sessionService.delete(UserContext.getUserId(), id);
        return Result.success();
    }

    /**
     * 只按档案推荐，不依赖 Redis。用于 P1 独立演示，也是会话存储故障时的明确降级入口。
     */
    @PostMapping("/profile-recommendations")
    public Result<StudentRecommendationVO> profileRecommendation(
            @RequestBody(required = false) AdviceRecommendationReq req) {
        return Result.success(sessionService.recommendFromProfile(
                UserContext.getUserId(), req == null ? null : req.getWindowDays()));
    }
}
