package com.campus.competition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.competition.common.BusinessException;
import com.campus.competition.common.PageVO;
import com.campus.competition.entity.TrainingCheckin;
import com.campus.competition.entity.TrainingFavorite;
import com.campus.competition.entity.TrainingSite;
import com.campus.competition.entity.User;
import com.campus.competition.mapper.TrainingCheckinMapper;
import com.campus.competition.mapper.TrainingFavoriteMapper;
import com.campus.competition.mapper.TrainingSiteMapper;
import com.campus.competition.mapper.UserMapper;
import com.campus.competition.vo.CheckinVO;
import com.campus.competition.vo.LeaderboardVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 训练模块服务：训练资源站（按竞赛/标签）、一键打卡（累计/连续/等级/日历）、收藏、排行榜
 */
@Service
public class TrainingService {

    private final TrainingSiteMapper siteMapper;
    private final TrainingCheckinMapper checkinMapper;
    private final TrainingFavoriteMapper favoriteMapper;
    private final UserMapper userMapper;

    public TrainingService(TrainingSiteMapper siteMapper, TrainingCheckinMapper checkinMapper,
                           TrainingFavoriteMapper favoriteMapper, UserMapper userMapper) {
        this.siteMapper = siteMapper;
        this.checkinMapper = checkinMapper;
        this.favoriteMapper = favoriteMapper;
        this.userMapper = userMapper;
    }

    // ==================== 训练资源 ====================

