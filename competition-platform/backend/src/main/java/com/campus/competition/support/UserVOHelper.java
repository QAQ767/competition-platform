package com.campus.competition.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.competition.entity.SkillTag;
import com.campus.competition.entity.User;
import com.campus.competition.entity.UserSkill;
import com.campus.competition.mapper.SkillTagMapper;
import com.campus.competition.mapper.UserSkillMapper;
import com.campus.competition.vo.SkillVO;
import com.campus.competition.vo.UserVO;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户视图转换助手
 */
public final class UserVOHelper {

    private UserVOHelper() {
    }

    public static UserVO toUserVO(User user, UserSkillMapper userSkillMapper, SkillTagMapper skillTagMapper) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setEmail(user.getEmail());
        vo.setCollege(user.getCollege());
        vo.setMajor(user.getMajor());
        vo.setIntro(user.getIntro());
        vo.setRole(user.getRole());
        vo.setCompeteStatus(user.getCompeteStatus());
        vo.setCreatedAt(user.getCreatedAt());

        List<SkillVO> skills = new ArrayList<>();
        List<UserSkill> links = userSkillMapper.selectList(
                new LambdaQueryWrapper<UserSkill>().eq(UserSkill::getUserId, user.getId()));
        for (UserSkill link : links) {
            SkillTag tag = skillTagMapper.selectById(link.getSkillId());
            if (tag != null) {
                skills.add(new SkillVO(tag.getId(), tag.getName()));
            }
        }
        vo.setSkills(skills);
        return vo;
    }

    /**
     * 获取用户技能名称列表
     */
    public static List<String> toSkillNames(Long userId, UserSkillMapper userSkillMapper, SkillTagMapper skillTagMapper) {
        List<UserSkill> links = userSkillMapper.selectList(
                new LambdaQueryWrapper<UserSkill>().eq(UserSkill::getUserId, userId));
        return links.stream()
                .map(link -> skillTagMapper.selectById(link.getSkillId()))
                .filter(tag -> tag != null)
                .map(SkillTag::getName)
                .collect(Collectors.toList());
    }
}
