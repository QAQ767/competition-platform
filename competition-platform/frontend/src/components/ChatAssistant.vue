<template>
  <button
    type="button"
    class="xiaozhi-launcher"
    :class="{ open: visible }"
    :aria-label="advice ? '打开参赛助手小智' : '打开 AI 学习助手小智'"
    @click="togglePanel"
  >
    <el-icon><MagicStick /></el-icon>
    <span>问小智</span>
  </button>

  <transition name="assistant-pop">
    <section
      v-if="visible"
      class="xiaozhi-panel"
      :aria-label="advice ? '小智参赛助手' : '小智 AI 学习助手'"
      @keydown.esc.stop="closePanel"
    >
      <header class="assistant-head">
        <div class="assistant-avatar">
          <el-icon><MagicStick /></el-icon>
        </div>
        <div>
          <strong>小智</strong>
          <small
            ><i></i>
            {{ advice ? '参赛助手 · 找比赛、找队伍' : 'AI 学习助手' }}</small
          >
        </div>
        <button
          type="button"
          aria-label="新建对话"
          title="新建对话"
          @click="newChat"
        >
          <el-icon><Delete /></el-icon>
        </button>
        <button
          type="button"
          aria-label="关闭小智"
          title="关闭"
          @click="closePanel"
        >
          <el-icon><Close /></el-icon>
        </button>
      </header>

      <div ref="messageList" class="assistant-messages">
        <div v-if="!messages.length" class="assistant-welcome">
          <div class="welcome-icon">
            <el-icon><ChatDotRound /></el-icon>
          </div>
          <strong>你好，我是小智</strong>
          <p>
            {{
              advice
                ? '可以帮你找比赛、找队伍。说说技能、每周时间或想参加的赛事，一次发送即可查看推荐。'
                : '可以陪你理解知识点、分析代码、制定计划，也可以出题检验学习效果。'
            }}
          </p>
          <div class="quick-prompts">
            <button
              v-for="prompt in quickPrompts"
              :key="prompt"
              type="button"
              @click="usePrompt(prompt)"
            >
              {{ prompt }}
            </button>
          </div>
        </div>

        <template v-for="(message, index) in messages" :key="index">
          <div :class="['assistant-message', message.role]">
            <span class="message-role">{{
              message.role === 'user' ? '我' : '智'
            }}</span>
            <div class="message-bubble">
              <span
                v-if="message.loading && !message.content"
                class="thinking-dots"
              >
                <i></i><i></i><i></i>
              </span>
              <span v-else>{{ message.content }}</span>
              <template v-if="message.recommendation">
                <div class="recommendation-source">
                  {{ modeMeta(message.recommendation.mode).label }}
                </div>
                <p
                  v-if="message.recommendation.mode === 'RULE'"
                  class="fallback-note"
                >
                  {{ recommendationFallbackMessage(message.recommendation) }}
                </p>
                <article
                  v-for="card in message.recommendation.competitions"
                  :key="'c' + card.id"
                  class="advice-card"
                >
                  <strong>{{ card.name }}</strong>
                  <small
                    >{{ card.categoryLabel }} · 报名截止
                    {{ formatDateTime(card.signupEnd) }}</small
                  >
                  <p>{{ card.reason }}</p>
                  <small v-for="risk in card.risks" :key="risk">{{
                    risk
                  }}</small>
                  <router-link :to="'/competitions/' + card.id"
                    >查看竞赛 →</router-link
                  >
                </article>
                <article
                  v-for="card in message.recommendation.teams"
                  :key="'t' + card.id"
                  class="advice-card"
                >
                  <strong>{{ card.title }}</strong
                  ><small>{{ card.competitionName || '所属竞赛待确认' }}</small>
                  <small
                    >{{ card.categoryLabel }} ·
                    {{ card.memberCount ?? '待确认' }}/{{
                      card.maxMembers ?? '待确认'
                    }}
                    人 · 余 {{ card.remainingPlaces ?? '待确认' }} 位</small
                  >
                  <p>{{ card.reason }}</p>
                  <small v-for="risk in card.risks" :key="risk">{{
                    risk
                  }}</small>
                  <router-link :to="'/teams/' + card.id"
                    >查看队伍 →</router-link
                  >
                </article>
              </template>
              <button
                v-if="message.error && message.question"
                type="button"
                class="retry-answer"
                @click="retry(message)"
              >
                重试
              </button>
            </div>
          </div>
        </template>
      </div>

      <div v-if="resourceName" class="assistant-context">
        正在结合「{{ resourceName }}」回答
        <button type="button" @click="resourceName = ''">×</button>
      </div>

      <footer class="assistant-input">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          resize="none"
          maxlength="2000"
          :placeholder="
            advice
              ? '描述参赛需求，Enter 发送，Shift + Enter 换行'
              : '输入学习问题，Enter 发送，Shift + Enter 换行'
          "
          @keydown="onKeydown"
        />
        <div class="input-foot">
          <span>云端模式会处理必要资料，请核对关键信息</span>
          <el-button v-if="generating" size="small" @click="stopGenerating"
            >停止</el-button
          >
          <el-button
            v-else
            type="primary"
            size="small"
            :disabled="!input.trim()"
            @click="send"
          >
            发送
          </el-button>
        </div>
      </footer>
    </section>
  </transition>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ChatDotRound,
  Close,
  Delete,
  MagicStick,
} from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'
import api from '../api'
import {
  formatDateTime,
  modeMeta,
  recommendationFallbackMessage,
} from '../utils/advice'
import { isAdviceSessionMissing, readEventStream, shouldSendOnEnter } from '../utils/assistant'

