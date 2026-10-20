export const DRAFT_STATUS = {
  DRAFT: { label: '草稿', type: 'info' },
  READY: { label: '待发布', type: 'warning' },
  PUBLISHED: { label: '已发布', type: 'success' },
  REJECTED: { label: '已拒绝', type: 'danger' }
}

export const JOB_STATUS = {
  QUEUED: { label: '排队中', type: 'info' },
  RUNNING: { label: '搜索/抓取中', type: 'warning' },
  SUCCEEDED: { label: '已完成', type: 'success' },
  PARTIAL: { label: '部分完成', type: 'warning' },
  FAILED: { label: '失败', type: 'danger' }
}

export const DRAFT_FIELD_LABELS = {
  name: '赛事名称',
  organizer: '主办方',
  year: '年份',
  edition: '届次',
  track: '赛道',
  level: '级别',
  description: '简介',
  tags: '方向标签',
  eligibilityText: '参赛条件',
  signupStart: '报名开始',
  signupEnd: '报名截止',
  startTime: '开赛时间'
}

export function draftStatusMeta(status) {
  return DRAFT_STATUS[status] || DRAFT_STATUS.DRAFT
}

export function jobStatusMeta(status) {
  return JOB_STATUS[status] || JOB_STATUS.QUEUED
}

export function publishBlockers(draft) {
  if (!draft) return ['草稿不存在']
  const blockers = Array.isArray(draft.publishBlockers)
    ? [...draft.publishBlockers]
    : []
  const add = (message) => {
    if (!blockers.includes(message)) blockers.push(message)
  }
  if (!draft.name) add('缺少赛事名称')
  if (!draft.organizer) add('缺少主办方')
  if (!draft.signupEnd) add('缺少报名截止时间')
  const manualSource = draft.sources?.some((s) => s.manual && s.rawText)
  if (!draft.sourceUrl && !manualSource) add('缺少可追溯来源')
  if (!draft.sourceUrl && manualSource && !draft.reviewNote) {
    add('人工粘贴文本需在审核备注中注明来源')
  }
  if (draft.signupEndPrecision === 'DATE' && !draft.dateInterpretationConfirmed) {
    add('报名截止只有日期，需确认解释')
  }
  if (draft.duplicateCandidates?.length && !draft.duplicateAcknowledged) {
    add('存在疑似重复赛事，需确认差异')
  }
  if (draft.conflicts?.length && !draft.reviewNote) {
    add('存在冲突，需在审核备注说明')
  }
  return blockers
}

export function canMarkReady(draft) {
  return draft?.status !== 'PUBLISHED' && publishBlockers(draft).length === 0
}

export function evidenceRows(draft) {
  if (!draft?.evidence) return []
  return Object.entries(draft.evidence)
    .filter(([, value]) => value)
    .map(([field, value]) => ({
      field,
      label: DRAFT_FIELD_LABELS[field] || field,
      value
    }))
}

export function draftMissing(draft) {
  if (Array.isArray(draft?.missingFields)) return draft.missingFields
  return []
}

export function jobProgress(job) {
  if (!job) return 0
  if (job.status === 'QUEUED') return 10
  if (job.status === 'RUNNING') return 55
  if (job.status === 'SUCCEEDED') return 100
  if (job.status === 'PARTIAL') return 100
  return 100
}
