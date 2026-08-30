package com.campus.competition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.competition.common.BusinessException;
import com.campus.competition.entity.Achievement;
import com.campus.competition.entity.Competition;
import com.campus.competition.entity.Team;
import com.campus.competition.entity.TeamApplication;
import com.campus.competition.entity.TeamMember;
import com.campus.competition.entity.User;
import com.campus.competition.mapper.AchievementMapper;
import com.campus.competition.mapper.CompetitionMapper;
import com.campus.competition.mapper.SkillTagMapper;
import com.campus.competition.mapper.TeamApplicationMapper;
import com.campus.competition.mapper.TeamMapper;
import com.campus.competition.mapper.TeamMemberMapper;
import com.campus.competition.mapper.UserMapper;
import com.campus.competition.mapper.UserSkillMapper;
import com.campus.competition.support.UserVOHelper;
import com.campus.competition.vo.RecommendUserVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI 智能推荐服务（核心亮点）
 *
 * 匹配度 = 技能契合度(40) + 竞赛方向契合度(30) + 参赛状态契合度(20) + 经验契合度(10)
 * 候选池：参赛状态为"希望被邀请/可接受邀请"，且未在该队伍中、未申请过的用户。
 * 推荐理由：前 3 名由大模型（LLM）批量生成，其余用规则模板；LLM 异常自动降级规则。
 */
@Service
public class AiService {

    private static final Pattern REASON_LINE = Pattern.compile("^\\s*(\\d+)\\s*[:：]\\s*(.+)$");

    private final UserMapper userMapper;
    private final UserSkillMapper userSkillMapper;
    private final SkillTagMapper skillTagMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final TeamApplicationMapper teamApplicationMapper;
    private final CompetitionMapper competitionMapper;
    private final AchievementMapper achievementMapper;
    private final LlmService llmService;

    public AiService(UserMapper userMapper, UserSkillMapper userSkillMapper, SkillTagMapper skillTagMapper,
                     TeamMapper teamMapper, TeamMemberMapper teamMemberMapper,
                     TeamApplicationMapper teamApplicationMapper, CompetitionMapper competitionMapper,
                     AchievementMapper achievementMapper, LlmService llmService) {
        this.userMapper = userMapper;
        this.userSkillMapper = userSkillMapper;
        this.skillTagMapper = skillTagMapper;
        this.teamMapper = teamMapper;
        this.teamMemberMapper = teamMemberMapper;
        this.teamApplicationMapper = teamApplicationMapper;
        this.competitionMapper = competitionMapper;
        this.achievementMapper = achievementMapper;
        this.llmService = llmService;
    }

    public List<RecommendUserVO> recommend(Long teamId) {
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            throw new BusinessException("队伍不存在");
        }
        List<String> teamSkills = split(team.getSkills());

