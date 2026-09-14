package com.campus.competition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.competition.entity.Competition;

public interface CompetitionMapper extends BaseMapper<Competition> {
    @org.apache.ibatis.annotations.Update("""
        UPDATE competition SET status = CASE
          WHEN signup_end <= #{now} THEN '已结束'
          WHEN signup_start > #{now} THEN '即将开始'
          ELSE '报名中' END
        WHERE status IS NULL OR status <> CASE
          WHEN signup_end <= #{now} THEN '已结束'
          WHEN signup_start > #{now} THEN '即将开始'
          ELSE '报名中' END
        """)
    int refreshStatuses(@org.apache.ibatis.annotations.Param("now") java.time.LocalDateTime now);
}
