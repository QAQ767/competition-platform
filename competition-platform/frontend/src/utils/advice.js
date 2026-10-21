export const ADVICE_MODE = {
  LLM: { label: 'AI 辅助分析', type: 'primary' },
  RULE: { label: '规则匹配', type: 'info' }
}

export const ADVICE_CONFIDENCE = {
  HIGH: { label: '证据较完整', type: 'success' },
  MEDIUM: { label: '证据一般', type: 'primary' },
  LOW: { label: '证据偏少', type: 'warning' },
  EXPLORE: { label: '探索结果', type: 'info' }
}

export const COMPETITION_CATEGORY = {
  OPEN: { label: '可报名', type: 'success' },
  UPCOMING: { label: '可提前准备', type: 'primary' },
  NEEDS_CONFIRM: { label: '需确认报名时间', type: 'warning' }
}

export const TEAM_CATEGORY = {
  OPEN: { label: '招募中', type: 'success' },
  NEEDS_CONFIRM: { label: '需确认赛事时间', type: 'warning' },
  APPLIED: { label: '已申请', type: 'info' }
}

export const PREFERENCE_SOURCE = {
  PROFILE: { label: '档案', type: 'info' },
  DIALOGUE: { label: '本次对话', type: 'warning' },
  MANUAL: { label: '手动确认', type: 'success' }
}

export const COMPETE_STATUS_LABEL = {
  WANT_INVITE: '希望被邀请',
  ACCEPT_INVITE: '可接受邀请',
  NOT_PARTICIPATE: '暂不参赛',
  ONLY_VIEW: '仅浏览'
}

export const ADVICE_DIRECTION_OPTIONS = [
  '前端',
  '后端',
  '算法',
  '数据分析',
  '机器学习',
  '建模',
  'UI设计',
  '文档写作',
  '答辩演讲',
  '网络安全',
  '硬件',
  '测试',
  '产品',
  '运营'
]

export const ADVICE_DIFFICULTY_OPTIONS = ['入门', '进阶']

export function modeMeta(mode) {
  return ADVICE_MODE[mode] || ADVICE_MODE.RULE
}

export function recommendationFallbackMessage(result) {
  const messages = {
    NO_CANDIDATES: '当前没有符合条件的候选，本轮未调用 AI 排序。',
    HTTP_401: 'AI 服务配置暂不可用，先为你展示规则匹配结果。',
    HTTP_403: 'AI 服务配置暂不可用，先为你展示规则匹配结果。',
    HTTP_429: 'AI 服务繁忙，先为你展示规则匹配结果，稍后可以重试。',
    LLM_TIMEOUT: 'AI 分析超时，先为你展示符合条件的结果。',
    EMPTY_RESPONSE: 'AI 未返回有效分析，先为你展示规则匹配结果。',
    VALIDATION_FAILED: 'AI 分析未通过核验，先为你展示规则匹配结果。',
    LLM_UNAVAILABLE: '暂时无法连接 AI，先为你展示规则匹配结果。'
  }
  return messages[result?.failureCategory] || 'AI 暂时未能完成分析，先为你展示符合条件的结果。'
}

export function confidenceMeta(confidence) {
  return ADVICE_CONFIDENCE[confidence] || ADVICE_CONFIDENCE.EXPLORE
}

export function competitionCategoryMeta(category) {
  return COMPETITION_CATEGORY[category] || COMPETITION_CATEGORY.NEEDS_CONFIRM
}

export function teamCategoryMeta(category) {
  return TEAM_CATEGORY[category] || TEAM_CATEGORY.NEEDS_CONFIRM
}

export function sourceMeta(source) {
  return PREFERENCE_SOURCE[source] || PREFERENCE_SOURCE.PROFILE
}

export function formatDateTime(value) {
  if (!value) return '未公布'
  return String(value).slice(0, 16).replace('T', ' ').replace(/-/g, '.')
}

export function formatDate(value) {
  if (!value) return '未公布'
  return String(value).slice(0, 10).replace(/-/g, '.')
}

export function formatScore(score) {
  const value = Number(score)
  if (!Number.isFinite(value)) return '—'
  return String(Math.max(0, Math.min(100, Math.round(value))))
}

export function matchScoreHint(score) {
  return `相对匹配度 ${formatScore(score)}/100，不代表获奖或录取概率`
}

export function completenessLabel(value) {
  const n = Number(value)
  if (!Number.isFinite(n)) return '证据完整度未知'
  return `证据完整度 ${Math.round(Math.max(0, Math.min(1, n)) * 100)}%`
}

export function sourceTypeLabel(type) {
  return (
    {
      PLATFORM_TAG: '平台标签',
      KEYWORD_INFERRED: '名称关键词推断',
      OFFICIAL: '官方来源',
      MANUAL_TEXT: '人工粘贴公告',
      PLATFORM: '平台录入',
      UNKNOWN: '来源类型未知',
      AGGREGATOR: '聚合站线索'
    }[type] || '来源类型未知'
  )
}

export function weeklyHoursLabel(hours, known = true) {
  if (!known || !hours) return '未确定'
  return `每周 ${hours} 小时`
}

const FIELD_LABELS = {
  weeklyHours: '每周时间',
  directions: '方向',
  skills: '技能',
  roles: '期望角色',
  difficulty: '难度偏好',
  excludedKeywords: '排除项',
  windowDays: '时间范围',
  teamOnly: '只看队伍',
  note: '补充说明'
}

export function preferenceRows(preferences) {
  if (!preferences) return []
  const rows = []
  const push = (field, values) => {
    if (values === undefined || values === null) return
    if (Array.isArray(values)) {
      if (!values.length) return
      rows.push({ field, label: FIELD_LABELS[field], values })
      return
    }
    if (values === '') return
    rows.push({
      field,
      label: FIELD_LABELS[field],
      values: [String(values)]
    })
  }
  push('weeklyHours', preferences.weeklyHours ? `${preferences.weeklyHours} 小时` : null)
  push('directions', preferences.directions)
  push('skills', preferences.skills)
  push('roles', preferences.roles)
  push('difficulty', preferences.difficulty)
  push('excludedKeywords', preferences.excludedKeywords)
  push('windowDays', preferences.windowDays ? `${preferences.windowDays} 天` : null)
  push('teamOnly', preferences.teamOnly ? '是' : null)
  push('note', preferences.note)
  return rows.map((row) => ({
    ...row,
    source: sourceMeta(preferences.sources?.[row.field])
  }))
}

export function adviceEmptyReason(result) {
  if (!result) return '还没有生成推荐'
  if (!result.competitions?.length && !result.teams?.length) {
    return '当前条件下没有可推荐结果，可以放宽时间范围或补充档案'
  }
  return ''
}

/** 时间窗口的唯一权威来源：需求摘要里的 windowDays；没有时用默认值。 */
export const DEFAULT_WINDOW_DAYS = 90

export function effectiveWindowDays(preferences, fallback = DEFAULT_WINDOW_DAYS) {
  if (preferences?.windowUnrestricted) return null
  const value = preferences?.windowDays
  const numeric = Number(value)
  return Number.isFinite(numeric) && numeric > 0 ? numeric : fallback
}

/** 勾选“只看队伍”后应直接展示队伍页签，避免停在空的竞赛列表。 */
export function preferredResultTab(preferences) {
  if (preferences?.outputTarget) return preferences.outputTarget === 'TEAMS' ? 'teams' : 'competitions'
  return preferences?.teamOnly ? 'teams' : 'competitions'
}
