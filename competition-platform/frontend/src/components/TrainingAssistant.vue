<template>
  <button
    type="button"
    class="xiaozhi-launcher"
    :class="{ open: visible }"
    aria-label="打开 AI 学习助手小智"
    @click="visible = !visible"
  >
    <el-icon><MagicStick /></el-icon>
    <span>问小智</span>
  </button>

  <transition name="assistant-pop">
    <section v-if="visible" class="xiaozhi-panel" aria-label="小智 AI 学习助手">
      <header class="assistant-head">
        <div class="assistant-avatar">
          <el-icon><MagicStick /></el-icon>
        </div>
        <div>
          <strong>小智</strong>
          <small><i></i> 本地 AI 学习助手 · DeepSeek R1</small>
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
          @click="visible = false"
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
            可以陪你理解知识点、分析代码、制定计划，也可以出题检验学习效果。
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
          placeholder="输入学习问题，Enter 发送，Shift + Enter 换行"
          @keydown="onKeydown"
        />
        <div class="input-foot">
          <span>回答由本地模型生成，请核对关键信息</span>
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
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ChatDotRound,
  Close,
  Delete,
  MagicStick
} from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'

const props = defineProps({
  competition: { type: String, default: '' },
  tag: { type: String, default: '' }
})

const router = useRouter()
const store = useUserStore()
const visible = ref(false)
const input = ref('')
const messages = ref([])
const generating = ref(false)
const resourceName = ref('')
const messageList = ref(null)
let controller = null

const quickPrompts = [
  '帮我制定一周训练计划',
  '用简单例子解释动态规划',
  '出一道题检验我的掌握程度',
  '帮我分析这段代码的问题'
]

function storageKey() {
  return `xiaozhi-history-${store.user?.id || 'guest'}`
}

function loadHistory() {
  if (!store.isLogin) return
  try {
    const saved = JSON.parse(localStorage.getItem(storageKey()) || '[]')
    if (Array.isArray(saved)) messages.value = saved.slice(-20)
  } catch {
    localStorage.removeItem(storageKey())
  }
}

function saveHistory() {
  if (!store.isLogin) return
  const saved = messages.value
    .filter((message) => message.content && !message.loading && !message.error)
    .slice(-20)
    .map(({ role, content }) => ({ role, content }))
  localStorage.setItem(storageKey(), JSON.stringify(saved))
}

function scrollToBottom() {
  nextTick(() => {
    if (messageList.value)
      messageList.value.scrollTop = messageList.value.scrollHeight
  })
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

function newChat() {
  stopGenerating()
  messages.value = []
  resourceName.value = ''
  localStorage.removeItem(storageKey())
}

function stopGenerating() {
  if (controller) controller.abort()
}

function onKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    send()
  }
}

async function send() {
  const question = input.value.trim()
  if (!question || generating.value) return
  if (!store.isLogin) {
    ElMessage.warning('请先登录后使用小智')
    router.push({ path: '/login', query: { redirect: '/training' } })
    return
  }

  const history = messages.value
    .filter((message) => message.content && !message.loading && !message.error)
    .slice(-12)
    .map(({ role, content }) => ({ role, content }))
  messages.value.push({ role: 'user', content: question })
  const answer = { role: 'assistant', content: '', loading: true }
  messages.value.push(answer)
  input.value = ''
  generating.value = true
  controller = new AbortController()
  scrollToBottom()

  try {
    const response = await fetch('/api/training/assistant/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${localStorage.getItem('token') || ''}`
      },
      body: JSON.stringify({
        message: question,
        history,
        competition: props.competition,
        tag: props.tag,
        resourceName: resourceName.value
      }),
      signal: controller.signal
    })
    if (!response.ok) {
      let message = '小智暂时无法回答，请稍后再试'
      try {
        message = (await response.json()).message || message
      } catch {
        /* 使用默认提示 */
      }
      throw new Error(message)
    }
    await readEventStream(response, (event) => {
      if (event.type === 'chunk') {
        answer.content += event.content
        scrollToBottom()
      } else if (event.type === 'error') {
        throw new Error(event.content)
      }
    })
    if (!answer.content)
      throw new Error('小智没有生成有效回答，请换一种问法再试')
  } catch (error) {
    answer.error = true
    if (error.name === 'AbortError') {
      answer.content = answer.content
        ? `${answer.content}\n\n[已停止生成]`
        : '已停止生成'
    } else {
      const message = error.message || '小智暂时无法回答，请稍后再试'
      answer.content = answer.content
        ? `${answer.content}\n\n[${message}]`
        : message
      ElMessage.error(message)
    }
  } finally {
    answer.loading = false
    generating.value = false
    controller = null
    saveHistory()
    scrollToBottom()
  }
}

async function readEventStream(response, onEvent) {
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder
      .decode(value || new Uint8Array(), { stream: !done })
      .replace(/\r\n/g, '\n')
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
        onEvent(event)
      }
    }
    if (done) break
  }
}

onMounted(loadHistory)
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
  width: min(400px, calc(100vw - 32px));
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
</style>