const props = defineProps({
  scenario: { type: String, default: 'learning' },
  competition: { type: String, default: '' },
  tag: { type: String, default: '' },
})
const advice = computed(() => props.scenario === 'advice')
const router = useRouter(),
  route = useRoute(),
  store = useUserStore()
const visible = ref(false),
  input = ref(''),
  messages = ref([]),
  generating = ref(false),
  resourceName = ref(''),
  messageList = ref(null)
const session = ref(null)
let controller = null,
  epoch = 0,
  pendingStop = Promise.resolve()
const quickPrompts = computed(() =>
  advice.value
    ? [
        '按我的情况推荐比赛和队伍',
        '我想参加蓝桥杯，只推荐队伍',
        '适合新手，最近一个月的比赛',
      ]
    : [
        '帮我制定一周训练计划',
        '用简单例子解释动态规划',
        '出一道题检验我的掌握程度',
        '帮我分析这段代码的问题',
      ],
)
function storageKey(id = store.user?.id) {
  return `xiaozhi-${props.scenario}-${id || 'guest'}`
}
function scrollToBottom() {
  nextTick(() => {
    if (messageList.value)
      messageList.value.scrollTop = messageList.value.scrollHeight
  })
}
async function loadHistory() {
  const current = epoch,
    owner = store.user?.id
  if (!store.isLogin) return
  try {
    if (advice.value) {
      const id = localStorage.getItem(storageKey())
      if (!id) {
        session.value = null
        messages.value = []
        return
      }
      const saved = await api.get(`/ai/advice/sessions/${id}`)
      if (current !== epoch || owner !== store.user?.id) return
      session.value = saved
      messages.value = saved.messages || []
    } else {
      const saved = JSON.parse(
        localStorage.getItem(storageKey()) ||
          localStorage.getItem(`xiaozhi-history-${owner}`) ||
          '[]',
      )
      if (Array.isArray(saved)) messages.value = saved.slice(-20)
    }
  } catch (error) {
    if (current === epoch) {
      if (advice.value && isAdviceSessionMissing(error)) {
        session.value = null
        messages.value = []
        localStorage.removeItem(storageKey(owner))
        ElMessage.info('上次会话已过期或被清空，下次发送将开启新对话')
      } else if (advice.value) {
        ElMessage.warning('暂时无法读取会话，原会话已保留，请稍后重试')
      } else {
        localStorage.removeItem(storageKey(owner))
        messages.value = []
      }
    }
  }
  scrollToBottom()
}
function saveHistory() {
  if (!store.isLogin || advice.value) return
  localStorage.setItem(
    storageKey(),
    JSON.stringify(
      messages.value
        .filter((m) => m.content && !m.loading && !m.error)
        .slice(-20)
        .map(({ role, content }) => ({ role, content })),
    ),
  )
}
function usePrompt(prompt) {
  input.value = prompt
  send()
}
function openWithResource(site) {
  resourceName.value = site?.name || ''
  visible.value = true
  input.value = site?.name ? `我该如何使用「${site.name}」进行有效训练？` : ''
}
function stopGenerating(cancelUpstream = true) {
  epoch++
  controller?.abort()
  controller = null
  if (cancelUpstream && generating.value && advice.value && session.value) {
    const id = session.value.id
    pendingStop = api
      .post(`/ai/advice/sessions/${id}/cancel`, {}, { timeout: 5000 })
      .catch(() => {})
  }
  messages.value.forEach((m) => {
    if (m.loading) {
      m.loading = false
      m.error = true
      m.content = m.content ? `${m.content}\n[已停止生成]` : '已停止生成'
    }
  })
  generating.value = false
}
function closePanel() {
  stopGenerating()
  visible.value = false
}
function togglePanel() {
  if (visible.value) closePanel()
  else {
    visible.value = true
    loadHistory()
  }
}
async function newChat() {
  const id = session.value?.id
  stopGenerating()
  session.value = null
  messages.value = []
  resourceName.value = ''
  localStorage.removeItem(storageKey())
  if (advice.value && id)
    await api.delete(`/ai/advice/sessions/${id}`).catch(() => {})
}
function onKeydown(event) {
  if (shouldSendOnEnter(event)) {
    event.preventDefault()
    send()
  }
}
function retry(message) {
  const index = messages.value.indexOf(message)
  if (index > 0) messages.value.splice(index - 1, 2)
  input.value = message.question
  send()
}
async function send() {
  const question = input.value.trim()
  if (!question || generating.value) return
  if (!store.isLogin) {
    router.push({
      path: '/login',
      query: { redirect: advice.value ? '/?assistant=advice' : route.fullPath },
    })
    return
  }
  const current = ++epoch,
    owner = store.user?.id,
    key = storageKey()
  const history = messages.value
    .filter((m) => m.content && !m.error && !m.loading)
    .slice(-12)
    .map(({ role, content }) => ({ role, content }))
  messages.value.push({ role: 'user', content: question })
  const answer = ref({
    role: 'assistant',
    content: '',
    loading: true,
    question,
  }).value
  messages.value.push(answer)
  input.value = ''
  generating.value = true
  const abort = new AbortController()
  controller = abort
  const timer = setTimeout(() => abort.abort(), advice.value ? 100000 : 125000)
  const valid = () => current === epoch && owner === store.user?.id
  scrollToBottom()
  try {
    if (advice.value) {
      await pendingStop
      if (!valid()) return
      if (!session.value) {
        const created = await api.post(
          '/ai/advice/sessions',
          {},
          { signal: abort.signal },
        )
        if (!valid()) return
        session.value = created
        localStorage.setItem(key, created.id)
      } else {
        const saved = await api.get(`/ai/advice/sessions/${session.value.id}`, {
          signal: abort.signal,
        })
        if (!valid()) return
        session.value = saved
      }
      const result = await api.post(
        `/ai/advice/sessions/${session.value.id}/chat`,
        { message: question, version: session.value.version },
        { timeout: 100000, signal: abort.signal },
      )
      if (!valid()) return
      session.value.version = result.version
      answer.content = result.reply
      answer.recommendation = result.recommendation
    } else {
      const response = await fetch('/api/training/assistant/chat', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('token') || ''}`,
        },
        body: JSON.stringify({
          message: question,
          history,
          competition: props.competition,
          tag: props.tag,
          resourceName: resourceName.value,
        }),
        signal: abort.signal,
      })
      if (!response.ok) {
        if (response.status === 401)
          router.push({ path: '/login', query: { redirect: route.fullPath } })
        throw new Error(
          response.status === 429
            ? '操作太频繁，请稍后重试'
            : '学习问答请求失败，请检查登录状态后重试',
        )
      }
      await readEventStream(response, (event) => {
        if (!valid()) return
        if (event.type === 'chunk') {
          answer.content += event.content
          scrollToBottom()
        }
        if (event.type === 'error') throw new Error(event.content)
      })
      if (!answer.content && valid()) throw new Error('模型未返回内容，请重试')
    }
  } catch (error) {
    if (!valid()) return
    answer.error = true
    if (advice.value && isAdviceSessionMissing(error)) {
      session.value = null
      localStorage.removeItem(key)
      messages.value = messages.value.slice(-2)
      answer.content = '上次会话已过期或被清空，本次问题已保留。点击重试将开启新对话。'
    } else answer.content = abort.signal.aborted
      ? '本轮等待超时，请重试'
      : error.code === 'ERR_NETWORK' || error instanceof TypeError
        ? '网络连接失败，请稍后重试'
        : error.code === 'ECONNABORTED'
          ? '本轮等待超时，请重试'
          : error.message || '暂时无法完成，请重试'
  } finally {
    clearTimeout(timer)
    if (valid()) {
      answer.loading = false
      generating.value = false
      controller = null
      saveHistory()
      scrollToBottom()
    }
  }
}
watch(
  () => store.user?.id,
  (id, old) => {
    stopGenerating(false)
    if (old) localStorage.removeItem(storageKey(old))
    session.value = null
    messages.value = []
    loadHistory()
  },
)
watch(
  () => route.query.assistant,
  (value) => {
    if (advice.value && value === 'advice' && !visible.value) {
      visible.value = true
      loadHistory()
    }
  },
  { immediate: true },
)
if (!visible.value) loadHistory()
onBeforeUnmount(stopGenerating)
defineExpose({ openWithResource })
</script>
<style scoped>
.xiaozhi-launcher {
  position: fixed;
  right: 28px;
  bottom: 28px;
  z-index: 30;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 48px;
  padding: 0 18px;
  border: 0;
  border-radius: 24px;
  color: #fff;
  background: linear-gradient(135deg, #3867ed, #7657e8);
  box-shadow: 0 12px 30px rgba(56, 103, 237, 0.32);
  cursor: pointer;
  font-weight: 650;
}
.xiaozhi-launcher.open {
  transform: translateY(2px);
}
.xiaozhi-panel {
  position: fixed;
  right: 28px;
  bottom: 88px;
  z-index: 31;
  width: min(460px, calc(100vw - 32px));
  height: min(620px, calc(100vh - 130px));
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(82, 105, 170, 0.18);
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 24px 70px rgba(31, 47, 89, 0.24);
}
.assistant-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 15px 16px;
  color: #fff;
  background: linear-gradient(135deg, #315fdc, #7356df);
}
.assistant-avatar,
.welcome-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.18);
  font-size: 20px;
}
.assistant-head > div:nth-child(2) {
  flex: 1;
  min-width: 0;
}
.assistant-head strong {
  display: block;
  font-size: 16px;
}
.assistant-head small {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 2px;
  opacity: 0.82;
  font-size: 10px;
}
.assistant-head small i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #72e0a2;
}
.assistant-head button {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 0;
  border-radius: 8px;
  color: #fff;
  background: transparent;
  cursor: pointer;
}
.assistant-head button:hover {
  background: rgba(255, 255, 255, 0.14);
}
.assistant-messages {
  flex: 1;
  overflow-y: auto;
  padding: 18px 16px;
  background: #f7f8fc;
}
.assistant-welcome {
  padding: 22px 8px;
  text-align: center;
  color: #34405a;
}
.assistant-welcome .welcome-icon {
  margin: 0 auto 12px;
  color: #fff;
  background: linear-gradient(135deg, #3867ed, #7657e8);
}
.assistant-welcome p {
  margin: 8px auto 18px;
  max-width: 310px;
  color: #7b879c;
  font-size: 12px;
  line-height: 1.75;
}
.quick-prompts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.quick-prompts button {
  padding: 10px;
  border: 1px solid #dfe5f3;
  border-radius: 10px;
  color: #52617a;
  background: #fff;
  cursor: pointer;
  font-size: 11px;
  line-height: 1.45;
}
.quick-prompts button:hover {
  border-color: #7192ef;
  color: #3867ed;
}
.assistant-message {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 15px;
}
.assistant-message.user {
  flex-direction: row-reverse;
}
.message-role {
  display: grid;
  place-items: center;
  flex: 0 0 28px;
  height: 28px;
  border-radius: 9px;
  color: #fff;
  background: #7657e8;
  font-size: 11px;
  font-weight: 700;
}
.user .message-role {
  background: #3867ed;
}
.message-bubble {
  max-width: calc(100% - 48px);
  padding: 10px 12px;
  border-radius: 4px 13px 13px 13px;
  color: #374158;
  background: #fff;
  box-shadow: 0 3px 12px rgba(40, 56, 94, 0.07);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: 13px;
  line-height: 1.7;
}
.user .message-bubble {
  border-radius: 13px 4px 13px 13px;
  color: #fff;
  background: #456fe1;
}
.thinking-dots {
  display: flex;
  gap: 4px;
  padding: 5px 2px;
}
.thinking-dots i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #8c98ad;
  animation: thinking 1.2s infinite;
}
.thinking-dots i:nth-child(2) {
  animation-delay: 0.18s;
}
.thinking-dots i:nth-child(3) {
  animation-delay: 0.36s;
}
.assistant-context {
  padding: 7px 16px;
  color: #5c6f9f;
  background: #edf2ff;
  font-size: 11px;
}
.assistant-context button {
  float: right;
  border: 0;
  color: #7180a4;
  background: transparent;
  cursor: pointer;
}
.assistant-input {
  padding: 12px 14px;
  border-top: 1px solid #edf0f6;
  background: #fff;
}
.assistant-input :deep(textarea) {
  box-shadow: none;
}
.input-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.input-foot span {
  color: #98a1b2;
  font-size: 9px;
}
.assistant-pop-enter-active,
.assistant-pop-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}
.assistant-pop-enter-from,
.assistant-pop-leave-to {
  opacity: 0;
  transform: translateY(12px) scale(0.98);
}
@keyframes thinking {
  0%,
  70%,
  100% {
    opacity: 0.3;
    transform: translateY(0);
  }
  35% {
    opacity: 1;
    transform: translateY(-3px);
  }
}
@media (max-width: 600px) {
  .xiaozhi-launcher {
    right: 16px;
    bottom: 18px;
  }
  .xiaozhi-panel {
    right: 16px;
    bottom: 76px;
    height: calc(100vh - 100px);
  }
}
.advice-card {
  margin-top: 10px;
  padding: 12px;
  border: 1px solid #dfe7f4;
  border-radius: 10px;
  background: #fff;
  white-space: normal;
  overflow-wrap: anywhere;
}
.advice-card strong,
.advice-card small {
  display: block;
  margin-bottom: 6px;
}
.advice-card small {
  color: #738098;
  font-size: 11px;
}
.advice-card p {
  margin: 8px 0;
  font-size: 12px;
}
.advice-card a,
.retry-answer {
  color: #3867ed;
  font-size: 12px;
}
.recommendation-source {
  margin-top: 10px;
  color: #3867ed;
  font-size: 11px;
}
.fallback-note {
  color: #8c7046;
  font-size: 11px;
  white-space: normal;
}
.message-bubble {
  min-width: 0;
  overflow-wrap: anywhere;
}
.assistant-message {
  min-width: 0;
}
@media (max-width: 600px) {
  .xiaozhi-panel {
    width: calc(100vw - 24px);
    right: 12px;
    height: calc(100dvh - 100px);
  }
}
</style>
