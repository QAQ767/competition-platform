package com.campus.competition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.competition.entity.CompetitionDraft;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface CompetitionDraftMapper extends BaseMapper<CompetitionDraft> {

    @Select("SELECT * FROM competition_draft WHERE id = #{id} FOR UPDATE")
    CompetitionDraft selectForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM competition_draft WHERE normalized_url = #{url} AND id <> #{excludeId} LIMIT 1")
    CompetitionDraft findByNormalizedUrl(@Param("url") String url, @Param("excludeId") Long excludeId);
}
