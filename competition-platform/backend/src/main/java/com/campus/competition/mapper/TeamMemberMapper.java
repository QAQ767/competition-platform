package com.campus.competition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.competition.entity.TeamMember;

public interface TeamMemberMapper extends BaseMapper<TeamMember> {
    @org.apache.ibatis.annotations.Select("SELECT id FROM team_member WHERE team_id = #{teamId} AND user_id = #{userId} LIMIT 1 FOR UPDATE")
    Long selectCurrentMember(@org.apache.ibatis.annotations.Param("teamId") Long teamId,
                             @org.apache.ibatis.annotations.Param("userId") Long userId);
}
