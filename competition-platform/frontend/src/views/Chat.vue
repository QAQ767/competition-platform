<template>
  <div class="messaging-page">
    <PageHeading title="每一次交流，都是合作的开始" description="与未来的队友聊聊想法，让共同的目标更清晰。" eyebrow="STAY CONNECTED" />
    <div class="chat-page" :class="{ 'has-conversation': currentId }">
      <aside class="conv-card" aria-label="私聊会话列表">
        <div class="conversation-heading"><div><span class="section-kicker">MESSAGES</span><h2>私聊消息 <span>{{ convs.length }}</span></h2></div><el-icon><ChatDotRound /></el-icon></div>
        <el-input v-model="search" class="conversation-search" placeholder="搜索联系人或消息" clearable aria-label="搜索会话"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <div class="conversation-list">
          <div v-if="!filteredConvs.length" class="conv-empty"><el-icon><Message /></el-icon><strong>{{ search ? '没有找到相关会话' : '还没有新的会话' }}</strong><p>{{ search ? '试试其他姓名或关键词' : '去组队交流大厅认识伙伴，点击昵称即可开始交流。' }}</p><router-link v-if="!search" to="/teams">去组队广场看看 →</router-link></div>
          <button v-for="c in filteredConvs" :key="c.userId" type="button" class="conv-item" :class="{ active: c.userId === currentId }" :aria-pressed="c.userId === currentId" @click="openConv(c)" @contextmenu.prevent="openUserMenu($event, { userId: c.userId, userName: c.userName })">
            <span class="contact-avatar">{{ (c.userName || '友').slice(0, 1) }}</span>
            <span class="conv-copy"><span class="conv-name">{{ c.userName }}<span v-if="c.unread" class="unread-count">{{ c.unread > 99 ? '99+' : c.unread }}</span></span><span class="conv-last">{{ c.lastMessage || '打个招呼，开始交流吧' }}</span></span>
          </button>
        </div>
        <div class="conversation-footer"><span class="status-dot"></span>一起交流，共同成长</div>
      </aside>
      <section class="msg-card" aria-label="私聊消息">
        <header class="message-header" v-if="currentId">
          <button type="button" class="back-conversations" aria-label="返回会话列表" @click="currentId = null">‹</button>
          <span class="contact-avatar">{{ (currentName || '友').slice(0, 1) }}</span>
          <div class="message-person"><strong @contextmenu.prevent="openUserMenu($event, { userId: currentId, userName: currentName })">{{ currentName }}</strong><small>私聊交流 · 仅你们可见</small></div>
          <el-button v-if="currentId !== store.user?.id && !isFollowing(currentId)" type="primary" plain round size="small" @click="followCurrent">关注并交流</el-button>
        </header>
        <div v-if="!currentId" class="conversation-welcome"><span class="welcome-symbol"><el-icon><ChatDotRound /></el-icon></span><h2>好想法，从一句问候开始</h2><p>选择一位伙伴，聊聊竞赛目标与合作计划</p><router-link to="/teams">发现合拍的队伍 <el-icon><ArrowRight /></el-icon></router-link></div>
        <div v-else ref="msgListRef" class="msg-list">
          <button v-if="!dmLoadedAll" type="button" class="msg-load-more" @click="loadEarlierDm">加载更早消息</button>
          <p v-if="!messages.length" class="first-message">还没有消息，向 {{ currentName }} 打个招呼吧</p>
          <div v-for="m in messages" :key="m.id" class="msg-row" :class="{ mine: m.userId === store.user?.id }">
            <span class="message-avatar">{{ (m.userId === store.user?.id ? store.user?.nickname || '我' : m.userName || currentName).slice(0, 1) }}</span>
            <div class="message-content"><div class="msg-meta"><span @contextmenu.prevent="openUserMenu($event, { userId: m.userId, userName: m.userName })">{{ m.userId === store.user?.id ? '我' : m.userName }}</span><time>{{ m.createTime }}</time></div><div class="msg-bubble">{{ m.content }}</div></div>
          </div>
        </div>
        <div v-if="currentId" class="msg-input">
          <el-input v-model="text" type="textarea" :rows="3" resize="none" maxlength="1000" placeholder="分享你的想法…" aria-label="私聊消息内容" @keydown="onMessageKeydown" />
          <div class="composer-footer"><span>Enter 发送，Shift + Enter 换行</span><el-button type="primary" round :loading="sending" :disabled="!text.trim()" @click="send">发送消息 <el-icon><Right /></el-icon></el-button></div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'
import { useUserStore } from '../stores/user'
import {
  connectWebSocket,
  isWsOpen,
  onWsMessage
} from '../utils/websocket'
import { openUserMenu } from '../utils/userMenu'
import { shouldSendOnEnter } from '../utils/assistant'

const route = useRoute()
const store = useUserStore()

