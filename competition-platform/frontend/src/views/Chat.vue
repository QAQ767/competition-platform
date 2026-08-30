<template>
  <div class="chat-page">
    <el-card shadow="never" class="conv-card">
      <template #header>💬 会话列表</template>
      <div v-if="!convs.length" class="conv-empty">
        暂无会话<br />
        <span style="font-size: 12px; color: #909399">去组队广场点击大厅消息里的小伙伴昵称即可发起私聊</span>
      </div>
      <div v-for="c in convs" :key="c.userId" class="conv-item" :class="{ active: c.userId === currentId }" @click="openConv(c)">
        <div class="conv-name">
          <span class="name-link" @contextmenu.prevent="openUserMenu($event, { userId: c.userId, userName: c.userName })">{{ c.userName }}</span>
          <el-badge v-if="c.unread" :value="c.unread" :max="99" />
        </div>
        <div class="conv-last">{{ c.lastMessage }}</div>
      </div>
    </el-card>

    <el-card shadow="never" class="msg-card">
      <template #header>
        <span class="name-link" @contextmenu.prevent="currentId && openUserMenu($event, { userId: currentId, userName: currentName })">{{ currentName || '请选择会话' }}</span>
        <el-button
          v-if="currentId && currentId !== store.user?.id && !isFollowing(currentId)"
          type="success"
          size="small"
          plain
          style="margin-left: 10px"
          @click="followCurrent"
        >＋关注后可私聊</el-button>
      </template>
      <div v-if="!currentId" class="conv-empty">从左侧选择一个会话开始聊天</div>
      <div v-else class="msg-list" ref="msgListRef">
        <div v-if="!dmLoadedAll" class="msg-load-more" @click="loadEarlierDm">⏫ 加载更早消息</div>
        <div v-for="m in messages" :key="m.id" class="msg-row" :class="{ mine: m.userId === store.user.id }">
          <div class="msg-bubble">{{ m.content }}</div>
          <div class="msg-meta">
            <template v-if="m.userId === store.user.id">我</template>
            <span v-else class="name-link" @contextmenu.prevent="openUserMenu($event, { userId: m.userId, userName: m.userName })">{{ m.userName }}</span>
            <span> · {{ m.createTime }}</span>
          </div>
        </div>
      </div>
      <div v-if="currentId" class="msg-input">
        <el-input v-model="text" placeholder="输入消息，回车发送" @keyup.enter="send" />
        <el-button type="primary" :loading="sending" @click="send">发送</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'
import { useUserStore } from '../stores/user'
import { connectWebSocket, isWsOpen, sendWsMessage, onWsMessage } from '../utils/websocket'
import { openUserMenu } from '../utils/userMenu'

const route = useRoute()
const store = useUserStore()

const convs = ref([])
const currentId = ref(null)
const currentName = ref('')
const messages = ref([])
const dmPage = ref(1)
const dmPageSize = 30
const dmLoadedAll = ref(false)
const text = ref('')
const sending = ref(false)
const msgListRef = ref(null)
const followingIds = ref([])
let pendingText = '' // 已通过 WS 发出、等待服务端回显的消息（回显成功才清空输入框）
let pollTimer = null
let convTimer = null
let unsubWs = null

// WebSocket 实时推送：私聊消息到达时刷新会话与消息
function handleWs(msg) {
  // 发送被拦截（如未关注对方）时给出明确提示，并保留输入内容
  if (msg.type === 'ERROR' && msg.message) {
    ElMessage.error(msg.message)
    return
  }
  if (msg.type !== 'DM' || !msg.data) return
  const me = store.user?.id
  if (msg.data.userId === me || msg.data.receiverId === me) {
    // 自己发出的消息被服务端回显 → 清空输入框
    if (pendingText && msg.data.userId === me && msg.data.content === pendingText) {
      text.value = ''
      pendingText = ''
    }
    loadConvs()
    // 当前正与对方聊天时，直接拉取新消息并标记已读
    if (msg.data.userId === currentId.value || msg.data.receiverId === currentId.value) {
      loadMessages()
      if (msg.data.receiverId === me) {
        api.post(`/chat/dm/${msg.data.userId}/read`).catch(() => {})
      }
    }
  }
}

