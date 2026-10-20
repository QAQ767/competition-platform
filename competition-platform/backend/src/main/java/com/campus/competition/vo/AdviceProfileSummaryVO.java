package com.campus.competition.vo;

import java.util.ArrayList;
import java.util.List;

/** 参赛推荐页顶部的档案摘要，不含邮箱等联系方式。 */
public class AdviceProfileSummaryVO {

    private String nickname;
    private String college;
    private String major;
    private List<String> skills = new ArrayList<>();
    private String preferredRoles;
    private Integer weeklyHours;
    private boolean weeklyHoursKnown;
    private String competeStatus;
    private boolean hasProjectExperience;

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getCollege() { return college; }
    public void setCollege(String college) { this.college = college; }
    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }
    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }
    public String getPreferredRoles() { return preferredRoles; }
    public void setPreferredRoles(String preferredRoles) { this.preferredRoles = preferredRoles; }
    public Integer getWeeklyHours() { return weeklyHours; }
    public void setWeeklyHours(Integer weeklyHours) { this.weeklyHours = weeklyHours; }
    public boolean isWeeklyHoursKnown() { return weeklyHoursKnown; }
    public void setWeeklyHoursKnown(boolean weeklyHoursKnown) { this.weeklyHoursKnown = weeklyHoursKnown; }
    public String getCompeteStatus() { return competeStatus; }
    public void setCompeteStatus(String competeStatus) { this.competeStatus = competeStatus; }
    public boolean isHasProjectExperience() { return hasProjectExperience; }
    public void setHasProjectExperience(boolean hasProjectExperience) { this.hasProjectExperience = hasProjectExperience; }
}
