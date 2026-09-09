package com.campus.competition.vo;

import java.util.List;

/**
 * 打卡状态视图：今日是否已打 + 累计/连续天数 + 等级 + 本月打卡日期（日历渲染用）
 */
public class CheckinVO {

    private boolean checkedToday;
    private int totalDays;
    private int streakDays;
    private String level;
    private String nextLevel;
    private int nextLevelNeed;
    private int rank;
    private List<String> monthDates;

    public boolean isCheckedToday() {
        return checkedToday;
    }

    public void setCheckedToday(boolean checkedToday) {
        this.checkedToday = checkedToday;
    }

    public int getTotalDays() {
        return totalDays;
    }

    public void setTotalDays(int totalDays) {
        this.totalDays = totalDays;
    }

    public int getStreakDays() {
        return streakDays;
    }

    public void setStreakDays(int streakDays) {
        this.streakDays = streakDays;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getNextLevel() {
        return nextLevel;
    }

    public void setNextLevel(String nextLevel) {
        this.nextLevel = nextLevel;
    }

    public int getNextLevelNeed() {
        return nextLevelNeed;
    }

    public void setNextLevelNeed(int nextLevelNeed) {
        this.nextLevelNeed = nextLevelNeed;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public List<String> getMonthDates() {
        return monthDates;
    }

    public void setMonthDates(List<String> monthDates) {
        this.monthDates = monthDates;
    }

    /** 打卡等级定义 */
    public static class Level {
        public final String name;
        public final String nextName;
        public final Integer nextNeed;

        public Level(String name, String nextName, Integer nextNeed) {
            this.name = name;
            this.nextName = nextName;
            this.nextNeed = nextNeed;
        }
    }
}
