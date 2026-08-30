package com.campus.competition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.competition.common.BusinessException;
import com.campus.competition.common.PageVO;
import com.campus.competition.entity.OperationLog;
import com.campus.competition.entity.User;
import com.campus.competition.mapper.OperationLogMapper;
import com.campus.competition.mapper.UserMapper;
import com.campus.competition.vo.OperationLogVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 操作日志服务：记录与查询（仅管理员）
 */
@Service
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;
    private final UserMapper userMapper;

    public OperationLogService(OperationLogMapper operationLogMapper, UserMapper userMapper) {
        this.operationLogMapper = operationLogMapper;
        this.userMapper = userMapper;
    }

    public void record(Long userId, String action, String detail) {
        if (userId == null) {
            return;
        }
        OperationLog log = new OperationLog();
        log.setUserId(userId);
        log.setAction(action);
        log.setDetail(detail);
        log.setCreatedAt(LocalDateTime.now());
        operationLogMapper.insert(log);
    }

    public PageVO<OperationLogVO> page(Long operatorId, long page, long size) {
        User operator = userMapper.selectById(operatorId);
        if (operator == null || !"ADMIN".equals(operator.getRole())) {
            throw new BusinessException("需要管理员权限");
        }
        Page<OperationLog> result = operationLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<OperationLog>().orderByDesc(OperationLog::getId));
        List<OperationLogVO> records = result.getRecords().stream()
                .map(this::toVO).collect(Collectors.toList());
        return new PageVO<>(records, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    private OperationLogVO toVO(OperationLog log) {
        OperationLogVO vo = new OperationLogVO();
        vo.setId(log.getId());
        vo.setUserId(log.getUserId());
        vo.setAction(log.getAction());
        vo.setDetail(log.getDetail());
        vo.setCreateTime(log.getCreatedAt());
        User user = userMapper.selectById(log.getUserId());
        vo.setUserName(user == null ? "未知" : user.getNickname());
        return vo;
    }
}