const convs = ref([])
const search = ref('')
const filteredConvs = computed(() => convs.value.filter(c => `${c.userName || ''} ${c.lastMessage || ''}`.toLowerCase().includes(search.value.trim().toLowerCase())))
function onMessageKeydown(event) { if (shouldSendOnEnter(event)) { event.preventDefault(); send() } }
let messageRequest = 0
const loadingEarlier = ref(false)
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
    loadConvs()
    // 当前正与对方聊天时，直接拉取新消息并标记已读
    if (
      msg.data.userId === currentId.value ||
      msg.data.receiverId === currentId.value
    ) {
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
    convs.value.unshift({
      userId: to,
      userName: name,
      lastMessage: '',
      unread: 0
    })
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
  const id = currentId.value
  if (!id || loadingEarlier.value) return
  const request = ++messageRequest
  const el = msgListRef.value
  const nearBottom = !el || el.scrollHeight - el.scrollTop - el.clientHeight < 80
  const data = await api.get(`/chat/dm/${id}`, { params: { page: 1, size: dmPageSize } })
  if (id !== currentId.value || request !== messageRequest) return
  const incoming = data.records || []
  const byId = new Map([...messages.value, ...incoming].map(m => [m.id, m]))
  messages.value = [...byId.values()].sort((a,b) => a.id - b.id)
  dmLoadedAll.value = messages.value.length >= data.total
  if (nearBottom) scrollBottom()
}

async function loadEarlierDm() {
  const id = currentId.value
  if (!id || dmLoadedAll.value || loadingEarlier.value) return
  loadingEarlier.value = true
  const el = msgListRef.value, height = el?.scrollHeight || 0, top = el?.scrollTop || 0
  try {
    const data = await api.get(`/chat/dm/${id}`, { params: { page: dmPage.value + 1, size: dmPageSize } })
    if (id !== currentId.value) return
    const all = new Map([...(data.records || []), ...messages.value].map(m => [m.id,m]))
    messages.value = [...all.values()].sort((a,b) => a.id-b.id)
    dmPage.value = data.page
    dmLoadedAll.value = messages.value.length >= data.total
    await nextTick()
    if (el) el.scrollTop = top + el.scrollHeight - height
  } finally { loadingEarlier.value = false }
}

async function openConv(c) {
  if (currentId.value !== c.userId) { messages.value = []; text.value = ''; loadingEarlier.value = false; dmPage.value = 1; dmLoadedAll.value = false; messageRequest++ }
  currentId.value = c.userId
  currentName.value = c.userName
  c.unread = 0
  await Promise.all([loadMessages(), api.post(`/chat/dm/${c.userId}/read`)])
}

