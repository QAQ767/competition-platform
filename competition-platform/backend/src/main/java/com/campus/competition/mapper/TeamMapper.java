package com.campus.competition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.competition.entity.Team;

public interface TeamMapper extends BaseMapper<Team> {
    @org.apache.ibatis.annotations.Update("UPDATE team SET status = '已结束' WHERE deadline <= #{now} AND status <> '已结束'")
    int closeExpiredTeams(@org.apache.ibatis.annotations.Param("now") java.time.LocalDateTime now);

    @org.apache.ibatis.annotations.Select("SELECT * FROM team WHERE id = #{id} FOR UPDATE")
    Team selectForUpdate(@org.apache.ibatis.annotations.Param("id") Long id);
}
