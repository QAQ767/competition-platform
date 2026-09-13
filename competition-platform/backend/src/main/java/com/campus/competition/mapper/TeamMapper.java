package com.campus.competition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.competition.entity.Team;

public interface TeamMapper extends BaseMapper<Team> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM team WHERE id = #{id} FOR UPDATE")
    Team selectForUpdate(@org.apache.ibatis.annotations.Param("id") Long id);
}
