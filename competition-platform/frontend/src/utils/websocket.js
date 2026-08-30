/**
 * WebSocket 管理器（单例）
 * - 连接后首条消息发送 AUTH（携带 JWT access token）
 * - 断线自动重连（3s），token 刷新后调用 refreshWsConnection() 重连
 * - 多个页面可注册消息处理器
 */

let ws = null
let handlers = new Set()
let reconnectTimer = null
let manualClose = false

function wsUrl() {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  return `${proto}://${location.host}/ws`
}

function scheduleReconnect() {
  if (reconnectTimer || manualClose) return
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    if (!manualClose) connectWebSocket()
  }, 3000)
}

export function connectWebSocket() {
  const token = localStorage.getItem('token')
  if (!token) return
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return

  try {
    ws = new WebSocket(wsUrl())
  } catch (e) {
    scheduleReconnect()
    return
  }

  ws.onopen = () => {
    const t = localStorage.getItem('token')
    ws.send(JSON.stringify({ type: 'AUTH', token: t }))
  }

  ws.onmessage = (ev) => {
    let msg
    try {
      msg = JSON.parse(ev.data)
    } catch (e) {
      return
    }
    if (msg.type === 'AUTH_OK') {
      // 认证成功
    }
    handlers.forEach((h) => h(msg))
  }

  ws.onclose = () => {
    ws = null
    scheduleReconnect()
  }

  ws.onerror = () => {
    try {
      ws && ws.close()
    } catch (e) {
      /* ignore */
    }
  }
}

export function disconnectWebSocket() {
  manualClose = true
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (ws) {
    try {
      ws.close()
    } catch (e) {
      /* ignore */
    }
    ws = null
  }
}

/** token 刷新后重连 */
export function refreshWsConnection() {
  manualClose = false
  disconnectWebSocket()
  manualClose = false
  connectWebSocket()
}

export function sendWsMessage(obj) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(obj))
    return true
  }
  return false
}

export function isWsOpen() {
  return !!ws && ws.readyState === WebSocket.OPEN
}

/** 注册消息处理器，返回取消函数 */
export function onWsMessage(handler) {
  handlers.add(handler)
  return () => handlers.delete(handler)
}
