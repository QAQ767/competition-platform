package com.campus.competition.controller;

import com.campus.competition.common.Result;
import com.campus.competition.common.UserContext;
import com.campus.competition.dto.CompeteStatusReq;
import com.campus.competition.dto.ProfileReq;
import com.campus.competition.entity.TeamApplication;
import com.campus.competition.service.UserService;
import com.campus.competition.vo.TeamVO;
import com.campus.competition.vo.UserVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户接口
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PutMapping("/profile")
    public Result<UserVO> updateProfile(@RequestBody ProfileReq req) {
        return Result.success(userService.updateProfile(UserContext.getUserId(), req));
    }

    @PutMapping("/compete-status")
    public Result<UserVO> updateCompeteStatus(@RequestBody CompeteStatusReq req) {
        return Result.success(userService.updateCompeteStatus(UserContext.getUserId(), req.getCompeteStatus()));
    }

    /** 更新我的技能标签（按名称，自动创建新标签） */
    @PutMapping("/skills")
    public Result<UserVO> updateSkills(@RequestBody List<String> skillNames) {
        return Result.success(userService.updateSkills(UserContext.getUserId(), skillNames));
    }

    @GetMapping("/me/teams")
    public Result<List<TeamVO>> myTeams() {
        return Result.success(userService.myTeams(UserContext.getUserId()));
    }

    @GetMapping("/me/applications")
    public Result<List<TeamApplication>> myApplications() {
        return Result.success(userService.myApplications(UserContext.getUserId()));
    }
}
