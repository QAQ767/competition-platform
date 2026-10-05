package com.campus.competition.service;

import com.campus.competition.vo.RecommendUserVO;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TeamAiAssessmentTest {
    private List<RecommendUserVO> candidates() {
        List<RecommendUserVO> result = new ArrayList<>();
        for (long id = 1; id <= 2; id++) {
            RecommendUserVO vo = new RecommendUserVO();
            vo.setUserId(id); vo.setMatchScore(id == 1 ? 90 : 60);
            vo.setReason("规则理由"); vo.setReasonSource("RULE"); result.add(vo);
        }
        return result;
    }
    private String row(long id, int score) {
        return "{\"userId\":"+id+",\"score\":"+score+",\"reason\":\"根据自述负责过接口联调\",\"suggestedRole\":\"前端\",\"uncertainty\":\"确认投入时间\"}";
    }
    @Test void semanticScoresActuallyChangeRanking() {
        var pool = candidates();
        assertTrue(TeamAiAssessment.apply("<think>分析</think>```json\n{\"teamAnalysis\":\"团队缺前端\",\"candidates\":["+row(1,65)+","+row(2,95)+"]}\n```", pool));
        assertEquals(2L, pool.get(0).getUserId());
        assertEquals("LLM", pool.get(0).getReasonSource());
        assertEquals("团队缺前端", pool.get(0).getTeamAnalysis());
    }
    @Test void malformedOrInventedCandidateNeverPartiallyMutatesRuleResults() {
        for (String invalid : Arrays.asList(null, "not json",
                "{\"teamAnalysis\":\"缺口\",\"candidates\":["+row(1,65)+","+row(9,95)+"]}",
                "{\"teamAnalysis\":\"缺口\",\"candidates\":["+row(1,65)+","+row(1,95)+"]}",
                "{\"teamAnalysis\":\"缺口\",\"candidates\":["+row(1,65)+","+row(2,101)+"]}")) {
            var pool = candidates(); assertFalse(TeamAiAssessment.apply(invalid, pool));
            assertEquals(90, pool.get(0).getMatchScore());
            assertEquals("RULE", pool.get(0).getReasonSource());
        }
    }
}
