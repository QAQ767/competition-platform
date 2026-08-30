package com.campus.competition.controller;

import com.campus.competition.common.Result;
import com.campus.competition.common.UserContext;
import com.campus.competition.dto.ResourceCreateReq;
import com.campus.competition.entity.Resource;
import com.campus.competition.annotation.OpLog;
import com.campus.competition.service.ResourceService;
import com.campus.competition.vo.ResourceVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 训练资料接口
 */
@RestController
@RequestMapping("/api")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping("/teams/{id}/resources")
    public Result<List<ResourceVO>> list(@PathVariable Long id) {
        return Result.success(resourceService.list(id, UserContext.getUserId()));
    }

    @OpLog("上传资料")
    @PostMapping("/teams/{id}/resources")
    public Result<Resource> upload(@PathVariable Long id, @Valid @RequestBody ResourceCreateReq req) {
        return Result.success(resourceService.upload(id, UserContext.getUserId(), req.getTitle(), req.getUrl()));
    }

    @OpLog("删除资料")
    @DeleteMapping("/resources/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        resourceService.delete(id, UserContext.getUserId());
        return Result.success();
    }
}
