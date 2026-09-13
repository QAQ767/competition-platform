package com.campus.competition.dto;

import javax.validation.constraints.Email;
import javax.validation.constraints.Size;

/**
 * 个人资料更新请求
 */
public class ProfileReq {

    private String nickname;
    private String intro;
    private String avatar;
    private String college;
    private String major;
    @Email(message = "请输入有效的邮箱地址")
    @Size(max = 100, message = "邮箱不能超过 100 个字符")
    private String email;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getIntro() {
        return intro;
    }

    public void setIntro(String intro) {
        this.intro = intro;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getCollege() {
        return college;
    }

    public void setCollege(String college) {
        this.college = college;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }
}