    /** 训练资源列表：按竞赛/标签/关键字筛选，仅启用 */
    public PageVO<TrainingSite> listSites(String competition, String tag, String keyword, long page, long size) {
        LambdaQueryWrapper<TrainingSite> qw = new LambdaQueryWrapper<>();
        qw.eq(TrainingSite::getStatus, "启用");
        if (competition != null && !competition.isBlank()) {
            qw.eq(TrainingSite::getCompetition, competition);
        }
        if (tag != null && !tag.isBlank()) {
            qw.like(TrainingSite::getTags, tag);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(TrainingSite::getName, keyword)
                    .or().like(TrainingSite::getDescription, keyword));
        }
        // 推荐的优先展示，再按名称
        qw.orderByDesc(TrainingSite::getRecommended).orderByAsc(TrainingSite::getId);
        Page<TrainingSite> result = siteMapper.selectPage(new Page<>(page, size), qw);
        return new PageVO<>(result.getRecords(), result.getTotal(), result.getCurrent(),
                result.getSize(), result.getPages());
    }

    /** 训练资源全部标签（去重，供筛选区使用） */
    public List<String> listTags() {
        List<TrainingSite> all = siteMapper.selectList(
                new LambdaQueryWrapper<TrainingSite>().eq(TrainingSite::getStatus, "启用"));
        Set<String> tags = new HashSet<>();
        for (TrainingSite s : all) {
            if (s.getTags() != null && !s.getTags().isBlank()) {
                for (String t : s.getTags().split(",")) {
                    if (!t.isBlank()) {
                        tags.add(t.trim());
                    }
                }
            }
        }
        return tags.stream().sorted().collect(Collectors.toList());
    }

    /** 我收藏的训练资源（完整资源对象） */
    public List<TrainingSite> myFavorites(Long userId) {
        List<TrainingFavorite> favs = favoriteMapper.selectList(
                new LambdaQueryWrapper<TrainingFavorite>().eq(TrainingFavorite::getUserId, userId));
        if (favs.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> siteIds = favs.stream().map(TrainingFavorite::getSiteId).collect(Collectors.toList());
        List<TrainingSite> sites = siteMapper.selectBatchIds(siteIds);
        Map<Long, TrainingSite> map = sites.stream().collect(Collectors.toMap(TrainingSite::getId, s -> s));
        List<TrainingSite> ordered = new ArrayList<>();
        for (Long id : siteIds) {
            TrainingSite s = map.get(id);
            if (s != null) {
                ordered.add(s);
            }
        }
        return ordered;
    }

    /** 我收藏的资源 id 集合（用于 ★ 状态） */
    public Set<Long> myFavoriteIds(Long userId) {
        List<TrainingFavorite> favs = favoriteMapper.selectList(
                new LambdaQueryWrapper<TrainingFavorite>().eq(TrainingFavorite::getUserId, userId));
        return favs.stream().map(TrainingFavorite::getSiteId).collect(Collectors.toSet());
    }

    /** 切换收藏，返回是否已收藏 */
    @Transactional
    public boolean toggleFavorite(Long userId, Long siteId) {
        TrainingSite site = siteMapper.selectById(siteId);
        if (site == null) {
            throw new BusinessException("训练资源不存在");
        }
        TrainingFavorite existing = favoriteMapper.selectOne(
                new LambdaQueryWrapper<TrainingFavorite>()
                        .eq(TrainingFavorite::getUserId, userId)
                        .eq(TrainingFavorite::getSiteId, siteId));
        if (existing != null) {
            favoriteMapper.deleteById(existing.getId());
            return false;
        }
        TrainingFavorite fav = new TrainingFavorite();
        fav.setUserId(userId);
        fav.setSiteId(siteId);
        fav.setCreateTime(java.time.LocalDateTime.now());
        favoriteMapper.insert(fav);
        return true;
    }

    // ==================== 训练打卡 ====================

    /** 今日一键打卡（同一天不可重复） */
    @Transactional
    public void checkin(Long userId) {
        LocalDate today = LocalDate.now();
        Long count = checkinMapper.selectCount(
                new LambdaQueryWrapper<TrainingCheckin>()
                        .eq(TrainingCheckin::getUserId, userId)
                        .eq(TrainingCheckin::getCheckinDate, today));
        if (count > 0) {
            throw new BusinessException("今日已打卡");
        }
        TrainingCheckin checkin = new TrainingCheckin();
        checkin.setUserId(userId);
        checkin.setCheckinDate(today);
        checkin.setCreateTime(java.time.LocalDateTime.now());
        checkinMapper.insert(checkin);
    }

    /** 今日打卡状态 + 累计/连续天数 + 等级 + 本月打卡日期（日历渲染用） */
    public CheckinVO checkinToday(Long userId) {
        List<LocalDate> dates = checkinDates(userId);
        LocalDate today = LocalDate.now();
        boolean checkedToday = dates.contains(today);

        int totalDays = dates.size();
        int streak = computeStreak(dates, today);
        CheckinVO.Level level = levelOf(totalDays);

        YearMonth month = YearMonth.now();
        List<String> monthDates = dates.stream()
                .filter(d -> YearMonth.from(d).equals(month))
                .sorted()
                .map(LocalDate::toString)
                .collect(Collectors.toList());

        CheckinVO vo = new CheckinVO();
        vo.setCheckedToday(checkedToday);
        vo.setTotalDays(totalDays);
        vo.setStreakDays(streak);
        vo.setLevel(level.name);
        vo.setNextLevel(level.nextName);
        vo.setNextLevelNeed(level.nextNeed == null ? 0 : level.nextNeed - totalDays);
        vo.setRank(myRank(userId, totalDays));
        vo.setMonthDates(monthDates);
        return vo;
    }

    /** 打卡排行榜 top10（all / month） */
    public List<LeaderboardVO> leaderboard(String range) {
        LocalDate from = null;
        if ("month".equalsIgnoreCase(range)) {
            from = YearMonth.now().atDay(1);
        }
        LambdaQueryWrapper<TrainingCheckin> qw = new LambdaQueryWrapper<>();
        if (from != null) {
            qw.ge(TrainingCheckin::getCheckinDate, from);
        }
        List<TrainingCheckin> all = checkinMapper.selectList(qw);
        Map<Long, Long> counts = new LinkedHashMap<>();
        all.stream().collect(Collectors.groupingBy(TrainingCheckin::getUserId, LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> counts.put(e.getKey(), e.getValue()));

        List<LeaderboardVO> result = new ArrayList<>();
        Map<Long, User> users = userMapper.selectBatchIds(counts.keySet()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        int rank = 1;
        for (Map.Entry<Long, Long> e : counts.entrySet()) {
            User u = users.get(e.getKey());
            LeaderboardVO vo = new LeaderboardVO();
            vo.setRank(rank);
            vo.setUserId(e.getKey());
            vo.setNickname(u == null ? "未知" : u.getNickname());
            vo.setAvatar(u == null ? null : u.getAvatar());
            vo.setDays(e.getValue().intValue());
            result.add(vo);
            rank++;
        }
        return result;
    }

    // ==================== 管理员：训练资源管理 ====================

    public PageVO<TrainingSite> adminList(String keyword, long page, long size) {
        LambdaQueryWrapper<TrainingSite> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.like(TrainingSite::getName, keyword);
        }
        qw.orderByDesc(TrainingSite::getId);
        Page<TrainingSite> result = siteMapper.selectPage(new Page<>(page, size), qw);
        return new PageVO<>(result.getRecords(), result.getTotal(), result.getCurrent(),
                result.getSize(), result.getPages());
    }

    public TrainingSite createSite(TrainingSite site) {
        if (site.getName() == null || site.getName().isBlank()) {
            throw new BusinessException("资源名不能为空");
        }
        if (site.getUrl() == null || site.getUrl().isBlank()) {
            throw new BusinessException("资源链接不能为空");
        }
        if (site.getStatus() == null) {
            site.setStatus("启用");
        }
        if (site.getDifficulty() == null) {
            site.setDifficulty("入门");
        }
        if (site.getRecommended() == null) {
            site.setRecommended(false);
        }
        site.setId(null);
        site.setCreatedAt(java.time.LocalDateTime.now());
        siteMapper.insert(site);
        return site;
    }

    @Transactional
    public TrainingSite updateSite(Long id, TrainingSite site) {
        TrainingSite exist = siteMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("训练资源不存在");
        }
        if (site.getName() != null && !site.getName().isBlank()) {
            exist.setName(site.getName());
        }
        if (site.getUrl() != null && !site.getUrl().isBlank()) {
            exist.setUrl(site.getUrl());
        }
        exist.setTags(site.getTags());
        exist.setCompetition(site.getCompetition());
        exist.setDifficulty(site.getDifficulty());
        exist.setDescription(site.getDescription());
        if (site.getRecommended() != null) {
            exist.setRecommended(site.getRecommended());
        }
        if (site.getStatus() != null) {
            exist.setStatus(site.getStatus());
        }
        siteMapper.updateById(exist);
        return exist;
    }

    public void deleteSite(Long id) {
        siteMapper.deleteById(id);
        favoriteMapper.delete(new LambdaQueryWrapper<TrainingFavorite>().eq(TrainingFavorite::getSiteId, id));
    }

    // ==================== 内部工具 ====================

    private List<LocalDate> checkinDates(Long userId) {
        List<TrainingCheckin> list = checkinMapper.selectList(
                new LambdaQueryWrapper<TrainingCheckin>().eq(TrainingCheckin::getUserId, userId));
        return list.stream().map(TrainingCheckin::getCheckinDate).distinct().collect(Collectors.toList());
    }

    /** 连续打卡天数：从今天（或昨天）往前数连续记录 */
    private int computeStreak(List<LocalDate> dates, LocalDate today) {
        Set<LocalDate> set = new HashSet<>(dates);
        int streak = 0;
        LocalDate cursor = set.contains(today) ? today : today.minusDays(1);
        while (set.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /** 累计天数 → 等级 */
    private CheckinVO.Level levelOf(int total) {
        if (total >= 300) {
            return new CheckinVO.Level("宗师", null, null);
        }
        if (total >= 100) {
            return new CheckinVO.Level("大师", "宗师", 300);
        }
        if (total >= 30) {
            return new CheckinVO.Level("熟练", "大师", 100);
        }
        if (total >= 7) {
            return new CheckinVO.Level("进阶", "熟练", 30);
        }
        return new CheckinVO.Level("见习", "进阶", 7);
    }

    /** 我的排行名次（按累计天数，同名次位次并列取先后） */
    private int myRank(Long userId, int myDays) {
        List<TrainingCheckin> all = checkinMapper.selectList(null);
        Map<Long, Long> counts = all.stream().collect(Collectors.groupingBy(TrainingCheckin::getUserId, Collectors.counting()));
        long higher = counts.values().stream().filter(c -> c > myDays).count();
        return (int) higher + 1;
    }
}
