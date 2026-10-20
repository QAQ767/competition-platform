package com.campus.competition.advice;

import com.campus.competition.entity.User;
import com.campus.competition.support.TextSignals;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 推荐所需的最小档案快照。不包含密码、邮箱、令牌等敏感字段。 */
public class ParticipantProfile {

    private Long userId;
    private String nickname;
    private String college;
    private String major;
    private String intro;
    private String projectExperience;
    private String preferredRoles;
    private Integer weeklyHours;
    private String competeStatus;
    private List<String> skills = new ArrayList<>();
    private List<String> achievements = new ArrayList<>();

    public static ParticipantProfile of(User user, List<String> skills, List<String> achievements) {
        ParticipantProfile profile = new ParticipantProfile();
        profile.userId = user.getId();
        profile.nickname = user.getNickname();
        profile.college = user.getCollege();
        profile.major = user.getMajor();
        profile.intro = user.getIntro();
        profile.projectExperience = user.getProjectExperience();
        profile.preferredRoles = user.getPreferredRoles();
        profile.weeklyHours = user.getWeeklyHours();
        profile.competeStatus = user.getCompeteStatus();
        profile.skills = skills == null ? new ArrayList<>() : new ArrayList<>(skills);
        profile.achievements = achievements == null ? new ArrayList<>() : new ArrayList<>(achievements);
        return profile;
    }

    /** 仅用于返回前的时间/状态二次校验，不参与打分。 */
    public static ParticipantProfile forValidation(Long userId) {
        ParticipantProfile profile = new ParticipantProfile();
        profile.userId = userId;
        return profile;
    }

    /** 能力方向 = 技能标签 + 期望角色 + 简介/项目经历中的弱信号。 */
    public Set<String> capabilities() {
        return TextSignals.capabilities(
                String.join(",", skills), preferredRoles, intro, projectExperience,
                String.join(",", achievements));
    }

    /** 用户明确写下的技能标签，不含推断。 */
    public Set<String> declaredSkills() {
        return new LinkedHashSet<>(skills);
    }

    public boolean hasProjectEvidence() {
        return (projectExperience != null && !projectExperience.isBlank())
                || !achievements.isEmpty();
    }

    /** weeklyHours=0 在现有系统表示“未确定”，不能当成完全没有时间。 */
    public Integer effectiveWeeklyHours() {
        return weeklyHours == null || weeklyHours <= 0 ? null : weeklyHours;
    }

    public List<String> missingFields() {
        List<String> missing = new ArrayList<>();
        if (skills.isEmpty()) missing.add("技能标签");
        if (isBlank(preferredRoles)) missing.add("期望角色");
        if (effectiveWeeklyHours() == null) missing.add("每周可投入时间（0 表示未确定）");
        if (isBlank(projectExperience)) missing.add("项目经历");
        if (isBlank(intro)) missing.add("个人简介");
        if (isBlank(major)) missing.add("专业");
        if (isBlank(college)) missing.add("学院");
        return missing;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public Long getUserId() { return userId; }
    public String getNickname() { return nickname; }
    public String getCollege() { return college; }
    public String getMajor() { return major; }
    public String getIntro() { return intro; }
    public String getProjectExperience() { return projectExperience; }
    public String getPreferredRoles() { return preferredRoles; }
    public Integer getWeeklyHours() { return weeklyHours; }
    public String getCompeteStatus() { return competeStatus; }
    public List<String> getSkills() { return skills; }
    public List<String> getAchievements() { return achievements; }
}
