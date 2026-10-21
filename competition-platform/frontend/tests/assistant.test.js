import test from 'node:test'
import assert from 'node:assert/strict'
import { isAdviceSessionMissing, readEventStream, shouldSendOnEnter } from '../src/utils/assistant.js'

test('只将明确的404视为会话失效，网络与503不清除会话', () => {
  assert.equal(isAdviceSessionMissing({code:404}),true)
  assert.equal(isAdviceSessionMissing({response:{status:404}}),true)
  assert.equal(isAdviceSessionMissing({code:'ERR_NETWORK'}),false)
  assert.equal(isAdviceSessionMissing({code:503,response:{status:503}}),false)
  assert.equal(isAdviceSessionMissing(new Error('会话不存在或已过期')),false)
})

test('中文组合输入与 Shift+Enter 不发送，普通 Enter 发送', () => {
  assert.equal(shouldSendOnEnter({ key: 'Enter' }), true)
  assert.equal(shouldSendOnEnter({ key: 'Enter', isComposing: true }), false)
  assert.equal(shouldSendOnEnter({ key: 'Enter', keyCode: 229 }), false)
  assert.equal(shouldSendOnEnter({ key: 'Enter', shiftKey: true }), false)
})

function response(text, size = 1) {
  const data = new TextEncoder().encode(text)
  return new Response(
    new ReadableStream({
      start(c) {
        for (let i = 0; i < data.length; i += size)
          c.enqueue(data.slice(i, i + size))
        c.close()
      },
    }),
  )
}
test('SSE 在 UTF-8 和 CRLF 字节边界分块仍正确解码', async () => {
  const events = []
  await readEventStream(
    response(
      'data: {"type":"chunk","content":"你好"}\r\n\r\ndata: {"type":"done"}\r\n\r\n',
    ),
    (e) => events.push(e),
  )
  assert.equal(events[0].content, '你好')
  assert.equal(events.length, 2)
})
test('流意外结束不能假装回答成功', async () => {
  await assert.rejects(
    readEventStream(
      response('data: {"type":"chunk","content":"部分"}\n\n'),
      () => {},
    ),
    /意外中断/,
  )
})
test('服务端错误会终止读取并释放 reader', async () => {
  await assert.rejects(
    readEventStream(
      response('data: {"type":"error","content":"限流"}\n\n'),
      (e) => {
        throw new Error(e.content)
      },
    ),
    /限流/,
  )
})
