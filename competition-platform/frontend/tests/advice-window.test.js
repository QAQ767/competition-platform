import test from 'node:test'
import assert from 'node:assert/strict'
import {
  DEFAULT_WINDOW_DAYS,
  effectiveWindowDays,
  preferredResultTab
} from '../src/utils/advice.js'

test('对话确认的时间窗口就是推荐请求用的窗口', () => {
  // 对话里要求 30 天：摘要字段变化后，请求值必须跟着变，不能被旧的 90 覆盖
  const prefs = { windowDays: 30 }
  assert.equal(effectiveWindowDays(prefs), 30)

  prefs.windowDays = 60
  assert.equal(effectiveWindowDays(prefs), 60)
})

test('摘要没写窗口时回落到默认值', () => {
  assert.equal(effectiveWindowDays(null), DEFAULT_WINDOW_DAYS)
  assert.equal(effectiveWindowDays({}), DEFAULT_WINDOW_DAYS)
  assert.equal(effectiveWindowDays({ windowDays: null }), DEFAULT_WINDOW_DAYS)
  assert.equal(effectiveWindowDays({ windowDays: 0 }), DEFAULT_WINDOW_DAYS)
  assert.equal(effectiveWindowDays({ windowDays: 'abc' }), DEFAULT_WINDOW_DAYS)
})

test('勾选只看队伍后直接展示队伍页签', () => {
  assert.equal(preferredResultTab({ teamOnly: true }), 'teams')
  assert.equal(preferredResultTab({ teamOnly: false }), 'competitions')
  assert.equal(preferredResultTab(null), 'competitions')
})
