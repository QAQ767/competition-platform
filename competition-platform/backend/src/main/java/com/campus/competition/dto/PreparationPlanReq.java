package com.campus.competition.dto;

public class PreparationPlanReq {
    /** 可选；留空使用目标竞赛的开赛时间。 */
    private String targetDate;
    public String getTargetDate() { return targetDate; }
    public void setTargetDate(String targetDate) { this.targetDate = targetDate; }
}