        // 队伍已有成员 & 待审批申请人，排除出候选池
        Set<Long> memberIds = teamMemberMapper.selectList(
                        new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, teamId))
                .stream().map(TeamMember::getUserId).collect(Collectors.toSet());
        Set<Long> applicantIds = teamApplicationMapper.selectList(
                        new LambdaQueryWrapper<TeamApplication>()
                                .eq(TeamApplication::getTeamId, teamId)
                                .eq(TeamApplication::getStatus, "待审批"))
                .stream().map(TeamApplication::getUserId).collect(Collectors.toSet());

        List<RecommendUserVO> results = new ArrayList<>();
        for (User user : userMapper.selectList(null)) {
            if (!"STUDENT".equals(user.getRole())) {
                continue;
            }
            String cs = user.getCompeteStatus();
            if (!("WANT_INVITE".equals(cs) || "ACCEPT_INVITE".equals(cs))) {
                continue; // 尊重用户意愿：只推荐"愿意被邀请"的用户
            }
            if (user.getId().equals(team.getCaptainId()) || memberIds.contains(user.getId())
                    || applicantIds.contains(user.getId())) {
                continue;
            }

            List<String> userSkills = UserVOHelper.toSkillNames(user.getId(), userSkillMapper, skillTagMapper);

            // 1. 技能契合度 40 分
            int skillScore = 0;
            if (!teamSkills.isEmpty()) {
                long inter = userSkills.stream().filter(teamSkills::contains).count();
                skillScore = (int) Math.round(inter * 40.0 / teamSkills.size());
            }

            // 2. 竞赛方向契合度 30 分
            int dirScore = directionScore(team, userSkills);

            // 3. 参赛状态契合度 20 分
            int statusScore = "WANT_INVITE".equals(cs) ? 20 : 12;

            // 4. 经验契合度 10 分（有获奖记录）
            boolean hasExp = achievementMapper.selectCount(
                    new LambdaQueryWrapper<Achievement>().eq(Achievement::getUserId, user.getId())) > 0;
            int expScore = hasExp ? 10 : 5;

            int total = Math.min(100, skillScore + dirScore + statusScore + expScore);

            RecommendUserVO vo = new RecommendUserVO();
            vo.setUserId(user.getId());
            vo.setNickname(user.getNickname());
            vo.setCollege(user.getCollege());
            vo.setMajor(user.getMajor());
            vo.setSkills(userSkills);
            vo.setCompeteStatus(cs);
            vo.setMatchScore(total);
            vo.setHasExperience(hasExp);
            vo.setReason(buildReason(teamSkills, userSkills, cs, hasExp));
            vo.setReasonSource("RULE");
            results.add(vo);
        }

        results.sort((a, b) -> Integer.compare(b.getMatchScore(), a.getMatchScore()));
        List<RecommendUserVO> top = results.size() > 10 ? results.subList(0, 10) : results;
        // 前 3 名由大模型生成个性化理由（批量一次调用，失败自动降级）
        enhanceWithLlm(team, top);
        return top;
    }

    /**
     * 调用大模型为前 3 名候选生成个性化推荐理由（单次批量调用）
     * 输出格式要求：每行 "序号:理由"；解析失败则该候选保持规则理由
     */
    private void enhanceWithLlm(Team team, List<RecommendUserVO> candidates) {
        if (candidates.isEmpty()) {
            return;
        }
        List<RecommendUserVO> targets = candidates.size() > 3 ? candidates.subList(0, 3) : candidates;
        Competition competition = competitionMapper.selectById(team.getCompetitionId());
        String competitionName = competition == null ? "未知竞赛" : competition.getName();

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是高校竞赛组队平台的智能推荐助手。队伍目标竞赛是「").append(competitionName)
                .append("」，队伍所需技能：").append(String.join("、", split(team.getSkills())))
                .append("。\n以下是系统按匹配度排序的前 ").append(targets.size()).append(" 位候选队友：\n");
        for (int i = 0; i < targets.size(); i++) {
            RecommendUserVO vo = targets.get(i);
            prompt.append(i + 1).append(". 昵称：").append(vo.getNickname())
                    .append("，学院：").append(vo.getCollege() == null ? "未知" : vo.getCollege())
                    .append("，专业：").append(vo.getMajor() == null ? "未知" : vo.getMajor())
                    .append("，技能：").append(vo.getSkills().isEmpty() ? "无" : String.join("、", vo.getSkills()))
                    .append("，参赛状态：").append(vo.getCompeteStatus())
                    .append("，获奖经历：").append(Boolean.TRUE.equals(vo.getHasExperience()) ? "有" : "无")
                    .append("\n");
        }
        prompt.append("请为每位候选生成一句 30 字以内、有说服力的推荐理由（结合与队伍的契合点），")
                .append("严格按以下格式逐行输出，不要输出其他内容：\n");
        for (int i = 0; i < targets.size(); i++) {
            prompt.append(i + 1).append(":理由\n");
        }

        String output = llmService.generate(prompt.toString());
        if (output == null || output.isBlank()) {
            return; // LLM 不可用，保留规则理由
        }

        Map<Integer, String> parsed = new HashMap<>();
        for (String line : output.split("\\n")) {
            Matcher matcher = REASON_LINE.matcher(line.trim());
            if (matcher.matches()) {
                int index = Integer.parseInt(matcher.group(1));
                String reason = matcher.group(2).trim();
                if (index >= 1 && index <= targets.size() && !reason.isEmpty()) {
                    parsed.put(index, reason);
                }
            }
        }
        for (Map.Entry<Integer, String> entry : parsed.entrySet()) {
            RecommendUserVO vo = targets.get(entry.getKey() - 1);
            vo.setReason(entry.getValue());
            vo.setReasonSource("LLM");
        }
    }

    /**
     * 竞赛方向契合度：用户技能与竞赛关键词的交集情况
     */
    private int directionScore(Team team, List<String> userSkills) {
        Competition competition = competitionMapper.selectById(team.getCompetitionId());
        if (competition == null || competition.getName() == null) {
            return 12;
        }
        List<String> keywords = new ArrayList<>();
        String name = competition.getName();
        if (name.contains("数学建模")) {
            keywords.add("数据分析");
            keywords.add("机器学习");
            keywords.add("文档写作");
        } else if (name.contains("蓝桥杯") || name.contains("ACM")) {
            keywords.add("算法");
            keywords.add("后端");
            keywords.add("前端");
        } else if (name.contains("互联网+") || name.contains("挑战杯")) {
            keywords.add("UI设计");
            keywords.add("答辩演讲");
            keywords.add("文档写作");
        }
        boolean hit = keywords.stream().anyMatch(userSkills::contains);
        return hit ? 25 : 12;
    }

    private String buildReason(List<String> teamSkills, List<String> userSkills,
                               String competeStatus, boolean hasExp) {
        List<String> parts = new ArrayList<>();
        if (!teamSkills.isEmpty()) {
            List<String> inter = userSkills.stream().filter(teamSkills::contains).collect(Collectors.toList());
            if (!inter.isEmpty()) {
                parts.add("TA 擅长" + String.join("、", inter) + "，与你的队伍需求高度契合");
            }
        }
        if ("WANT_INVITE".equals(competeStatus)) {
            parts.add("TA 当前正在备战，希望被邀请入队");
        } else if ("ACCEPT_INVITE".equals(competeStatus)) {
            parts.add("TA 愿意接受组队邀请");
        }
        if (hasExp) {
            parts.add("TA 有过竞赛获奖经历，经验丰富");
        }
        if (parts.isEmpty()) {
            parts.add("与你的组队需求有一定契合度");
        }
        return String.join("；", parts);
    }

    private List<String> split(String skills) {
        if (skills == null || skills.isBlank()) {
            return new ArrayList<>();
        }
        Set<String> set = new HashSet<>();
        for (String s : skills.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                set.add(t);
            }
        }
        return new ArrayList<>(set);
    }
}
