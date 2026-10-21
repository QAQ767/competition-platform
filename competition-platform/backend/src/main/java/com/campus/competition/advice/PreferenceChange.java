package com.campus.competition.advice;

import java.util.ArrayList;
import java.util.List;

/** 单条对话产生的结构化需求变更，用于前端展示“本次改动”。 */
public class PreferenceChange {

    private String field;
    private String op;
    private List<String> values = new ArrayList<>();

    public PreferenceChange() { }

    public PreferenceChange(String field, String op, List<String> values) {
        this.field = field;
        this.op = op;
        this.values = values == null ? new ArrayList<>() : values;
    }

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public String getOp() { return op; }
    public void setOp(String op) { this.op = op; }
    public List<String> getValues() { return values; }
    public void setValues(List<String> values) { this.values = values == null ? new ArrayList<>() : values; }

    /** Jackson 会把 getDisplay 序列化为 display，前端可直接展示中文变更。 */
    public String getDisplay() {
        return display();
    }

    public String display() {
        String label = switch (field) {
            case "weeklyHours" -> "每周时间";
            case "directions" -> "方向";
            case "skills" -> "技能";
            case "roles" -> "期望角色";
            case "difficulty" -> "难度偏好";
            case "excludedKeywords" -> "排除项";
            case "windowDays" -> "时间范围";
            case "teamOnly" -> "只看队伍";
            case "outputTarget" -> "推荐目标";
            case "competitionKeywords" -> "指定赛事";
            default -> field;
        };
        String joined = String.join("、", values);
        if ("CLEAR".equals(op)) return label + " 已取消限制";
        if ("REMOVE".equals(op)) return label + " 移除：" + joined;
        if ("SET".equals(op)) return label + " 设为：" + joined;
        return label + " 增加：" + joined;
    }
}
