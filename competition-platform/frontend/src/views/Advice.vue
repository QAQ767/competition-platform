<template>
  <div class="advice-page">
    <PageHeading
      title="把方向说清楚，让合适的赛事与队伍找到你"
      description="基于你的档案、技能与可投入时间做规则筛选，再由模型解释匹配理由；推荐不会自动报名或入队。"
      eyebrow="FIND YOUR NEXT CHALLENGE"
    >
      <el-button size="small" @click="router.push('/teams')">去组队广场</el-button>
    </PageHeading>

    <el-alert
      v-if="sessionError"
      class="block"
      type="warning"
      :closable="false"
      show-icon
      :title="sessionError"
      description="你仍然可以使用“仅按档案推荐”；对话补充需求需要 Redis 会话存储恢复后再试。"
    />

    <el-row :gutter="18" class="top-row">
      <el-col :xs="24" :md="14">
        <el-card shadow="never" class="block profile-card">
          <template #header>
            <div class="card-header">
              <span>可用于推荐的资料</span>
              <el-tag size="small" effect="plain">档案只读</el-tag>
            </div>
          </template>
          <div v-if="profile" class="profile-grid">
            <div><span>专业 / 学院</span><strong>{{ profile.major || '未填写' }} · {{ profile.college || '未填写' }}</strong></div>
            <div><span>参赛意向</span><strong>{{ COMPETE_STATUS_LABEL[profile.competeStatus] || '未设置' }}</strong></div>
            <div><span>每周可投入</span><strong>{{ weeklyHoursLabel(profile.weeklyHours, profile.weeklyHoursKnown) }}</strong></div>
            <div><span>项目经历</span><strong>{{ profile.hasProjectExperience ? '已填写' : '未填写' }}</strong></div>
          </div>
          <div v-else class="muted">正在读取档案…</div>
          <div class="tag-line">
            <span class="tag-label">技能</span>
            <template v-if="profile?.skills?.length">
              <el-tag v-for="skill in profile.skills" :key="skill" size="small" class="chip">{{ skill }}</el-tag>
            </template>
            <span v-else class="muted">未填写技能标签</span>
          </div>
          <div class="tag-line">
            <span class="tag-label">期望角色</span>
            <span v-if="profile?.preferredRoles" class="text">{{ profile.preferredRoles }}</span>
            <span v-else class="muted">未填写</span>
          </div>
          <el-alert
            v-if="missingFields.length"
            class="missing"
            type="info"
            :closable="false"
            show-icon
            :title="'资料缺失：' + missingFields.join('、')"
            description="缺失字段不会被推断或补造；补全后匹配证据会更完整。"
          />
        </el-card>
      </el-col>

      <el-col :xs="24" :md="10">
        <el-card shadow="never" class="block action-card">
          <template #header>
            <div class="card-header"><span>生成推荐</span><el-tag v-if="result" size="small" :type="modeMeta(result.mode).type">{{ modeMeta(result.mode).label }}</el-tag></div>
          </template>
          <div class="action-row">
            <span class="action-label">关注时间范围</span>
            <el-select v-model="windowDays" size="small" style="width: 120px">
              <el-option :value="30" label="未来 30 天" />
              <el-option :value="90" label="未来 90 天" />
              <el-option :value="180" label="未来 180 天" />
              <el-option :value="365" label="未来 365 天" />
            </el-select>
          </div>
          <el-button type="primary" class="wide" :loading="loading" @click="recommend(true)">
            {{ session ? '按当前需求推荐' : '根据我的资料推荐' }}
          </el-button>
          <el-button class="wide" :loading="loading" @click="recommend(false)">仅按档案推荐</el-button>
          <p class="hint">先看规则结果也可以；对话补充的条件只有在你确认摘要后才参与推荐。</p>
        </el-card>
      </el-col>
    </el-row>

    <el-card v-if="session" shadow="never" class="block conversation-card">
      <template #header>
        <div class="card-header">
          <span>补充说明自身情况</span>
          <div class="header-actions">
            <el-tag size="small" effect="plain">已确认 {{ session.version }} 次</el-tag>
            <el-button link type="danger" size="small" @click="clearConversation">清空对话</el-button>
          </div>
        </div>
      </template>

      <div class="conversation-body">
        <div class="messages">
          <div v-if="!messages.length" class="muted message-empty">
            可以直接说：我是软件工程专业，会 Vue，每周 6 小时，想找缺前端的队伍，不参加硬件比赛。
          </div>
          <div v-for="(message, index) in messages" :key="index" class="message" :class="message.role">
            <div class="bubble">
              <div class="bubble-text">{{ message.content }}</div>
              <div v-if="message.changes?.length" class="change-tags">
                <el-tag v-for="change in message.changes" :key="change" size="small" type="warning" effect="plain">{{ change }}</el-tag>
              </div>
              <ul v-if="message.questions?.length" class="questions">
                <li v-for="question in message.questions" :key="question">{{ question }}</li>
              </ul>
            </div>
          </div>
        </div>

        <div class="composer">
          <el-input
            v-model="messageText"
            type="textarea"
            :rows="2"
            maxlength="2000"
            show-word-limit
            placeholder="例如：我只能投入 3 小时，不想参加建模了，想找缺后端的队伍"
            @keyup.ctrl.enter="sendMessage"
          />
          <div class="composer-actions">
            <span class="hint">对话内容不会自动写回公开档案；Ctrl + Enter 发送</span>
            <el-button type="primary" size="small" :loading="sending" @click="sendMessage">发送并提取需求</el-button>
          </div>
        </div>

        <el-divider content-position="left">本次需求摘要</el-divider>
        <div class="preference-grid">
          <div class="pref-item">
            <label>每周可投入 <el-tag size="small" :type="sourceMeta(prefs.sources?.weeklyHours).type">{{ sourceMeta(prefs.sources?.weeklyHours).label }}</el-tag></label>
            <el-input-number v-model="prefs.weeklyHours" :min="1" :max="80" size="small" controls-position="right" placeholder="未确定" />
          </div>
          <div class="pref-item">
            <label>关注方向 <el-tag size="small" :type="sourceMeta(prefs.sources?.directions).type">{{ sourceMeta(prefs.sources?.directions).label }}</el-tag></label>
            <el-select v-model="prefs.directions" multiple filterable allow-create default-first-option size="small" placeholder="可多选或输入">
              <el-option v-for="direction in ADVICE_DIRECTION_OPTIONS" :key="direction" :label="direction" :value="direction" />
            </el-select>
          </div>
          <div class="pref-item">
            <label>技能 <el-tag size="small" :type="sourceMeta(prefs.sources?.skills).type">{{ sourceMeta(prefs.sources?.skills).label }}</el-tag></label>
            <el-select v-model="prefs.skills" multiple filterable allow-create default-first-option size="small" placeholder="明确会用的技术">
              <el-option v-for="direction in ADVICE_DIRECTION_OPTIONS" :key="direction" :label="direction" :value="direction" />
            </el-select>
          </div>
          <div class="pref-item">
            <label>期望角色 <el-tag size="small" :type="sourceMeta(prefs.sources?.roles).type">{{ sourceMeta(prefs.sources?.roles).label }}</el-tag></label>
            <el-select v-model="prefs.roles" multiple filterable allow-create default-first-option size="small" placeholder="想承担的角色">
              <el-option v-for="direction in ADVICE_DIRECTION_OPTIONS" :key="direction" :label="direction" :value="direction" />
            </el-select>
          </div>
          <div class="pref-item">
            <label>难度偏好 <el-tag size="small" :type="sourceMeta(prefs.sources?.difficulty).type">{{ sourceMeta(prefs.sources?.difficulty).label }}</el-tag></label>
            <el-select v-model="prefs.difficulty" clearable size="small" placeholder="不限定">
              <el-option v-for="item in ADVICE_DIFFICULTY_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
          </div>
          <div class="pref-item">
            <label>排除方向 <el-tag size="small" :type="sourceMeta(prefs.sources?.excludedKeywords).type">{{ sourceMeta(prefs.sources?.excludedKeywords).label }}</el-tag></label>
            <el-select v-model="prefs.excludedKeywords" multiple filterable allow-create default-first-option size="small" placeholder="明确不参加的方向">
              <el-option v-for="direction in ADVICE_DIRECTION_OPTIONS" :key="direction" :label="direction" :value="direction" />
            </el-select>
          </div>
          <div class="pref-item pref-wide">
            <label>补充说明 <el-tag size="small" :type="sourceMeta(prefs.sources?.note).type">{{ sourceMeta(prefs.sources?.note).label }}</el-tag></label>
            <el-input v-model="prefs.note" size="small" maxlength="500" placeholder="其他明确约束，不会写回公开档案" />
          </div>
        </div>
        <div class="pref-actions">
          <el-checkbox v-model="prefs.teamOnly">只看队伍</el-checkbox>
          <span class="hint">来源标签表示该值来自档案、本次对话还是你手动确认。</span>
          <div>
            <el-button size="small" :loading="savingPrefs" @click="savePreferences">保存摘要</el-button>
            <el-button type="primary" size="small" :loading="savingPrefs || loading" @click="saveAndRecommend">保存并重新推荐</el-button>
          </div>
        </div>
      </div>
    </el-card>

    <el-alert
      v-if="result && resultStale"
      class="block"
      type="warning"
      :closable="false"
      show-icon
      title="需求摘要已变化，当前结果还是上一版"
      description="重新生成后才会按新条件筛选。"
    >
      <template #default><el-button size="small" type="primary" @click="recommend(true)">重新生成</el-button></template>
    </el-alert>

    <template v-if="result">
      <div class="result-header">
        <div>
          <h2>为你找到的下一步</h2>
          <p>{{ formatDateTime(result.generatedAt) }} 生成 · 仅根据平台内已发布赛事与可申请队伍</p>
        </div>
        <div class="result-tags">
          <el-tag :type="modeMeta(result.mode).type">{{ modeMeta(result.mode).label }}</el-tag>
          <el-tag :type="confidenceMeta(result.confidence).type" effect="plain">{{ confidenceMeta(result.confidence).label }}</el-tag>
          <el-tag v-if="result.windowDays" type="info" effect="plain">{{ result.windowDays }} 天窗口</el-tag>
        </div>
      </div>

      <el-alert
        v-if="result.fallbackReason"
        class="block"
        type="info"
        :closable="false"
        show-icon
        :title="result.fallbackReason"
      />
      <el-alert
        v-if="result.insight"
        class="block"
        type="success"
        :closable="false"
        show-icon
        :title="'分析结论：' + result.insight"
      />
      <div v-if="result.notes?.length" class="notes">
        <el-icon><InfoFilled /></el-icon>
        <ul><li v-for="note in result.notes" :key="note">{{ note }}</li></ul>
      </div>

      <el-tabs v-model="activeTab" class="result-tabs">
        <el-tab-pane :label="`竞赛推荐 (${result.competitions?.length || 0})`" name="competitions">
          <div v-if="result.competitions?.length" class="card-grid">
            <el-card v-for="item in result.competitions" :key="item.id" shadow="hover" class="rec-card">
              <div class="rec-top">
                <el-tag size="small" :type="competitionCategoryMeta(item.category).type">{{ competitionCategoryMeta(item.category).label }}</el-tag>
                <span class="score">{{ formatScore(item.matchScore) }}<small>/100</small></span>
              </div>
              <h3>{{ item.name }}</h3>
              <p class="meta">{{ item.organizer || '主办方待公布' }} · {{ item.level || '级别待确认' }}</p>
              <div class="date-line">
                <span>报名 {{ formatDate(item.signupStart) }} — {{ formatDate(item.signupEnd) }}</span>
                <span>开赛 {{ formatDate(item.startTime) }}</span>
              </div>
              <div v-if="item.tags?.length" class="chip-line">
                <el-tag v-for="tag in item.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
              </div>
              <p class="reason">{{ item.reason }}</p>
              <div v-if="item.risks?.length" class="risk-box">
                <el-icon><Warning /></el-icon>
                <ul><li v-for="risk in item.risks" :key="risk">{{ risk }}</li></ul>
              </div>
              <div class="card-foot">
                <div class="foot-meta">
                  <el-tooltip :content="matchScoreHint(item.matchScore)"><span>{{ completenessLabel(item.evidenceCompleteness) }}</span></el-tooltip>
                  <span>{{ sourceTypeLabel(item.sourceType) }}</span>
                </div>
                <div class="foot-actions">
                  <el-button link type="primary" size="small" @click="router.push(`/competitions/${item.id}`)">查看详情</el-button>
                  <el-button link size="small" @click="router.push({ path: '/teams', query: { competitionId: item.id } })">去组队</el-button>
                </div>
              </div>
            </el-card>
          </div>
          <el-empty v-if="prefs.teamOnly" description="已勾选“只看队伍”，本次不返回竞赛推荐">
            <el-button @click="prefs.teamOnly = false; recommend(true)">改为同时看竞赛</el-button>
          </el-empty>
          <el-empty v-else description="没有符合当前条件的竞赛">
            <el-button @click="windowDays = 365; recommend(true)">放宽到 365 天</el-button>
          </el-empty>
        </el-tab-pane>

        <el-tab-pane :label="`队伍推荐 (${result.teams?.length || 0})`" name="teams">
          <div v-if="result.teams?.length" class="card-grid">
            <el-card v-for="item in result.teams" :key="item.id" shadow="hover" class="rec-card">
              <div class="rec-top">
                <el-tag size="small" :type="teamCategoryMeta(item.category).type">{{ teamCategoryMeta(item.category).label }}</el-tag>
                <span class="score">{{ formatScore(item.matchScore) }}<small>/100</small></span>
              </div>
              <h3>{{ item.title }}</h3>
              <p class="meta">{{ item.competitionName || '赛事待确认' }} · 队长 {{ item.captainName || '未知' }}</p>
              <div class="date-line">
                <span>空余 {{ item.remainingPlaces }} / {{ item.maxMembers }} 人</span>
                <span>招募截止 {{ formatDate(item.deadline) }}</span>
              </div>
              <div v-if="item.requiredSkills?.length" class="chip-line">
                <span class="tag-label">所需</span>
                <el-tag v-for="skill in item.requiredSkills" :key="skill" size="small" type="warning" effect="plain">{{ skill }}</el-tag>
              </div>
              <div v-if="item.memberSkillSummary?.length" class="chip-line">
                <span class="tag-label">现有成员</span>
                <el-tag v-for="skill in item.memberSkillSummary" :key="skill" size="small" type="info" effect="plain">{{ skill }}</el-tag>
              </div>
              <p v-if="item.suggestedRole" class="suggested">建议角色：{{ item.suggestedRole }}</p>
              <p class="reason">{{ item.reason }}</p>
              <div v-if="item.risks?.length" class="risk-box">
                <el-icon><Warning /></el-icon>
                <ul><li v-for="risk in item.risks" :key="risk">{{ risk }}</li></ul>
              </div>
              <div class="card-foot">
                <div class="foot-meta">
                  <el-tooltip :content="matchScoreHint(item.matchScore)"><span>{{ completenessLabel(item.evidenceCompleteness) }}</span></el-tooltip>
                </div>
                <div class="foot-actions">
                  <el-button link type="primary" size="small" @click="router.push(`/teams/${item.id}`)">查看队伍</el-button>
                  <el-button link size="small" @click="router.push(`/teams/${item.id}?apply=1`)">申请加入</el-button>
                </div>
              </div>
            </el-card>
          </div>
          <el-empty v-else description="没有符合当前条件的可申请队伍">
            <el-button @click="windowDays = 365; recommend(true)">放宽到 365 天</el-button>
          </el-empty>
        </el-tab-pane>

        <el-tab-pane v-if="result.appliedTeams?.length" :label="`已申请 (${result.appliedTeams.length})`" name="applied">
          <el-table :data="result.appliedTeams" size="small">
            <el-table-column prop="title" label="队伍" min-width="180" />
            <el-table-column prop="competitionName" label="赛事" min-width="160" />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column label="操作" width="110">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="router.push(`/teams/${row.teamId}`)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </template>

    <el-result
      v-else-if="!loading"
      icon="info"
      title="准备好后生成第一份推荐"
      sub-title="推荐只使用平台内已发布赛事和真实队伍，不会混入网页搜索结果。"
    >
      <template #extra><el-button type="primary" @click="recommend(false)">根据我的资料推荐</el-button></template>
    </el-result>

    <div v-if="loading" class="loading-box">
      <el-skeleton :rows="6" animated />
      <p class="hint">正在过滤候选{{ modeMeta(result?.mode).label === 'AI 辅助分析' ? '并等待模型分析' : '' }}，最长可能需要一两分钟…</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { InfoFilled, Warning } from '@element-plus/icons-vue'
