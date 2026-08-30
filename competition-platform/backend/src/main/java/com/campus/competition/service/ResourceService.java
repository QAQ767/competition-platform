package com.campus.competition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.competition.common.BusinessException;
import com.campus.competition.entity.Resource;
import com.campus.competition.entity.Team;
import com.campus.competition.entity.TeamMember;
import com.campus.competition.entity.User;
import com.campus.competition.mapper.ResourceMapper;
import com.campus.competition.mapper.TeamMapper;
import com.campus.competition.mapper.TeamMemberMapper;
import com.campus.competition.mapper.UserMapper;
import com.campus.competition.vo.ResourceVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 训练资料服务（队伍成员共享，上传/查看/删除有权限控制）
 */
@Service
public class ResourceService {

    private final ResourceMapper resourceMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final TeamMapper teamMapper;
    private final UserMapper userMapper;

    public ResourceService(ResourceMapper resourceMapper, TeamMemberMapper teamMemberMapper,
                           TeamMapper teamMapper, UserMapper userMapper) {
        this.resourceMapper = resourceMapper;
        this.teamMemberMapper = teamMemberMapper;
        this.teamMapper = teamMapper;
        this.userMapper = userMapper;
    }

    public boolean isMember(Long teamId, Long userId) {
        return teamMemberMapper.selectCount(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getUserId, userId)) > 0;
    }

    public Resource upload(Long teamId, Long userId, String title, String url) {
        if (teamMapper.selectById(teamId) == null) {
            throw new BusinessException("队伍不存在");
        }
        if (!isMember(teamId, userId)) {
            throw new BusinessException("仅队伍成员可上传资料");
        }
        Resource resource = new Resource();
        resource.setTeamId(teamId);
        resource.setName(title);
        resource.setUrl(url);
        resource.setType(typeOf(url));
        resource.setUploaderId(userId);
        resource.setCreatedAt(LocalDateTime.now());
        resourceMapper.insert(resource);
        return resource;
    }

    public List<ResourceVO> list(Long teamId, Long userId) {
        if (!isMember(teamId, userId)) {
            throw new BusinessException("仅队伍成员可查看训练资料");
        }
        return resourceMapper.selectList(
                        new LambdaQueryWrapper<Resource>()
                                .eq(Resource::getTeamId, teamId)
                                .orderByDesc(Resource::getId))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    public void delete(Long id, Long userId) {
        Resource resource = resourceMapper.selectById(id);
        if (resource == null) {
            throw new BusinessException("资料不存在");
        }
        Team team = teamMapper.selectById(resource.getTeamId());
        boolean isUploader = resource.getUploaderId().equals(userId);
        boolean isCaptain = team != null && team.getCaptainId().equals(userId);
        if (!isUploader && !isCaptain) {
            throw new BusinessException("仅上传者或队长可删除资料");
        }
        resourceMapper.deleteById(id);
    }

    private ResourceVO toVO(Resource resource) {
        ResourceVO vo = new ResourceVO();
        vo.setId(resource.getId());
        vo.setTeamId(resource.getTeamId());
        vo.setName(resource.getName());
        vo.setUrl(resource.getUrl());
        vo.setType(resource.getType());
        vo.setUploaderId(resource.getUploaderId());
        vo.setCreatedAt(resource.getCreatedAt());
        User uploader = userMapper.selectById(resource.getUploaderId());
        vo.setUploaderName(uploader == null ? "未知" : uploader.getNickname());
        return vo;
    }

    private String typeOf(String url) {
        if (url == null) {
            return "其他";
        }
        String lower = url.toLowerCase();
        if (lower.matches(".*\\.(png|jpe?g|gif|webp|bmp)$")) {
            return "图片";
        }
        if (lower.matches(".*\\.(docx?|pdf|txt|md|pptx?|xlsx?)$")) {
            return "文档";
        }
        if (lower.matches(".*\\.(zip|rar|7z|tar|gz)$")) {
            return "压缩包";
        }
        return "其他";
    }
}
