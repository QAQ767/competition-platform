package com.campus.competition.service;

import com.campus.competition.vo.CompetitionRecommendationVO;
import com.campus.competition.vo.TeamRecommendationVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 模型输出必须完整覆盖候选 ID、无重复、分数和文本长度合法，否则整批回退。 */
class StudentRecommendationAiTest {

    @Test
    @DisplayName("合法输出按模型分数重排并写入理由")
    void appliesValidOutput() {
        List<CompetitionRecommendationVO> competitions = competitions(1L, 2L);
        List<TeamRecommendationVO> teams = teams(10L);
        String json = "{\"insight\":\"你更适合前端主导的队伍\","
                + "\"competitions\":[{\"id\":2,\"score\":88,\"reason\":\"赛事需要前端\","
                + "\"risks\":[\"报名截止较近\"],\"nextStep\":\"核对公告\"},"
                + "{\"id\":1,\"score\":60,\"reason\":\"方向一般\",\"risks\":[],\"nextStep\":\"先观察\"}],"
                + "\"teams\":[{\"id\":10,\"score\":91,\"reason\":\"队伍缺前端\","
                + "\"risks\":[\"需确认时间\"],\"nextStep\":\"提交申请\",\"suggestedRole\":\"前端（补位）\"}]}";
        StudentRecommendationAi.Application application = StudentRecommendationAi.apply(json, competitions, teams);
        assertTrue(application.ok);
        assertEquals("你更适合前端主导的队伍", application.insight);
        assertEquals(2L, competitions.get(0).getId());
        assertEquals(88, competitions.get(0).getMatchScore());
        assertEquals(91, teams.get(0).getMatchScore());
        assertEquals("前端（补位）", teams.get(0).getSuggestedRole());
    }

    @Test
    @DisplayName("假 ID、重复 ID、缺失候选都拒绝")
    void rejectsUnknownDuplicateAndMissingIds() {
        List<CompetitionRecommendationVO> competitions = competitions(1L, 2L);
        String unknown = "{\"insight\":\"x\",\"competitions\":[{\"id\":99,\"score\":50,\"reason\":\"x\","
                + "\"risks\":[],\"nextStep\":\"x\"},{\"id\":2,\"score\":50,\"reason\":\"x\","
                + "\"risks\":[],\"nextStep\":\"x\"}],\"teams\":[]}";
        assertFalse(StudentRecommendationAi.apply(unknown, competitions, teams()).ok);

        String duplicate = "{\"insight\":\"x\",\"competitions\":[{\"id\":1,\"score\":50,\"reason\":\"x\","
                + "\"risks\":[],\"nextStep\":\"x\"},{\"id\":1,\"score\":50,\"reason\":\"x\","
                + "\"risks\":[],\"nextStep\":\"x\"}],\"teams\":[]}";
        assertFalse(StudentRecommendationAi.apply(duplicate, competitions, teams()).ok);

        String missing = "{\"insight\":\"x\",\"competitions\":[{\"id\":1,\"score\":50,\"reason\":\"x\","
                + "\"risks\":[],\"nextStep\":\"x\"}],\"teams\":[]}";
        assertFalse(StudentRecommendationAi.apply(missing, competitions, teams()).ok);
    }

    @Test
    @DisplayName("越界分数、残缺 JSON、超长理由都拒绝")
    void rejectsInvalidScoreAndMalformedJson() {
        List<CompetitionRecommendationVO> competitions = competitions(1L);
        String score = "{\"insight\":\"x\",\"competitions\":[{\"id\":1,\"score\":101,\"reason\":\"x\","
                + "\"risks\":[],\"nextStep\":\"x\"}],\"teams\":[]}";
        assertFalse(StudentRecommendationAi.apply(score, competitions, teams()).ok);
        assertFalse(StudentRecommendationAi.apply("{\"insight\":", competitions, teams()).ok);

        String longReason = "{\"insight\":\"x\",\"competitions\":[{\"id\":1,\"score\":50,\"reason\":\""
                + "a".repeat(601) + "\",\"risks\":[],\"nextStep\":\"x\"}],\"teams\":[]}";
        assertFalse(StudentRecommendationAi.apply(longReason, competitions, teams()).ok);
    }

    @Test
    @DisplayName("超长建议角色只保留第一个角色，不整批回退")
    void trimsOverlongSuggestedRole() {
        List<TeamRecommendationVO> teams = teams(10L);
        String json = "{\"insight\":\"x\",\"competitions\":[],\"teams\":[{\"id\":10,\"score\":80,"
                + "\"reason\":\"队伍缺前端\",\"risks\":[],\"nextStep\":\"申请\","
                + "\"suggestedRole\":\"前端工程师/后端工程师/算法工程师\"}]}";
        StudentRecommendationAi.Application application =
                StudentRecommendationAi.apply(json, new ArrayList<>(), teams);
        assertTrue(application.ok);
        assertEquals("前端工程师", teams.get(0).getSuggestedRole());
    }

    @Test
    @DisplayName("候选为空时模型必须返回空数组")
    void emptyCandidatesRequireEmptyArrays() {
        String json = "{\"insight\":\"暂无候选\",\"competitions\":[],\"teams\":[]}";
        assertTrue(StudentRecommendationAi.apply(json, new ArrayList<>(), new ArrayList<>()).ok);
    }

    @Test
    void invalidLaterTeamDoesNotMutateEarlierCompetition() {
        var comps = competitions(14L); comps.get(0).setReason("规则理由");
        var ts = teams(7L); ts.get(0).setReason("规则队伍");
        String output = "{\"insight\":\"x\",\"competitions\":[{\"id\":14,\"score\":99,\"reason\":\"模型理由\",\"risks\":[],\"nextStep\":\"x\"}],\"teams\":[{\"id\":7,\"score\":101,\"reason\":\"错误\",\"risks\":[],\"nextStep\":\"x\",\"suggestedRole\":\"后端\"}]}";
        assertFalse(StudentRecommendationAi.apply(output, comps, ts).ok);
        assertEquals(1, comps.get(0).getMatchScore()); assertEquals("规则理由", comps.get(0).getReason());
        assertEquals("规则队伍", ts.get(0).getReason());
    }

    @Test
    void trailingGarbageAndOverflowScoreRejected() {
        String output = "{\"insight\":\"x\",\"competitions\":[],\"teams\":[]}";
        assertFalse(StudentRecommendationAi.apply(output + "garbage", new ArrayList<>(), new ArrayList<>()).ok);
        String overflow = "{\"insight\":\"x\",\"competitions\":[{\"id\":14,\"score\":4294967297,\"reason\":\"x\",\"risks\":[],\"nextStep\":\"x\"}],\"teams\":[]}";
        assertFalse(StudentRecommendationAi.apply(overflow, competitions(14L), teams()).ok);
    }

    private List<CompetitionRecommendationVO> competitions(Long... ids) {
        List<CompetitionRecommendationVO> list = new ArrayList<>();
        for (Long id : ids) {
            CompetitionRecommendationVO vo = new CompetitionRecommendationVO();
            vo.setId(id);
            vo.setMatchScore(1);
            list.add(vo);
        }
        return list;
    }

    private List<TeamRecommendationVO> teams(Long... ids) {
        List<TeamRecommendationVO> list = new ArrayList<>();
        for (Long id : ids) {
            TeamRecommendationVO vo = new TeamRecommendationVO();
            vo.setId(id);
            vo.setMatchScore(1);
            list.add(vo);
        }
        return list;
    }
}