import api from '../api'
import PageHeading from '../components/PageHeading.vue'
import {
  ADVICE_DIFFICULTY_OPTIONS,
  ADVICE_DIRECTION_OPTIONS,
  COMPETE_STATUS_LABEL,
  competitionCategoryMeta,
  completenessLabel,
  confidenceMeta,
  effectiveWindowDays,
  formatDate,
  formatDateTime,
  formatScore,
  matchScoreHint,
  modeMeta,
  preferredResultTab,
  sourceMeta,
  teamCategoryMeta,
  weeklyHoursLabel,
  sourceTypeLabel
} from '../utils/advice'

const router = useRouter()
const session = ref(null)
const messages = ref([])
const result = ref(null)
const resultVersion = ref(-1)
const loading = ref(false)
const sending = ref(false)
const savingPrefs = ref(false)
const sessionError = ref('')
const messageText = ref('')
const activeTab = ref('competitions')

const emptyPreferences = () => ({
  weeklyHours: null,
  directions: [],
  skills: [],
  roles: [],
  difficulty: '',
  excludedKeywords: [],
  windowDays: null,
  teamOnly: false,
  note: '',
  sources: {}
})
const prefs = reactive(emptyPreferences())

// 时间窗口只有一个权威来源：需求摘要里的 windowDays。对话改窗口后这里会同步，不再各自维护。
const windowDays = computed({
  get: () => effectiveWindowDays(prefs),
  set: (value) => {
    prefs.windowDays = value || null
  }
})

