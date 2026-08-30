package com.campus.competition.seed;

import com.campus.competition.entity.Achievement;
import com.campus.competition.entity.ChatMessage;
import com.campus.competition.entity.Competition;
import com.campus.competition.entity.Feedback;
import com.campus.competition.entity.Notification;
import com.campus.competition.entity.SkillTag;
import com.campus.competition.entity.Team;
import com.campus.competition.entity.TeamApplication;
import com.campus.competition.entity.TeamDiscussion;
import com.campus.competition.entity.TeamMember;
import com.campus.competition.entity.User;
import com.campus.competition.entity.UserSkill;
import com.campus.competition.mapper.AchievementMapper;
import com.campus.competition.mapper.ChatMessageMapper;
import com.campus.competition.mapper.CompetitionMapper;
import com.campus.competition.mapper.FeedbackMapper;
import com.campus.competition.mapper.NotificationMapper;
import com.campus.competition.mapper.SkillTagMapper;
import com.campus.competition.mapper.TeamApplicationMapper;
import com.campus.competition.mapper.TeamDiscussionMapper;
import com.campus.competition.mapper.TeamMapper;
import com.campus.competition.mapper.TeamMemberMapper;
import com.campus.competition.mapper.UserMapper;
import com.campus.competition.mapper.UserSkillMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示数据初始化：仅当 users 表为空时执行。
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserMapper userMapper;
    private final SkillTagMapper skillTagMapper;
    private final UserSkillMapper userSkillMapper;
    private final CompetitionMapper competitionMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final TeamApplicationMapper teamApplicationMapper;
    private final NotificationMapper notificationMapper;
    private final AchievementMapper achievementMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final FeedbackMapper feedbackMapper;
    private final TeamDiscussionMapper teamDiscussionMapper;

    public DataSeeder(UserMapper userMapper, SkillTagMapper skillTagMapper, UserSkillMapper userSkillMapper,
                      CompetitionMapper competitionMapper, TeamMapper teamMapper, TeamMemberMapper teamMemberMapper,
                      TeamApplicationMapper teamApplicationMapper, NotificationMapper notificationMapper,
                      AchievementMapper achievementMapper,
                      ChatMessageMapper chatMessageMapper, FeedbackMapper feedbackMapper,
                      TeamDiscussionMapper teamDiscussionMapper) {
        this.userMapper = userMapper;
        this.skillTagMapper = skillTagMapper;
        this.userSkillMapper = userSkillMapper;
        this.competitionMapper = competitionMapper;
        this.teamMapper = teamMapper;
        this.teamMemberMapper = teamMemberMapper;
        this.teamApplicationMapper = teamApplicationMapper;
        this.notificationMapper = notificationMapper;
        this.achievementMapper = achievementMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.feedbackMapper = feedbackMapper;
        this.teamDiscussionMapper = teamDiscussionMapper;
    }

    @Override
    public void run(String... args) {
        if (userMapper.selectCount(null) == 0) {
            log.info("开始初始化核心演示数据...");
            seedSkills();
            Map<String, Long> users = seedUsers();
            seedUserSkills(users);
            Map<String, Long> competitions = seedCompetitions();
            Map<String, Long> teams = seedTeamsAndMembers(users, competitions);
            seedApplicationsAndNotifications(users, teams);
            seedAchievements(users, competitions);
            log.info("核心演示数据初始化完成");
        } else {
            log.info("核心演示数据已存在，跳过");
        }
        // 大厅聊天 / 反馈 / 团队讨论独立播种：即使核心数据已存在也会补齐
        if (chatMessageMapper.selectCount(null) == 0) {
            seedHallChat();
        }
        if (feedbackMapper.selectCount(null) == 0) {
            seedFeedback();
        }
        if (teamDiscussionMapper.selectCount(null) == 0) {
            seedTeamDiscussions();
        }
    }

    private void seedSkills() {
        String[] names = {"算法", "前端", "后端", "UI设计", "文档写作", "数据分析", "机器学习", "答辩演讲"};
        for (String name : names) {
            SkillTag tag = new SkillTag();
            tag.setName(name);
            skillTagMapper.insert(tag);
        }
    }

    private Map<String, Long> seedUsers() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String pwd = encoder.encode("123456");
        Object[][] data = {
                {"student1", "小林", "计算机学院", "软件工程", "热爱算法与Web开发，多次参加校内比赛", "STUDENT", "WANT_INVITE"},
                {"student2", "小陈", "计算机学院", "数据科学与大数据技术", "数据分析方向，熟悉Python与可视化", "STUDENT", "WANT_INVITE"},
                {"student3", "小张", "计算机学院", "软件工程", "前端开发与UI设计爱好者", "STUDENT", "ACCEPT_INVITE"},
                {"student4", "小李", "信息学院", "物联网工程", "后端与嵌入式开发，喜欢折腾", "STUDENT", "ACCEPT_INVITE"},
                {"student5", "小王", "计算机学院", "软件工程", "正在准备考研，偶尔参加算法赛", "STUDENT", "NOT_PARTICIPATE"},
                {"student6", "小刘", "外国语学院", "英语", "写作能力突出，擅长文档与答辩", "STUDENT", "NOT_PARTICIPATE"},
                {"student7", "小赵", "计算机学院", "软件工程", "只看看不参赛", "STUDENT", "ONLY_VIEW"},
                {"admin1", "管理员", "教务处", "竞赛管理", "平台管理员", "ADMIN", "ONLY_VIEW"},
        };
        Map<String, Long> idMap = new HashMap<>();
        for (Object[] row : data) {
            User user = new User();
            user.setUsername((String) row[0]);
            user.setPassword(pwd);
            user.setNickname((String) row[1]);
            user.setCollege((String) row[2]);
            user.setMajor((String) row[3]);
            user.setIntro((String) row[4]);
            user.setRole((String) row[5]);
            user.setCompeteStatus((String) row[6]);
            user.setCreatedAt(LocalDateTime.now().minusDays(20));
            userMapper.insert(user);
            idMap.put((String) row[0], user.getId());
        }
        return idMap;
    }

    private void seedUserSkills(Map<String, Long> users) {
        Object[][] data = {
                {"student1", "算法", "后端"},
                {"student2", "数据分析", "机器学习"},
                {"student3", "前端", "UI设计"},
                {"student4", "后端", "数据分析"},
                {"student5", "算法"},
                {"student6", "文档写作"},
                {"student7", "算法", "前端"},
        };
        Map<String, Long> skillIds = skillIds();
        for (Object[] row : data) {
            for (int i = 1; i < row.length; i++) {
                UserSkill us = new UserSkill();
                us.setUserId(users.get(row[0]));
                us.setSkillId(skillIds.get(row[i]));
                userSkillMapper.insert(us);
            }
        }
    }

    private Map<String, Long> seedCompetitions() {
        LocalDateTime now = LocalDateTime.now();
        Object[][] data = {
                {"数学建模", "中国工业与应用数学学会", "国家级", now.minusDays(14), now.plusDays(17), now.plusDays(26), "全国大学生数学建模竞赛（CUMCM），三人一组，72小时完成建模论文。"},
                {"蓝桥杯", "工业和信息化部人才交流中心", "省级", now.plusDays(30), now.plusDays(75), now.plusDays(110), "蓝桥杯全国软件和信息技术专业人才大赛，个人赛与团队赛。"},
                {"ACM校赛", "校计算机学院", "校级", now.minusDays(5), now.plusDays(10), now.plusDays(15), "ACM-ICPC 校内选拔赛，算法与数据结构巅峰对决。"},
                {"互联网+", "教育部", "国家级", now.minusDays(160), now.minusDays(100), now.minusDays(90), "中国国际大学生创新大赛（原互联网+），创新创业项目路演。"},
                {"挑战杯", "共青团中央", "省级", now.plusDays(45), now.plusDays(105), now.plusDays(120), "挑战杯全国大学生课外学术科技作品竞赛。"},
        };
        Map<String, Long> idMap = new HashMap<>();
        for (Object[] row : data) {
            Competition c = new Competition();
            c.setName((String) row[0]);
            c.setOrganizer((String) row[1]);
            c.setLevel((String) row[2]);
            c.setSignupStart((LocalDateTime) row[3]);
            c.setSignupEnd((LocalDateTime) row[4]);
            c.setStartTime((LocalDateTime) row[5]);
            c.setDescription((String) row[6]);
            c.setStatus(computeStatus((LocalDateTime) row[3], (LocalDateTime) row[4]));
            competitionMapper.insert(c);
            idMap.put((String) row[0], c.getId());
        }
        return idMap;
    }

    private Map<String, Long> seedTeamsAndMembers(Map<String, Long> users, Map<String, Long> competitions) {
        Map<String, Long> teamIds = new HashMap<>();
        Object[][] data = {
                {"数学建模", "student1", "数学建模组队招人（缺数据分析）", "三人一组，已有队长（算法+后端），缺数据分析与论文写作的同学", 3, "数据分析,文档写作", "招募中", 10},
                {"ACM校赛", "student5", "ACM 校赛算法队", "目标进校队，日常刷题训练", 3, "算法", "招募中", 9},
                {"互联网+", "student3", "互联网+ 创新创业队", "项目已成型，核心成员齐全", 5, "前端,UI设计,答辩演讲", "已满员", 5},
                {"挑战杯", "student4", "挑战杯备赛队伍", "社科学术作品，需要数据分析与写作", 4, "数据分析,文档写作", "备赛中", 6},
                {"蓝桥杯", "student6", "蓝桥杯算法练习队", "一起刷题备战蓝桥杯", 3, "算法,后端", "招募中", 12},
        };
        for (Object[] row : data) {
            Team team = new Team();
            team.setCompetitionId(competitions.get(row[0]));
            team.setCaptainId(users.get(row[1]));
            team.setTitle((String) row[2]);
            team.setDescription((String) row[3]);
            team.setMaxMembers((Integer) row[4]);
            team.setSkills((String) row[5]);
            team.setStatus((String) row[6]);
            team.setDeadline(LocalDateTime.now().plusDays((Integer) row[7]));
            team.setCreatedAt(LocalDateTime.now().minusDays((Integer) row[7]));
            team.setMemberCount(1);
            teamMapper.insert(team);
            teamIds.put((String) row[0], team.getId());

            // 队长入队
            TeamMember captain = new TeamMember();
            captain.setTeamId(team.getId());
            captain.setUserId(users.get(row[1]));
            captain.setUserName(nickname(users.get(row[1])));
            captain.setRole("队长");
            captain.setJoinedTime(LocalDateTime.now().minusDays((Integer) row[7]));
            teamMemberMapper.insert(captain);

            // 预置部分队员
            if ("ACM校赛".equals(row[0])) {
                addMember(team, users.get("student7"), "队员", 6);
            } else if ("互联网+".equals(row[0])) {
                addMember(team, users.get("student1"), "队员", 80);
                addMember(team, users.get("student2"), "队员", 60);
                addMember(team, users.get("student4"), "队员", 40);
                addMember(team, users.get("student6"), "队员", 20);
            } else if ("挑战杯".equals(row[0])) {
                addMember(team, users.get("student2"), "队员", 30);
                addMember(team, users.get("student6"), "队员", 10);
            } else if ("蓝桥杯".equals(row[0])) {
                addMember(team, users.get("student5"), "队员", 5);
            }
            team.setMemberCount(countMembers(team.getId()));
            teamMapper.updateById(team);
        }
        return teamIds;
    }

    private void seedApplicationsAndNotifications(Map<String, Long> users, Map<String, Long> teams) {
        // student2 申请加入 数学建模队
        TeamApplication app1 = new TeamApplication();
        app1.setTeamId(teams.get("数学建模"));
        app1.setUserId(users.get("student2"));
        app1.setUserName(nickname(users.get("student2")));
        app1.setIntro("我是数据分析方向的，会Python和可视化，求带！");
        app1.setStatus("待审批");
        app1.setInvited(false);
        app1.setApplyTime(LocalDateTime.now().minusHours(3));
        teamApplicationMapper.insert(app1);

        // student7 申请加入 蓝桥杯队
        TeamApplication app2 = new TeamApplication();
        app2.setTeamId(teams.get("蓝桥杯"));
        app2.setUserId(users.get("student7"));
        app2.setUserName(nickname(users.get("student7")));
        app2.setIntro("算法还行，想找队友一起刷题。");
        app2.setStatus("待审批");
        app2.setInvited(false);
        app2.setApplyTime(LocalDateTime.now().minusHours(1));
        teamApplicationMapper.insert(app2);

        send(users.get("student1"), "APPLY", "小陈 申请加入你的队伍「数学建模组队招人（缺数据分析）」");
        send(users.get("student1"), "SYSTEM", "欢迎使用竞赛组队平台！完善个人资料与技能标签，更容易被 AI 推荐哦");
        send(users.get("student2"), "SYSTEM", "欢迎加入竞赛组队平台，开启你的竞赛之旅");
        send(users.get("student6"), "APPLY", "小赵 申请加入你的队伍「蓝桥杯算法练习队」");
        send(users.get("student7"), "SYSTEM", "欢迎使用竞赛组队平台");
    }

    /** 大厅聊天演示消息（独立播种） */
    private void seedHallChat() {
        List<User> allUsers = userMapper.selectList(null);
        Object[][] msgs = {
                {"student1", "有没有人一起打数学建模？队伍缺数据分析！"},
                {"student3", "蓝桥杯快开始了，组队刷题走起~"},
                {"student2", "我可以做数据分析，数学建模求带走！"},
                {"student6", "挑战杯找会写文档的队友，报酬是奶茶一杯"},
                {"student4", "ACM校赛有人组队吗？我是后端"},
                {"student7", "围观学习.jpg"},
        };
        for (int i = 0; i < msgs.length; i++) {
            String username = (String) msgs[i][0];
            User sender = allUsers.stream()
                    .filter(u -> u.getUsername().equals(username))
                    .findFirst().orElse(null);
            if (sender == null) {
                continue;
            }
            ChatMessage m = new ChatMessage();
            m.setRoomType("HALL");
            m.setUserId(sender.getId());
            m.setUserName(sender.getNickname());
            m.setContent((String) msgs[i][1]);
            m.setIsRead(true);
            m.setCreateTime(LocalDateTime.now().minusMinutes((long) (msgs.length - i) * 7));
            chatMessageMapper.insert(m);
        }
        log.info("大厅聊天演示消息已生成");
    }

    /** 示例反馈（独立播种） */
    private void seedFeedback() {
        User user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getUsername, "student2"));
        if (user == null) {
            return;
        }
        Feedback feedback = new Feedback();
        feedback.setUserId(user.getId());
        feedback.setUserName(user.getNickname());
        feedback.setType("赛事申请");
        feedback.setContent("想参加「全国大学生服务外包创新创业大赛」，平台里没有这个赛事，希望管理员添加，谢谢！");
        feedback.setStatus("待处理");
        feedback.setCreateTime(LocalDateTime.now().minusDays(1));
        feedbackMapper.insert(feedback);
        log.info("示例反馈已生成");
    }

    /** 团队讨论区演示消息（独立播种，选"互联网+"队展示多人群聊） */
    private void seedTeamDiscussions() {
        Team team = teamMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Team>()
                        .like(Team::getTitle, "互联网+"));
        if (team == null) {
            return;
        }
        Object[][] msgs = {
                {"student3", "欢迎来到互联网+队伍讨论区！大家先熟悉一下~"},
                {"student1", "PPT 初稿我今晚发到群里"},
                {"student2", "数据分析部分我明天完成，格式按上次模板"},
                {"student6", "商业计划书我周五前改完，有问题的部分在文档里标注了"},
        };
        for (int i = 0; i < msgs.length; i++) {
            User sender = userMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                            .eq(User::getUsername, (String) msgs[i][0]));
            if (sender == null) {
                continue;
            }
            TeamDiscussion d = new TeamDiscussion();
            d.setTeamId(team.getId());
            d.setUserId(sender.getId());
            d.setUserName(sender.getNickname());
            d.setContent((String) msgs[i][1]);
            d.setCreateTime(LocalDateTime.now().minusMinutes((long) (msgs.length - i) * 30));
            teamDiscussionMapper.insert(d);
        }
        log.info("团队讨论区演示消息已生成");
    }

    private void seedAchievements(Map<String, Long> users, Map<String, Long> competitions) {
        Object[][] data = {
                {"student1", "数学建模", "国家级", "省二等奖"},
                {"student2", "数学建模", "国家级", "省三等奖"},
                {"student3", "互联网+", "国家级", "国赛铜奖"},
        };
        for (Object[] row : data) {
            Achievement a = new Achievement();
            a.setUserId(users.get(row[0]));
            a.setCompetitionId(competitions.get(row[1]));
            a.setName((String) row[1]);
            a.setLevel((String) row[2]);
            a.setAward((String) row[3]);
            a.setAwardYear(java.time.LocalDate.now().getYear() - 1);
            a.setStatus("已通过"); // 种子成果默认为已审核通过，便于成果墙演示
            a.setReviewTime(LocalDateTime.now().minusDays(150));
            a.setCreatedAt(LocalDateTime.now().minusDays(200));
            achievementMapper.insert(a);
        }
    }

    private void addMember(Team team, Long userId, String role, long daysAgo) {
        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setUserId(userId);
        member.setUserName(nickname(userId));
        member.setRole(role);
        member.setJoinedTime(LocalDateTime.now().minusDays(daysAgo));
        teamMemberMapper.insert(member);
    }

    private int countMembers(Long teamId) {
        return teamMemberMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)).intValue();
    }

    private void send(Long userId, String type, String content) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setContent(content);
        notification.setIsRead(false);
        notification.setCreateTime(LocalDateTime.now().minusHours(1));
        notificationMapper.insert(notification);
    }

    private String nickname(Long userId) {
        User user = userMapper.selectById(userId);
        return user == null ? "未知" : user.getNickname();
    }

    private Map<String, Long> skillIds() {
        Map<String, Long> map = new HashMap<>();
        for (SkillTag tag : skillTagMapper.selectList(null)) {
            map.put(tag.getName(), tag.getId());
        }
        return map;
    }

    private String computeStatus(LocalDateTime signupStart, LocalDateTime signupEnd) {
        LocalDateTime now = LocalDateTime.now();
        if (signupStart != null && now.isBefore(signupStart)) {
            return "即将开始";
        }
        if (signupEnd != null && now.isAfter(signupEnd)) {
            return "已结束";
        }
        return "报名中";
    }
}
