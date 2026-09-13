package com.campus.competition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.competition.common.BusinessException;
import com.campus.competition.dto.ProfileReq;
import com.campus.competition.entity.SkillTag;
import com.campus.competition.entity.Team;
import com.campus.competition.entity.TeamApplication;
import com.campus.competition.entity.TeamMember;
import com.campus.competition.entity.User;
import com.campus.competition.entity.UserSkill;
import com.campus.competition.mapper.CompetitionMapper;
import com.campus.competition.mapper.SkillTagMapper;
import com.campus.competition.mapper.TeamApplicationMapper;
import com.campus.competition.mapper.TeamMapper;
import com.campus.competition.mapper.TeamMemberMapper;
import com.campus.competition.mapper.UserMapper;
import com.campus.competition.mapper.UserSkillMapper;
import com.campus.competition.support.TeamVOHelper;
import com.campus.competition.support.UserVOHelper;
import com.campus.competition.vo.TeamVO;
import com.campus.competition.vo.UserVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户服务：资料维护 / 参赛状态 / 我的队伍 / 我的申请
 */
@Service
public class UserService {

    private static final Set<String> VALID_STATUS = Set.of(
            "WANT_INVITE", "ACCEPT_INVITE", "NOT_PARTICIPATE", "ONLY_VIEW");

    private final UserMapper userMapper;
    private final UserSkillMapper userSkillMapper;
    private final SkillTagMapper skillTagMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final TeamApplicationMapper teamApplicationMapper;
    private final CompetitionMapper competitionMapper;

    public UserService(UserMapper userMapper, UserSkillMapper userSkillMapper, SkillTagMapper skillTagMapper,
                       TeamMapper teamMapper, TeamMemberMapper teamMemberMapper,
                       TeamApplicationMapper teamApplicationMapper, CompetitionMapper competitionMapper) {
        this.userMapper = userMapper;
        this.userSkillMapper = userSkillMapper;
        this.skillTagMapper = skillTagMapper;
        this.teamMapper = teamMapper;
        this.teamMemberMapper = teamMemberMapper;
        this.teamApplicationMapper = teamApplicationMapper;
        this.competitionMapper = competitionMapper;
    }

    @Transactional
    public UserVO updateProfile(Long userId, ProfileReq req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 只更新本次提交的字段，避免头像上传等局部更新覆盖已有资料。
        LambdaUpdateWrapper<User> update = new LambdaUpdateWrapper<User>().eq(User::getId, userId);
        update.set(req.getNickname() != null && !req.getNickname().isBlank(), User::getNickname, req.getNickname());
        update.set(req.getIntro() != null, User::getIntro, req.getIntro());
        update.set(req.getAvatar() != null, User::getAvatar, req.getAvatar());
        update.set(req.getCollege() != null, User::getCollege, req.getCollege());
        update.set(req.getMajor() != null, User::getMajor, req.getMajor());
        update.set(req.getEmail() != null, User::getEmail, req.getEmail());
        if (update.getSqlSet() != null) {
            userMapper.update(null, update);
            user = userMapper.selectById(userId);
        }
        return UserVOHelper.toUserVO(user, userSkillMapper, skillTagMapper);
    }

    public UserVO updateCompeteStatus(Long userId, String competeStatus) {
        if (!VALID_STATUS.contains(competeStatus)) {
            throw new BusinessException("非法的参赛状态: " + competeStatus);
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setCompeteStatus(competeStatus);
        userMapper.updateById(user);
        return UserVOHelper.toUserVO(user, userSkillMapper, skillTagMapper);
    }

    /**
     * 更新用户技能标签：按名称解析/创建 skill_tag，并整体替换 user_skill 关联
     */
    @Transactional
    public UserVO updateSkills(Long userId, List<String> skillNames) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        List<String> names = skillNames == null ? new ArrayList<>()
                : skillNames.stream()
                        .filter(s -> s != null && !s.isBlank())
                        .map(String::trim)
                        .distinct()
                        .collect(Collectors.toList());
        if (names.size() > 20) {
            throw new BusinessException("技能标签最多 20 个");
        }

        List<Long> skillIds = new ArrayList<>();
        for (String name : names) {
            SkillTag tag = skillTagMapper.selectOne(
                    new LambdaQueryWrapper<SkillTag>().eq(SkillTag::getName, name));
            if (tag == null) {
                tag = new SkillTag();
                tag.setName(name);
                skillTagMapper.insert(tag);
            }
            skillIds.add(tag.getId());
        }

        userSkillMapper.delete(new LambdaQueryWrapper<UserSkill>().eq(UserSkill::getUserId, userId));
        for (Long skillId : skillIds) {
            UserSkill link = new UserSkill();
            link.setUserId(userId);
            link.setSkillId(skillId);
            userSkillMapper.insert(link);
        }
        return UserVOHelper.toUserVO(user, userSkillMapper, skillTagMapper);
    }

    public List<TeamVO> myTeams(Long userId) {
        List<Team> teams = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        List<Team> captainTeams = teamMapper.selectList(
                new LambdaQueryWrapper<Team>().eq(Team::getCaptainId, userId));
        for (Team t : captainTeams) {
            teams.add(t);
            seen.add(t.getId());
        }

        List<TeamMember> members = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getUserId, userId));
        for (TeamMember m : members) {
            if (!seen.contains(m.getTeamId())) {
                Team t = teamMapper.selectById(m.getTeamId());
                if (t != null) {
                    teams.add(t);
                    seen.add(t.getId());
                }
            }
        }
        return teams.stream()
                .map(t -> TeamVOHelper.toTeamVO(t, competitionMapper, userMapper))
                .collect(Collectors.toList());
    }

    public List<TeamApplication> myApplications(Long userId) {
        return teamApplicationMapper.selectList(
                new LambdaQueryWrapper<TeamApplication>()
                        .eq(TeamApplication::getUserId, userId)
                        .orderByDesc(TeamApplication::getApplyTime));
    }
}
