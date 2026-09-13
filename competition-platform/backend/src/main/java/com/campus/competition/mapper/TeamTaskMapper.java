package com.campus.competition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.competition.entity.TeamTask;

public interface TeamTaskMapper extends BaseMapper<TeamTask> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM team_task WHERE id = #{id} FOR UPDATE")
    TeamTask selectForUpdate(@org.apache.ibatis.annotations.Param("id") Long id);
}
