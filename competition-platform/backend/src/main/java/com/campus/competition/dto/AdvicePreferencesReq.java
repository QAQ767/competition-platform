package com.campus.competition.dto;

import com.campus.competition.advice.AdvicePreferences;

/** 手动确认/编辑需求摘要，必须带当前版本号。 */
public class AdvicePreferencesReq {

    private Integer version;
    private AdvicePreferences preferences;

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public AdvicePreferences getPreferences() { return preferences; }
    public void setPreferences(AdvicePreferences preferences) { this.preferences = preferences; }
}
