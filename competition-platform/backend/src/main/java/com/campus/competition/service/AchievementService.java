package com.campus.competition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.competition.common.BusinessException;
import com.campus.competition.common.PageVO;
import com.campus.competition.dto.AchievementReq;
import com.campus.competition.entity.Achievement;
import com.campus.competition.entity.Competition;
import com.campus.competition.entity.Team;
import com.campus.competition.entity.TeamMember;
import com.campus.competition.entity.User;
import com.campus.competition.mapper.AchievementMapper;
import com.campus.competition.mapper.CompetitionMapper;
import com.campus.competition.mapper.TeamMapper;
import com.campus.competition.mapper.TeamMemberMapper;
import com.campus.competition.mapper.UserMapper;
import com.campus.competition.vo.AchievementVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 成果服务（v9.0：发布需证明 + 管理员审核后才公开展示）
 */
@Service
public class AchievementService {

    private final AchievementMapper achievementMapper;
    private final CompetitionMapper competitionMapper;
    private final UserMapper userMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;

    public AchievementService(AchievementMapper achievementMapper,
                              CompetitionMapper competitionMapper, UserMapper userMapper,
                              TeamMapper teamMapper, TeamMemberMapper teamMemberMapper) {
        this.achievementMapper = achievementMapper;
        this.competitionMapper = competitionMapper;
        this.userMapper = userMapper;
        this.teamMapper = teamMapper;
        this.teamMemberMapper = teamMemberMapper;
    }

    /** 提交成果（含证明文件）→ 待审核；获奖队伍由申请人选择（不选则视为个人奖项） */
    public AchievementVO create(Long userId, AchievementReq req) {
        Achievement achievement = new Achievement();
        achievement.setUserId(userId);
        achievement.setCompetitionId(req.getCompetitionId());
        if (req.getTeamId() != null) {
            Team team = teamMapper.selectById(req.getTeamId());
            if (team == null) {
                throw new BusinessException("所选队伍不存在");
            }
            Long memberCount = teamMemberMapper.selectCount(
                    new LambdaQueryWrapper<TeamMember>()
                            .eq(TeamMember::getTeamId, team.getId())
                            .eq(TeamMember::getUserId, userId));
            if (memberCount == 0) {
                throw new BusinessException("你不在所选队伍中");
            }
            achievement.setTeamId(team.getId());
            achievement.setTeamName(team.getTitle()); // 当时队伍名快照
        }
        achievement.setName(req.getName());
        achievement.setLevel(req.getLevel());
        achievement.setAward(req.getAward());
        achievement.setAwardYear(req.getAwardYear());
        achievement.setProof(req.getProof());
        achievement.setStatus("待审核");
        achievement.setCreatedAt(LocalDateTime.now());
        achievementMapper.insert(achievement);
        return toVO(achievement);
    }

    /** 公开成果墙：仅展示已审核通过的成果 */
    public PageVO<AchievementVO> list(long page, long size) {
        Page<Achievement> result = achievementMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Achievement>()
                        .eq(Achievement::getStatus, "已通过")
                        .orderByDesc(Achievement::getId));
        List<AchievementVO> records = result.getRecords().stream()
                .map(this::toVO).collect(Collectors.toList());
        return new PageVO<>(records, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    /** 我的成果（含各状态，便于查看审核进度） */
    public PageVO<AchievementVO> myList(Long userId, long page, long size) {
        Page<Achievement> result = achievementMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Achievement>()
                        .eq(Achievement::getUserId, userId)
                        .orderByDesc(Achievement::getId));
        List<AchievementVO> records = result.getRecords().stream()
                .map(this::toVO).collect(Collectors.toList());
        return new PageVO<>(records, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    /** 某用户已通过的成果（用于用户主页） */
    public List<AchievementVO> listApprovedByUser(Long userId) {
        return achievementMapper.selectList(new LambdaQueryWrapper<Achievement>()
                        .eq(Achievement::getUserId, userId)
                        .eq(Achievement::getStatus, "已通过")
                        .orderByDesc(Achievement::getId))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    /** 管理端：按状态查询 */
    public PageVO<AchievementVO> adminList(String status, long page, long size) {
        LambdaQueryWrapper<Achievement> qw = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            qw.eq(Achievement::getStatus, status);
        }
        qw.orderByDesc(Achievement::getId);
        Page<Achievement> result = achievementMapper.selectPage(new Page<>(page, size), qw);
        List<AchievementVO> records = result.getRecords().stream()
                .map(this::toVO).collect(Collectors.toList());
        return new PageVO<>(records, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    /** 待审核成果数量（管理员红点提醒用） */
    public long pendingCount() {
        return achievementMapper.selectCount(
                new LambdaQueryWrapper<Achievement>().eq(Achievement::getStatus, "待审核"));
    }

    public void approve(Long id, Long operatorId) {
        Achievement achievement = requireAchievement(id);
        achievement.setStatus("已通过");
        achievement.setReviewTime(LocalDateTime.now());
        achievementMapper.updateById(achievement);
    }

    public void reject(Long id, Long operatorId) {
        Achievement achievement = requireAchievement(id);
        achievement.setStatus("已拒绝");
        achievement.setReviewTime(LocalDateTime.now());
        achievementMapper.updateById(achievement);
    }

    /** 删除：仅管理员可删 */
    public void delete(Long id, Long operatorId) {
        User operator = userMapper.selectById(operatorId);
        if (operator == null || !"ADMIN".equals(operator.getRole())) {
            throw new BusinessException("仅管理员可删除成果");
        }
        achievementMapper.deleteById(id);
    }

    private Achievement requireAchievement(Long id) {
        Achievement achievement = achievementMapper.selectById(id);
        if (achievement == null) {
            throw new BusinessException("成果不存在");
        }
        return achievement;
    }

    private AchievementVO toVO(Achievement achievement) {
        AchievementVO vo = new AchievementVO();
        vo.setId(achievement.getId());
        vo.setUserId(achievement.getUserId());
        vo.setTeamId(achievement.getTeamId());
        vo.setTeamName(achievement.getTeamName());
        vo.setCompetitionId(achievement.getCompetitionId());
        vo.setName(achievement.getName());
        vo.setLevel(achievement.getLevel());
        vo.setAward(achievement.getAward());
        vo.setAwardYear(achievement.getAwardYear());
        vo.setProof(achievement.getProof());
        vo.setStatus(achievement.getStatus());
        vo.setReviewTime(achievement.getReviewTime());
        vo.setCreatedAt(achievement.getCreatedAt());
        User user = userMapper.selectById(achievement.getUserId());
        vo.setUserName(user == null ? "未知" : user.getNickname());
        Competition competition = competitionMapper.selectById(achievement.getCompetitionId());
        vo.setCompetitionName(competition == null ? "未知竞赛" : competition.getName());
        return vo;
    }
}