const profile = computed(
  () => result.value?.profileSummary || session.value?.profileSummary || null
)
const missingFields = computed(
  () => result.value?.missingFields || session.value?.missingFields || []
)
const resultStale = computed(
  () => !!result.value && !!session.value && resultVersion.value !== session.value.version
)

function normalizePreferences(value) {
  const source = value || {}
  return {
    ...emptyPreferences(),
    ...source,
    directions: Array.isArray(source.directions) ? [...source.directions] : [],
    skills: Array.isArray(source.skills) ? [...source.skills] : [],
    roles: Array.isArray(source.roles) ? [...source.roles] : [],
    excludedKeywords: Array.isArray(source.excludedKeywords)
      ? [...source.excludedKeywords]
      : [],
    sources: source.sources && typeof source.sources === 'object' ? { ...source.sources } : {}
  }
}

function applySession(data) {
  session.value = data
  messages.value = data.messages || []
  Object.assign(prefs, normalizePreferences(data.preferences))
}

async function init() {
  try {
    applySession(await api.post('/ai/advice/sessions'))
  } catch {
    sessionError.value = '推荐会话暂不可用'
  }
}

async function ensureSession() {
  if (session.value) return true
  try {
    applySession(await api.post('/ai/advice/sessions'))
    sessionError.value = ''
    return true
  } catch {
    sessionError.value = '推荐会话暂不可用'
    return false
  }
}

