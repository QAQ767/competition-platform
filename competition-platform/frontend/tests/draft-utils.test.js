import test from 'node:test'
import assert from 'node:assert/strict'
import {
  canMarkReady,
  draftStatusMeta,
  evidenceRows,
  jobProgress,
  jobStatusMeta,
  publishBlockers
} from '../src/utils/draft.js'

const readyDraft = {
  status: 'DRAFT',
  name: '测试赛事',
  organizer: '测试主办方',
  signupEnd: '2026-12-01 23:59:59',
  sourceUrl: 'https://example.edu.cn/a',
  sources: [{ url: 'https://example.edu.cn/a', manual: false, rawText: '正文' }],
  signupEndPrecision: 'DATETIME',
  duplicateCandidates: [],
  conflicts: [],
  publishBlockers: []
}

test('草稿状态与作业状态标签', () => {
  assert.equal(draftStatusMeta('READY').label, '待发布')
  assert.equal(draftStatusMeta('PUBLISHED').type, 'success')
  assert.equal(draftStatusMeta('UNKNOWN').label, '草稿')
  assert.equal(jobStatusMeta('PARTIAL').label, '部分完成')
})

test('完整草稿才允许标记待发布', () => {
  assert.equal(canMarkReady(readyDraft), true)
  assert.equal(canMarkReady({ ...readyDraft, name: '' }), false)
  assert.equal(canMarkReady({ ...readyDraft, organizer: '' }), false)
  assert.equal(canMarkReady({ ...readyDraft, signupEnd: '' }), false)
  assert.equal(canMarkReady({ ...readyDraft, status: 'PUBLISHED' }), false)
})

test('只有日期的截止必须确认解释，重复和冲突必须人工确认', () => {
  const dateOnly = { ...readyDraft, signupEndPrecision: 'DATE', dateInterpretationConfirmed: false }
  assert.match(publishBlockers(dateOnly).join('；'), /只有日期/)
  assert.equal(
    canMarkReady({ ...dateOnly, dateInterpretationConfirmed: true }),
    true
  )

  const duplicates = { ...readyDraft, duplicateCandidates: [{ id: 1 }], duplicateAcknowledged: false }
  assert.match(publishBlockers(duplicates).join('；'), /疑似重复/)
  assert.equal(canMarkReady({ ...duplicates, duplicateAcknowledged: true }), true)

  const conflicts = { ...readyDraft, conflicts: ['发现多个报名截止'], reviewNote: '' }
  assert.match(publishBlockers(conflicts).join('；'), /审核备注/)
  assert.equal(canMarkReady({ ...conflicts, reviewNote: '已核对校内截止' }), true)
})

test('人工粘贴来源需要审核备注，URL 来源不需要', () => {
  const manual = {
    ...readyDraft,
    sourceUrl: '',
    sources: [{ manual: true, rawText: '公告正文' }],
    reviewNote: ''
  }
  assert.match(publishBlockers(manual).join('；'), /审核备注/)
  assert.equal(canMarkReady({ ...manual, reviewNote: '来源：教务处公告栏' }), true)
  const noSource = { ...readyDraft, sourceUrl: '', sources: [] }
  assert.match(publishBlockers(noSource).join('；'), /可追溯来源/)
})

test('证据行只展示非空字段，作业进度有上限', () => {
  const rows = evidenceRows({ evidence: { name: '赛事名称原文', signupEnd: '' } })
  assert.equal(rows.length, 1)
  assert.equal(rows[0].field, 'name')
  assert.equal(jobProgress({ status: 'RUNNING' }), 55)
  assert.equal(jobProgress(null), 0)
})
