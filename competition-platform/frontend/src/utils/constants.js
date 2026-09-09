/**
 * 参赛状态枚举 -> 中文标签 / Element 标签类型
 */
export const COMPETE_STATUS = {
  WANT_INVITE: { label: '希望被邀请', type: 'success' },
  ACCEPT_INVITE: { label: '可接受邀请', type: 'primary' },
  NOT_PARTICIPATE: { label: '暂不参赛', type: 'info' },
  ONLY_VIEW: { label: '仅浏览', type: 'warning' }
}

/** 固定技能标签库 */
export const SKILLS = ['算法', '前端', '后端', 'UI设计', '文档写作', '数据分析', '机器学习', '答辩演讲']

/** 竞赛级别 */
export const LEVELS = ['校级', '省级', '国家级']

/** 队伍状态标签颜色 */
export const TEAM_STATUS_TYPE = {
  '招募中': 'success',
  '已满员': 'warning',
  '备赛中': 'primary',
  '已结束': 'info'
}

/** 申请状态标签颜色 */
export const APPLY_STATUS_TYPE = {
  '待审批': 'warning',
  '已通过': 'success',
  '已拒绝': 'danger'
}

/** 训练竞赛分类 */
export const TRAINING_COMPETITIONS = ['ACM', 'CTF', '数学建模', '蓝桥杯', '互联网+', '通用']

/** 训练资源难度 */
export const TRAINING_DIFFICULTY = ['入门', '进阶', '竞赛']

/** 打卡难度标签颜色 */
export const TRAINING_DIFFICULTY_TYPE = { '入门': 'info', '进阶': 'warning', '竞赛': 'danger' }

/** 打卡等级（累计天数区间） */
export const CHECKIN_LEVEL_COLOR = {
  '见习': 'info',
  '进阶': 'primary',
  '熟练': 'warning',
  '大师': 'danger',
  '宗师': 'danger'
}
