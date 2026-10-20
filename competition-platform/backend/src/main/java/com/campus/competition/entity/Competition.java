package com.campus.competition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 竞赛信息实体
 */
@TableName("competition")
public class Competition {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String organizer;
    /** 校级 / 省级 / 国家级 */
    private String level;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime signupStart;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime signupEnd;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    private String description;
    /** 报名中 / 即将报名 / 报名即将截止 / 已结束；即将开赛由 startTime 独立判断 */
    private String status;
    /** 方向标签，逗号分隔；由管理员维护，旧数据可为空 */
    private String tags;
    /** 参赛条件原文/摘要；由管理员维护，旧数据可为空 */
    private String eligibilityText;
    /** 赛事年份（可空，不用网页更新时间推算） */
    private Integer competitionYear;
    /** 届次，如“第十二届” */
    private String edition;
    /** 赛道，如“本科组”“算法赛道” */
    private String track;
    /** 发布来源 URL（管理员录入或审核通过的发现草稿） */
    private String sourceUrl;
    /** PLATFORM / OFFICIAL / AGGREGATOR / MANUAL_TEXT / UNKNOWN */
    private String sourceType;
    /**
     * 赛事实例身份键（名称归一化+主办方+年份+届次+赛道 的 SHA-256）。
     * 身份不完整时为 null；唯一索引只用于拦截同一实例的并发重复发布。
     */
    private String identityKey;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOrganizer() {
        return organizer;
    }

    public void setOrganizer(String organizer) {
        this.organizer = organizer;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public LocalDateTime getSignupStart() {
        return signupStart;
    }

    public void setSignupStart(LocalDateTime signupStart) {
        this.signupStart = signupStart;
    }

    public LocalDateTime getSignupEnd() {
        return signupEnd;
    }

    public void setSignupEnd(LocalDateTime signupEnd) {
        this.signupEnd = signupEnd;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getEligibilityText() {
        return eligibilityText;
    }

    public void setEligibilityText(String eligibilityText) {
        this.eligibilityText = eligibilityText;
    }

    public Integer getCompetitionYear() {
        return competitionYear;
    }

    public void setCompetitionYear(Integer competitionYear) {
        this.competitionYear = competitionYear;
    }

    public String getEdition() {
        return edition;
    }

    public void setEdition(String edition) {
        this.edition = edition;
    }

    public String getTrack() {
        return track;
    }

    public void setTrack(String track) {
        this.track = track;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getIdentityKey() {
        return identityKey;
    }

    public void setIdentityKey(String identityKey) {
        this.identityKey = identityKey;
    }
}
