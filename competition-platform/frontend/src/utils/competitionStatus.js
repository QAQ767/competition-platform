export const COMPETITION_STATUS_TYPE = {
  报名中: 'success',
  即将报名: 'primary',
  报名即将截止: 'warning',
  已结束: 'info'
}

export const COMPETITION_FILTER_OPTIONS = [
  { label: '报名中', value: '报名中' },
  { label: '即将报名', value: '即将报名' },
  { label: '报名即将截止', value: '报名即将截止' },
  { label: '即将开赛', value: '即将开赛' },
  { label: '报名已结束', value: '已结束' }
]

export function competitionStatusLabel(status) {
  return status === '已结束' ? '报名已结束' : status
}

export function isCompetitionStartingSoon(competition, now = Date.now()) {
  if (!competition?.startTime) return false
  const start = new Date(
    String(competition.startTime).replace(' ', 'T')
  ).getTime()
  return Number.isFinite(start) && start > now && start < now + 7 * 86400000
}