async function loadConvs() {
  convs.value = await api.get('/chat/dm/conversations')
  // 从组队广场跳转过来的目标用户：补上真实昵称（而不是"用户+id"）
  const to = Number(route.query.to)
  if (to && !convs.value.some((c) => c.userId === to)) {
    let name = '用户' + to
    try {
      const home = await api.get(`/users/${to}`)
      name = home.user?.nickname || name
    } catch (e) {
      /* 忽略 */
    }
    convs.value.unshift({ userId: to, userName: name, lastMessage: '', unread: 0 })
  }
}

function isFollowing(userId) {
  return followingIds.value.some((u) => u.id === userId || u.userId === userId)
}

async function loadFollowing() {
  if (!store.isLogin) return
  try {
    followingIds.value = await api.get('/users/me/following')
  } catch (e) {
    /* 忽略 */
  }
}

async function followCurrent() {
  await api.post(`/users/${currentId.value}/follow`)
  ElMessage.success('关注成功，现在可以私聊对方了')
  loadFollowing()
}

async function loadMessages() {
  if (!currentId.value) return
  dmPage.value = 1
  dmLoadedAll.value = false
  const data = await api.get(`/chat/dm/${currentId.value}`, { params: { page: 1, size: dmPageSize } })
  messages.value = data.records || []
  dmLoadedAll.value = (data.records || []).length >= data.total
  scrollBottom()
}

async function loadEarlierDm() {
  if (!currentId.value || dmLoadedAll.value) return
  const data = await api.get(`/chat/dm/${currentId.value}`, {
    params: { page: dmPage.value + 1, size: dmPageSize }
  })
  const older = data.records || []
  messages.value = [...older, ...messages.value]
  dmPage.value = data.page
  dmLoadedAll.value = messages.value.length >= data.total
}

async function openConv(c) {
  currentId.value = c.userId
  currentName.value = c.userName
  c.unread = 0
  await Promise.all([loadMessages(), api.post(`/chat/dm/${c.userId}/read`)])
}

async function send() {
  const content = text.value.trim()
  if (!content || !currentId.value) return
  sending.value = true
  try {
    // 优先 WebSocket 实时发送（成功时服务端回显后自动清空；被拦截时保留输入并提示）
    if (sendWsMessage({ type: 'DM', toUserId: currentId.value, content })) {
      pendingText = content
    } else {
      await api.post(`/chat/dm/${currentId.value}`, { content })
      text.value = ''
      await loadMessages()
    }
  } finally {
    sending.value = false
  }
}

function scrollBottom() {
  nextTick(() => {
    const el = msgListRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

onMounted(async () => {
  connectWebSocket()
  unsubWs = onWsMessage(handleWs)
  await Promise.all([loadConvs(), loadFollowing()])
  const to = Number(route.query.to)
  const target = convs.value.find((c) => c.userId === to)
  if (target) {
    openConv(target)
  }
  // WS 断开时降级轮询兜底
  convTimer = setInterval(() => {
    if (!isWsOpen()) loadConvs()
  }, 5000)
  pollTimer = setInterval(() => {
    if (!isWsOpen()) loadMessages()
  }, 3000)
})

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
  if (convTimer) clearInterval(convTimer)
  if (unsubWs) unsubWs()
})
</script>

<style scoped>
.chat-page {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.conv-card {
  width: 260px;
  flex-shrink: 0;
}
.conv-empty {
  color: #909399;
  font-size: 13px;
  padding: 20px 8px;
  text-align: center;
  line-height: 1.8;
}
.conv-item {
  padding: 10px 8px;
  border-radius: 6px;
  cursor: pointer;
  border-bottom: 1px solid #f0f2f5;
}
.conv-item:hover {
  background: #f5f7fa;
}
.conv-item.active {
  background: #ecf5ff;
}
.conv-name {
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.name-link {
  cursor: pointer;
  color: #409eff;
}
.name-link:hover {
  text-decoration: underline;
}
.conv-last {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.msg-card {
  flex: 1;
}
.msg-list {
  height: 420px;
  overflow-y: auto;
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px;
}
.msg-load-more {
  text-align: center;
  font-size: 12px;
  color: #409eff;
  cursor: pointer;
  padding: 6px 0;
}
.msg-load-more:hover {
  text-decoration: underline;
}
.msg-row {
  margin-bottom: 12px;
  display: flex;
  flex-direction: column;
}
.msg-row.mine {
  align-items: flex-end;
}
.msg-bubble {
  max-width: 70%;
  background: #fff;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-all;
}
.msg-row.mine .msg-bubble {
  background: #409eff;
  color: #fff;
}
.msg-meta {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
}
.msg-input {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}
</style>
