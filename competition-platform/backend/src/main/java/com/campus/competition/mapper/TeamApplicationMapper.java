package com.campus.competition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.competition.entity.TeamApplication;

public interface TeamApplicationMapper extends BaseMapper<TeamApplication> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM team_application WHERE id = #{id} FOR UPDATE")
    TeamApplication selectForUpdate(@org.apache.ibatis.annotations.Param("id") Long id);
}
