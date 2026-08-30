package com.campus.competition.controller;

import com.campus.competition.common.Result;
import com.campus.competition.service.AiService;
import com.campus.competition.vo.RecommendUserVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 智能推荐接口（核心亮点）
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @GetMapping("/recommend")
    public Result<List<RecommendUserVO>> recommend(@RequestParam Long teamId) {
        return Result.success(aiService.recommend(teamId));
    }
}