async function send() {
  const content = text.value.trim(), id = currentId.value
  if (!content || !id || sending.value) return
  sending.value = true
  try {
    await api.post(`/chat/dm/${id}`, { content })
    if (currentId.value === id) {
      if (text.value.trim() === content) text.value = ''
      await loadMessages()
      scrollBottom()
    }
    await loadConvs()
  } catch { /* 保留输入，接口层显示错误 */ }
  finally { sending.value = false }
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

watch(() => route.query.to, async (value) => {
  if (!value) return
  await loadConvs()
  const target = convs.value.find(c => c.userId === Number(value))
  if (target) openConv(target)
})

onBeforeUnmount(() => {
  messageRequest++
  if (pollTimer) clearInterval(pollTimer)
  if (convTimer) clearInterval(convTimer)
  if (unsubWs) unsubWs()
})
</script>

<style scoped>
.messaging-page{padding-bottom:75px}
.chat-page{display:grid;grid-template-columns:290px minmax(0,1fr);height: min(740px,calc(100dvh - 320px));min-height:510px;background:#fff;border:1px solid #e6edf6;border-radius:20px;overflow:hidden;box-shadow:0 12px 40px #20365c06}
.conv-card{display:flex;flex-direction:column;min-height:0;border-right:1px solid #e9eef6;background:#fcfdff}.conversation-heading{display:flex;justify-content:space-between;align-items:center;padding:26px 22px 18px}.section-kicker{font-size:10px;letter-spacing:2px;color:#8a9bb8}.conversation-heading h2{font-size:19px;margin:8px 0 0;color:#253752}.conversation-heading h2 span{font-size:11px;background:#ecf2ff;color:#5277d8;border-radius:6px;padding:3px 7px;margin-left:7px}.conversation-heading>.el-icon{font-size:25px;color:#7390db}.conversation-search{width:auto;margin:0 16px 18px}.conversation-search :deep(.el-input__wrapper){border-radius:10px;background:#f2f5fa;box-shadow:none}.conversation-list{flex:1;overflow:auto;padding:0 10px}.conv-item{font:inherit;border:0;width:100%;text-align:left;display:flex;gap:12px;align-items:center;padding:15px 12px;border-radius:12px;cursor:pointer;background:transparent;margin-bottom:5px;color:#34455e;transition:background .15s}.conv-item:hover{background:#f1f5fc}.conv-item.active{background:#eaf0ff}.contact-avatar,.message-avatar{display:grid;place-items:center;flex-shrink:0;width:43px;height:43px;border-radius:14px;background:#e9efff;color:#5174d2;font-size:17px;font-weight:600}.conv-item.active .contact-avatar{background:#3867ed;color:#fff}.conv-copy{min-width:0;flex:1}.conv-name{display:flex;align-items:center;justify-content:space-between;font-size:14px;font-weight:600;gap:6px}.conv-last{display:block;font-size:12px;color:#8b97aa;margin-top:7px;white-space:nowrap;text-overflow:ellipsis;overflow:hidden}.unread-count{font-size:10px;background:#3867ed;color:white;min-width:18px;height:18px;padding:0 4px;border-radius:9px;display:grid;place-items:center}.conversation-footer{padding:18px 22px;font-size:11px;color:#91a0b5;border-top:1px solid #eef2f7}.status-dot{display:inline-block;width:6px;height:6px;border-radius:50%;background:#65b6a5;margin-right:8px}.conv-empty{text-align:center;padding:36px 16px;color:#8d9ab0;font-size:12px;line-height:1.9}.conv-empty>.el-icon{font-size:32px;display:block;margin:0 auto 18px;color:#a7b9df}.conv-empty strong{display:block;color:#586a85;font-size:14px}.conv-empty a{color:#3867ed;text-decoration:none}.msg-card{min-width:0;display:flex;flex-direction:column;min-height:0}.message-header{display:flex;align-items:center;gap:12px;padding:20px 26px;border-bottom:1px solid #edf1f7}.message-person{flex:1;min-width:0}.message-person strong{font-size:16px;color:#2c3d58}.message-person small{display:block;font-size:11px;color:#95a1b4;margin-top:6px}.msg-list{flex:1;min-height:0;overflow:auto;padding:24px;background:#f7f9fd;overscroll-behavior:contain}.msg-row{display:flex;gap:10px;align-items:flex-start;margin:0 0 22px}.message-avatar{width:32px;height:32px;font-size:13px;border-radius:11px}.message-content{max-width:78%;min-width:0}.msg-meta{display:flex;gap:10px;flex-wrap:wrap;font-size:11px;color:#8e9bb0;margin-bottom:7px}.msg-meta>span{color:#63758f}.msg-bubble{background:white;border:1px solid #e8edf5;border-radius:0 14px 14px 14px;padding:12px 16px;color:#34465f;font-size:14px;line-height:1.8;white-space:pre-wrap;overflow-wrap:anywhere}.msg-row.mine{flex-direction:row-reverse}.mine .msg-meta{justify-content:flex-end}.mine .msg-bubble{background:#3867ed;border-color:#3867ed;color:#fff;border-radius:14px 0 14px 14px}.mine .message-avatar{background:#e6edff;color:#3867ed}.msg-load-more{display:block;margin:0 auto 20px;border:0;border-radius:20px;background:#eaf0fb;color:#6f85ac;padding:7px 16px;font-size:11px;cursor:pointer}.msg-input{padding:14px 22px 18px;border-top:1px solid #edf1f7;background:white}.msg-input :deep(.el-textarea__inner){box-shadow:none;padding:6px 0;line-height:1.7;background:transparent}.composer-footer{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-top:8px}.composer-footer>span{font-size:11px;color:#98a4b7}.composer-footer .el-icon{margin-left:7px}.conversation-welcome{flex:1;display:flex;align-items:center;justify-content:center;flex-direction:column;background:radial-gradient(ellipse at center,#f3f6ff,white 70%);padding:30px;text-align:center}.welcome-symbol{display:grid;place-items:center;width:84px;height:84px;border-radius:27px;background:#ebf0ff;color:#6b89df;font-size:38px;transform:rotate(-6deg);margin-bottom:18px}.conversation-welcome h2{color:#344762;font-size:21px}.conversation-welcome p{font-size:13px;color:#93a0b5;line-height:1.8}.conversation-welcome a{display:flex;align-items:center;gap:6px;margin-top:20px;font-size:13px;text-decoration:none;color:#3867ed}.first-message{text-align:center;font-size:12px;color:#9aa7ba;margin:32px 0}.back-conversations{display:none}.conv-item:focus-visible,.back-conversations:focus-visible{outline:2px solid #3867ed;outline-offset:-2px}
@media(max-width:1100px){.chat-page{grid-template-columns:245px minmax(0,1fr)}}
@media(max-width:767px){.chat-page{display:flex;height:calc(100dvh - 225px);min-height:450px;border-radius:16px}.conv-card{width:100%;border:0}.msg-card{display:none;width:100%}.has-conversation .conv-card{display:none}.has-conversation .msg-card{display:flex}.back-conversations{display:block;border:0;background:#f1f5fb;color:#5d7497;font-size:25px;border-radius:8px;width:28px;height:32px}.message-header{padding:15px 12px;gap:8px}.message-header .contact-avatar{width:35px;height:35px;border-radius:11px}.msg-list{padding:18px 12px}.message-content{max-width:83%}.msg-input{padding:12px}.composer-footer>span{font-size:10px}.message-person strong{font-size:14px}.messaging-page{padding-bottom:60px}}
</style>
