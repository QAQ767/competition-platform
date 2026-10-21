export function shouldSendOnEnter(event) {
  return (
    event.key === 'Enter' &&
    !event.shiftKey &&
    !event.isComposing &&
    event.keyCode !== 229
  )
}

export function isAdviceSessionMissing(error) {
  return Number(error?.code) === 404 || error?.response?.status === 404
}
export async function readEventStream(response, onEvent) {
  const reader = response.body.getReader(),
    decoder = new TextDecoder()
  let buffer = '',
    ended = false
  try {
    while (true) {
      const { value, done } = await reader.read()
      buffer += decoder.decode(value || new Uint8Array(), { stream: !done })
      buffer = buffer.replace(/\r\n/g, '\n')
      let boundary
      while ((boundary = buffer.indexOf('\n\n')) >= 0) {
        const block = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + 2)
        const data = block
          .split('\n')
          .filter((line) => line.startsWith('data:'))
          .map((line) => line.slice(5).trimStart())
          .join('\n')
        if (data) {
          let event = JSON.parse(data)
          if (typeof event === 'string') event = JSON.parse(event)
          if (event.type === 'done') ended = true
          onEvent(event)
        }
      }
      if (done) break
    }
    if (!ended) throw new Error('回答流意外中断，请重试')
  } finally {
    await reader.cancel().catch(() => {})
    reader.releaseLock()
  }
}