async function recommend(useSession = true) {
  if (loading.value) return
  loading.value = true
  const payload = { windowDays: windowDays.value }
  try {
    if (useSession && session.value) {
      result.value = await api.post(
        `/ai/advice/sessions/${session.value.id}/recommendations`,
        payload,
        { timeout: 180000 }
      )
    } else {
      result.value = await api.post('/ai/advice/profile-recommendations', payload, {
        timeout: 180000
      })
    }
    resultVersion.value = session.value?.version ?? -1
    // “只看队伍”时后端不再返回竞赛，直接切到队伍页签，避免看到空竞赛列表。
    activeTab.value = preferredResultTab(prefs)
  } catch (error) {
    if (useSession && session.value && String(error?.message || '').includes('Redis')) {
      sessionError.value = '推荐会话暂不可用，已切换为仅按档案推荐'
      session.value = null
      loading.value = false
      return recommend(false)
    }
    ElMessage.error(error?.message || '生成推荐失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function sendMessage() {
  const text = messageText.value.trim()
  if (!text) {
    ElMessage.warning('请先输入要补充的情况')
    return
  }
  if (text.length > 2000) {
    ElMessage.warning('单条消息最长 2000 字')
    return
  }
  if (!(await ensureSession())) return
  sending.value = true
  try {
    messages.value.push({ role: 'user', content: text, changes: [], questions: [] })
    const data = await api.post(
      `/ai/advice/sessions/${session.value.id}/messages`,
      { message: text },
      { timeout: 120000 }
    )
    messages.value.push({
      role: 'assistant',
      content: data.reply,
      changes: data.changes?.map((item) => item.display || item.field) || [],
      questions: data.questions || []
    })
    session.value = { ...session.value, version: data.version, preferences: data.preferences }
    Object.assign(prefs, normalizePreferences(data.preferences))
    messageText.value = ''
    if (data.fallbackReason) {
      ElMessage.info(`本次为规则提取：${data.fallbackReason}`)
    }
  } catch (error) {
    messages.value.pop()
    ElMessage.error(error?.message || '发送失败，请稍后重试')
  } finally {
    sending.value = false
  }
}

async function savePreferences() {
  if (!session.value) {
    ElMessage.warning('当前没有可保存的会话')
    return false
  }
  savingPrefs.value = true
  try {
    const data = await api.put(`/ai/advice/sessions/${session.value.id}/preferences`, {
      version: session.value.version,
      preferences: { ...prefs }
    })
    applySession(data)
    ElMessage.success('需求摘要已保存')
    return true
  } catch (error) {
    ElMessage.error(error?.message || '保存失败，请刷新后重试')
    return false
  } finally {
    savingPrefs.value = false
  }
}

async function saveAndRecommend() {
  if (await savePreferences()) await recommend(true)
}

async function clearConversation() {
  if (session.value) {
    try {
      await api.delete(`/ai/advice/sessions/${session.value.id}`)
    } catch {
      /* 会话可能已过期，继续清理本地状态 */
    }
  }
  session.value = null
  messages.value = []
  result.value = null
  resultVersion.value = -1
  sessionError.value = ''
  Object.assign(prefs, emptyPreferences())
  ElMessage.success('已清空对话，恢复为按档案推荐')
  await recommend(false)
}

onMounted(init)
</script>

<style scoped>
.advice-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.block {
  margin-bottom: 0;
}
.top-row {
  margin: 0;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.profile-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 20px;
}
.profile-grid span {
  display: block;
  color: #8b97aa;
  font-size: 11px;
  margin-bottom: 5px;
}
.profile-grid strong {
  font-size: 13px;
  color: #31405c;
}
.tag-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 16px;
}
.tag-label {
  color: #8b97aa;
  font-size: 11px;
}
.chip {
  margin: 0;
}
.text {
  font-size: 12px;
  color: #485a78;
}
.muted {
  color: #97a2b4;
  font-size: 12px;
}
.missing {
  margin-top: 18px;
}
.action-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.action-label {
  color: #62718b;
  font-size: 12px;
}
.wide {
  width: 100%;
  margin: 0 0 10px;
}
.hint {
  color: #8a97aa;
  font-size: 11px;
  line-height: 1.8;
  margin: 2px 0 0;
}
.conversation-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.messages {
  max-height: 300px;
  overflow-y: auto;
  padding: 8px;
  background: #f8faff;
  border: 1px solid #edf1f8;
  border-radius: 10px;
}
.message-empty {
  padding: 12px;
}
.message {
  display: flex;
  margin-bottom: 10px;
}
.message.user {
  justify-content: flex-end;
}
.bubble {
  max-width: 82%;
  background: #fff;
  border: 1px solid #e4eaf5;
  border-radius: 10px;
  padding: 10px 12px;
}
.message.user .bubble {
  background: #edf2ff;
  border-color: #d8e2fb;
}
.bubble-text {
  font-size: 12px;
  line-height: 1.8;
  color: #34425a;
  white-space: pre-wrap;
}
.change-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 8px;
}
.questions {
  margin: 8px 0 0;
  padding-left: 18px;
  color: #8a6d3b;
  font-size: 11px;
  line-height: 1.8;
}
.composer-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}
.preference-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px 18px;
}
.pref-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.pref-item label {
  font-size: 11px;
  color: #68768d;
  display: flex;
  align-items: center;
  gap: 6px;
}
.pref-item :deep(.el-select),
.pref-item :deep(.el-input-number) {
  width: 100%;
}
.pref-wide {
  grid-column: span 3;
}
.pref-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  flex-wrap: wrap;
}
.result-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
  margin-top: 8px;
}
.result-header h2 {
  font-size: 20px;
  margin-bottom: 6px;
}
.result-header p {
  color: #8b97aa;
  font-size: 11px;
  margin: 0;
}
.result-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.notes {
  display: flex;
  gap: 8px;
  color: #7d8aa0;
  font-size: 11px;
  line-height: 1.9;
  background: #f8faff;
  border: 1px solid #edf1f8;
  border-radius: 9px;
  padding: 10px 14px;
}
.notes ul {
  margin: 0;
  padding-left: 16px;
}
.notes li {
  margin: 0;
}
.result-tabs {
  margin-top: 4px;
}
.card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.rec-card h3 {
  font-size: 15px;
  line-height: 1.6;
  margin: 10px 0 6px;
}
.rec-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.score {
  font-size: 22px;
  font-weight: 700;
  color: #3867ed;
}
.score small {
  font-size: 10px;
  color: #98a3b5;
  font-weight: 400;
}
.meta {
  color: #75839b;
  font-size: 11px;
  margin: 0 0 8px;
}
.date-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  color: #77869e;
  font-size: 11px;
  margin-bottom: 8px;
}
.chip-line {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  align-items: center;
  margin-bottom: 8px;
}
.reason {
  color: #42526e;
  font-size: 12px;
  line-height: 1.9;
  margin: 8px 0;
}
.suggested {
  display: inline-block;
  color: #8a6d3b;
  background: #fff8e8;
  border-radius: 5px;
  padding: 4px 8px;
  font-size: 11px;
}
.risk-box {
  display: flex;
  gap: 6px;
  color: #a15c4a;
  background: #fff7f4;
  border-radius: 7px;
  padding: 8px 10px;
  font-size: 11px;
  line-height: 1.8;
}
.risk-box ul {
  margin: 0;
  padding-left: 14px;
}
.card-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  border-top: 1px solid #edf0f5;
  padding-top: 12px;
  margin-top: 12px;
  flex-wrap: wrap;
}
.foot-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: #95a0b2;
  font-size: 10px;
}
.foot-actions {
  display: flex;
  gap: 8px;
}
.loading-box {
  background: #fff;
  border: 1px solid #e7ecf4;
  border-radius: 12px;
  padding: 24px;
}
@media (max-width: 1000px) {
  .card-grid {
    grid-template-columns: 1fr;
  }
  .preference-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .pref-wide {
    grid-column: span 2;
  }
}
@media (max-width: 700px) {
  .profile-grid,
  .preference-grid {
    grid-template-columns: 1fr;
  }
  .pref-wide {
    grid-column: span 1;
  }
  .result-header {
    flex-direction: column;
    align-items: flex-start;
  }
  .composer-actions,
  .pref-actions {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
