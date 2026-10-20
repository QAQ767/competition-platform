package com.campus.competition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.competition.common.BusinessException;
import com.campus.competition.entity.Competition;
import com.campus.competition.entity.Team;
import com.campus.competition.entity.TeamMember;
import com.campus.competition.mapper.CompetitionMapper;
import com.campus.competition.mapper.TeamMapper;
import com.campus.competition.mapper.TeamMemberMapper;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 招募准入检查与人数一致性修复。推荐卡片、申请、审批、邀请共用同一套判断，
 * 避免“页面能点、提交才失败”或“成员已入队但 member_count 还是旧值”。
 */
@Service
public class TeamRecruitmentGuard {

    private final TeamMemberMapper teamMemberMapper;
    private final TeamMapper teamMapper;
    private final CompetitionMapper competitionMapper;
    private final Clock clock;

    public TeamRecruitmentGuard(TeamMemberMapper teamMemberMapper, TeamMapper teamMapper,
                                CompetitionMapper competitionMapper, Clock businessClock) {
        this.teamMemberMapper = teamMemberMapper;
        this.teamMapper = teamMapper;
        this.competitionMapper = competitionMapper;
        this.clock = businessClock;
    }

    /** 实际成员数，不读取可能过期的 team.member_count。 */
    public int actualCount(Long teamId) {
        Long count = teamMemberMapper.selectCount(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, teamId));
        return count == null ? 0 : count.intValue();
    }

    /** 修复人数与满员/招募状态；返回实际成员数。 */
    public int reconcile(Team team) {
        if (team == null || team.getId() == null) return 0;
        int actual = actualCount(team.getId());
        boolean dirty = false;
        if (!Objects.equals(actual, team.getMemberCount())) {
            team.setMemberCount(actual);
            dirty = true;
        }
        int max = team.getMaxMembers() == null ? 0 : team.getMaxMembers();
        LocalDateTime now = LocalDateTime.now(clock);
        if (actual >= max && isRecruiting(team.getStatus())) {
            team.setStatus("已满员");
            dirty = true;
        } else if (actual < max && "已满员".equals(team.getStatus())
                && (team.getDeadline() == null || team.getDeadline().isAfter(now))) {
            team.setStatus("招募中");
            dirty = true;
        }
        if (dirty) {
            // 只更新确有变化的两个字段，避免整实体回写覆盖队长同时修改的标题/截止等字段。
            teamMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Team>()
                    .eq(Team::getId, team.getId())
                    .set(Team::getMemberCount, team.getMemberCount())
                    .set(Team::getStatus, team.getStatus()));
        }
        return actual;
    }

    /**
     * 申请/审批/邀请前的统一检查。抛出 BusinessException 时调用方事务回滚。
     * 推荐服务使用 {@link #joinBlocker} 的静态版本，保证同一套文案与条件。
     */
    public void requireCanJoin(Team team) {
        int actual = actualCount(team.getId());
        Competition competition = team.getCompetitionId() == null
                ? null : competitionMapper.selectById(team.getCompetitionId());
        String blocker = joinBlocker(team, competition, actual, LocalDateTime.now(clock));
        if (blocker != null) {
            throw new BusinessException(blocker);
        }
    }

    /** 纯规则检查，供推荐与申请共用。返回 null 表示可以申请。 */
    public static String joinBlocker(Team team, Competition competition, int actualMembers, LocalDateTime now) {
        if (team == null) return "队伍不存在";
        String status = team.getStatus();
        // 旧数据可能没有状态；按“招募中”处理，但仍受截止与人数约束。
        if (status == null || status.isBlank()) status = "招募中";
        if ("已结束".equals(status)) return "队伍状态为已结束，不再招募";
        if (team.getDeadline() != null && !team.getDeadline().isAfter(now)) {
            return "队伍招募已于 " + team.getDeadline().toLocalDate() + " 截止";
        }
        int max = team.getMaxMembers() == null ? 0 : team.getMaxMembers();
        if (!"招募中".equals(status) && !("已满员".equals(status) && actualMembers < max)) {
            return "队伍当前状态为「" + status + "」，暂不招募";
        }
        if (actualMembers >= max) {
            return "队伍实际成员已达上限 " + max + " 人";
        }
        if (competition != null && competition.getSignupEnd() != null
                && !competition.getSignupEnd().isAfter(now)) {
            return "所属赛事报名已于 " + competition.getSignupEnd().toLocalDate() + " 截止";
        }
        return null;
    }

    private static boolean isRecruiting(String status) {
        return status == null || status.isBlank() || "招募中".equals(status);
    }
}
