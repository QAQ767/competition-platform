import test from 'node:test'
import assert from 'node:assert/strict'
import {
  adviceEmptyReason,
  competitionCategoryMeta,
  completenessLabel,
  confidenceMeta,
  formatDate,
  formatScore,
  matchScoreHint,
  modeMeta,
  preferenceRows,
  sourceTypeLabel,
  weeklyHoursLabel
} from '../src/utils/advice.js'

test('推荐模式与置信度使用明确中文标签', () => {
  assert.equal(modeMeta('LLM').label, 'AI 辅助分析')
  assert.equal(modeMeta('RULE').label, '规则匹配')
  assert.equal(modeMeta('UNKNOWN').label, '规则匹配')
  assert.equal(confidenceMeta('EXPLORE').label, '探索结果')
  assert.equal(confidenceMeta(undefined).label, '探索结果')
})

test('竞赛类别没有伪造“正在报名”', () => {
  assert.equal(competitionCategoryMeta('OPEN').label, '可报名')
  assert.equal(competitionCategoryMeta('UPCOMING').label, '可提前准备')
  assert.equal(competitionCategoryMeta('NEEDS_CONFIRM').label, '需确认报名时间')
  assert.equal(competitionCategoryMeta('EXCLUDED').label, '需确认报名时间')
})

test('日期、分数与完整度不会返回 NaN', () => {
  assert.equal(formatDate(null), '未公布')
  assert.equal(formatDate('2026-05-20 18:00:00'), '2026.05.20')
  assert.equal(formatScore(101), '100')
  assert.equal(formatScore(-1), '0')
  assert.equal(formatScore('abc'), '—')
  assert.match(completenessLabel(0.66), /66%/)
  assert.match(matchScoreHint(80), /不代表获奖或录取概率/)
})

test('weeklyHours=0 显示未确定而不是 0 小时', () => {
  assert.equal(weeklyHoursLabel(0, false), '未确定')
  assert.equal(weeklyHoursLabel(0, true), '未确定')
  assert.equal(weeklyHoursLabel(6, true), '每周 6 小时')
})

test('需求摘要保留每个字段的来源', () => {
  const rows = preferenceRows({
    weeklyHours: 3,
    directions: ['前端'],
    skills: [],
    roles: ['前端'],
    excludedKeywords: ['硬件'],
    difficulty: '入门',
    windowDays: 90,
    teamOnly: true,
    note: '不参加硬件比赛',
    sources: { weeklyHours: 'DIALOGUE', directions: 'MANUAL', excludedKeywords: 'PROFILE' }
  })
  const byField = Object.fromEntries(rows.map((row) => [row.field, row]))
  assert.equal(byField.weeklyHours.source.label, '本次对话')
  assert.equal(byField.directions.source.label, '手动确认')
  assert.equal(byField.excludedKeywords.source.label, '档案')
  assert.equal(byField.roles.source.label, '档案')
  assert.equal(byField.skills, undefined)
  assert.equal(byField.teamOnly.values[0], '是')
})

test('来源类型与空结果说明可读', () => {
  assert.equal(sourceTypeLabel('KEYWORD_INFERRED'), '名称关键词推断')
  assert.equal(sourceTypeLabel('MANUAL_TEXT'), '人工粘贴公告')
  assert.match(adviceEmptyReason({ competitions: [], teams: [] }), /放宽时间范围/)
  assert.equal(adviceEmptyReason({ competitions: [{}], teams: [] }), '')
})
