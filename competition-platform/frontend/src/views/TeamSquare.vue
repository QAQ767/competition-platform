<template>
  <div class="team-square">
    <PageHeading
      title="优秀的伙伴，让热爱走得更远"
      description="发现技能互补的队伍，或发起属于你的竞赛计划。"
      eyebrow="FIND YOUR TEAM"
    />
    <el-card shadow="never" class="filter-card">
      <el-select
        v-model="filters.competitionId"
        placeholder="目标竞赛"
        clearable
        style="width: 200px"
        @change="onFilterChange"
      >
        <el-option
          v-for="c in competitions"
          :key="c.id"
          :label="c.name"
          :value="c.id"
        />
      </el-select>
      <el-select
        v-model="filters.skill"
        placeholder="技能标签"
        clearable
        style="width: 140px; margin-left: 10px"
        @change="onFilterChange"
      >
        <el-option v-for="s in SKILLS" :key="s" :label="s" :value="s" />
      </el-select>
      <el-select
        v-model="filters.status"
        placeholder="队伍状态"
        clearable
        style="width: 140px; margin-left: 10px"
        @change="onFilterChange"
      >
        <el-option label="招募中" value="招募中" />
        <el-option label="已满员" value="已满员" />
        <el-option label="备赛中" value="备赛中" />
      </el-select>
      <el-input
        v-model="filters.keyword"
        placeholder="搜索队伍标题"
        clearable
        style="width: 200px; margin-left: 10px"
        @keyup.enter="onFilterChange"
        @clear="onFilterChange"
      />
      <el-button
        type="primary"
        style="margin-left: 10px"
        @click="onFilterChange"
        >查询</el-button
      >
      <el-button
        type="primary"
        plain
        style="margin-left: 10px"
        @click="openCreate"
        >＋ 创建组队</el-button
      >
      <el-button
        type="success"
        plain
        style="margin-left: 10px"
        @click="router.push('/?assistant=advice')"
        >让推荐帮我找队</el-button
      >
    </el-card>

    <el-row :gutter="16">
      <el-col
        :xs="24"
        :sm="12"
        :lg="8"
        v-for="t in teams"
        :key="t.id"
        style="margin-bottom: 16px"
      >
        <el-card shadow="hover" class="team-card">
          <div class="team-emblem">
            <el-icon><Connection /></el-icon><span>寻找一起突破的伙伴</span>
          </div>
          <div class="team-head">
            <router-link :to="`/teams/${t.id}`" class="team-title">{{
              t.title
            }}</router-link>
            <el-tag :type="TEAM_STATUS_TYPE[t.status] || 'info'" size="small">{{
              t.status
            }}</el-tag>
          </div>
          <div class="team-meta">
            <el-icon><Trophy /></el-icon> {{ t.competitionName }}
          </div>
          <div class="team-meta">
            <el-icon><User /></el-icon> 队长：{{ t.captainName }}
          </div>
          <div class="team-meta">
            <el-icon><Connection /></el-icon> 成员：{{ t.memberCount }} /
            {{ t.maxMembers }}
          </div>
          <el-progress
            :percentage="
              Math.min(
                100,
                Math.round(((t.memberCount || 0) / (t.maxMembers || 1)) * 100)
              )
            "
            :show-text="false"
            :stroke-width="5"
            class="team-progress"
          />
          <div class="team-skills">
            <el-tag
              v-for="s in t.skills"
              :key="s"
              size="small"
              type="warning"
              effect="plain"
              style="margin-right: 6px"
              >{{ s }}</el-tag
            >
          </div>
          <div class="team-actions">
            <el-button type="primary" link @click="goDetail(t.id)"
              >查看队伍 <el-icon><ArrowRight /></el-icon
            ></el-button>
            <el-button
              v-if="store.isLogin && t.joined"
              type="success"
              size="small"
              plain
              @click.stop="goDetail(t.id)"
              >我的队伍</el-button
            >
            <el-button
              v-else-if="store.isLogin && t.status === '招募中'"
              type="primary"
              size="small"
              plain
              @click.stop="openApply(t)"
              >申请入队</el-button
            >
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-empty v-if="!teams.length" description="暂无队伍" />
    <el-pagination
      v-if="teamsTotal > teamsPageSize"
      layout="prev, pager, next, total"
      :total="teamsTotal"
      :page-size="teamsPageSize"
      :current-page="teamsPage"
      style="justify-content: center; margin-top: 8px"
      @current-change="onTeamPageChange"
    />

    <button v-show="!hallOpen" type="button" class="hall-dock" aria-label="打开组队交流大厅" :aria-expanded="hallOpen" @click="hallOpen = true">
      <span class="hall-dock-icon"><el-icon><ChatDotRound /></el-icon></span>
      <span class="hall-dock-label"><strong>组队交流大厅</strong><small>在这里遇见未来队友</small></span>
      <span class="hall-preview">
        <span v-for="m in recentHallMsgs" :key="m.id" class="hall-preview-line"><b>{{ m.userName }}</b><span>{{ m.content }}</span></span>
        <span v-if="!recentHallMsgs.length" class="hall-preview-empty">{{ store.isLogin ? '还没有消息，来打个招呼吧' : '登录后，和同学们聊聊组队计划' }}</span>
      </span>
      <span class="hall-dock-action">展开交流 <el-icon><ArrowUp /></el-icon></span>
    </button>
    <el-drawer v-model="hallOpen" direction="rtl" size="min(640px, 100vw)" :with-header="false" append-to-body class="hall-drawer" @opened="scrollHallBottom">
      <section class="hall-room" aria-label="组队交流大厅">
        <header class="hall-room-header"><span class="hall-room-icon"><el-icon><ChatDotRound /></el-icon></span><div><span class="hall-eyebrow">MEET YOUR TEAM</span><h2>组队交流大厅</h2></div><button type="button" aria-label="收起交流大厅" class="hall-close" @click="hallOpen = false">×</button></header>
        <div class="hall-room-notice"><span class="hall-live-dot"></span>聊目标、找伙伴，点击头像或昵称查看更多操作</div>
        <div ref="hallListRef" class="hall-msg-list">
          <button v-if="store.isLogin && !hallLoadedAll" type="button" class="hall-load-more" :disabled="hallLoadingEarlier" @click="loadHallEarlier">{{ hallLoadingEarlier ? '正在加载…' : '加载更早消息' }}</button>
          <div v-if="!hallMsgs.length" class="hall-empty"><el-icon><Connection /></el-icon><strong>{{ store.isLogin ? '新的合作，从打招呼开始' : '登录后加入交流' }}</strong><p>说说你的技能，或分享想参加的竞赛</p><el-button v-if="!store.isLogin" type="primary" round @click="router.push({path:'/login',query:{redirect:'/teams'}})">登录参与</el-button></div>
          <article v-for="m in hallMsgs" :key="m.id" class="hall-msg" :class="{ mine: m.userId === store.user?.id }">
            <button type="button" class="hall-avatar" :aria-label="'查看' + m.userName + '的用户菜单'" @click="openHallUserMenu($event, m)" @contextmenu.prevent="openHallUserMenu($event, m)">{{ (m.userName || '友').slice(0, 1) }}</button>
            <div class="hall-message-body"><div class="hall-message-meta"><button type="button" class="hall-name" @click="openHallUserMenu($event, m)" @contextmenu.prevent="openHallUserMenu($event, m)">{{ m.userId === store.user?.id ? '我' : m.userName }}</button><time>{{ m.createTime }}</time></div><div class="hall-content">{{ m.content }}</div></div>
          </article>
        </div>
        <footer class="hall-input"><el-input v-model="hallText" type="textarea" :rows="3" resize="none" maxlength="1000" :placeholder="store.isLogin ? '介绍一下自己，聊聊想加入的队伍…' : '登录后即可参与交流'" aria-label="大厅消息内容" :disabled="!store.isLogin" @keydown="onHallKeydown" /><div class="hall-composer-footer"><span>Enter 发送，Shift + Enter 换行</span><el-button type="primary" round :loading="hallSending" :disabled="!store.isLogin || !hallText.trim()" @click="sendHall">发送消息 <el-icon><Right /></el-icon></el-button></div></footer>
      </section>
    </el-drawer>


    <el-dialog v-model="applyVisible" title="申请加入队伍" width="420px">
      <p style="margin-top: 0">
        <b>{{ applyTeam?.title }}</b>
      </p>
      <el-input
        v-model="applyIntro"
        type="textarea"
        :rows="4"
        placeholder="介绍一下自己：擅长什么、能承担什么角色"
      />
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="applying" @click="doApply"
          >提交申请</el-button
        >
      </template>
    </el-dialog>

    <el-dialog v-model="createVisible" title="创建组队" width="520px">
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="目标竞赛" required>
          <el-select
            v-model="createForm.competitionId"
            placeholder="选择竞赛"
            style="width: 100%"
          >
            <el-option
              v-for="c in competitions"
              :key="c.id"
              :label="c.name"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="组队标题" required>
          <el-input
            v-model="createForm.title"
            placeholder="如：数学建模组队招人（缺数据分析）"
          />
        </el-form-item>
        <el-form-item label="队伍简介">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            placeholder="描述队伍定位、缺什么样的人"
          />
        </el-form-item>
        <el-form-item label="人数上限" required>
          <el-input-number v-model="createForm.maxMembers" :min="2" :max="10" />
        </el-form-item>
        <el-form-item label="所需技能">
          <el-select
            v-model="createForm.skills"
            multiple
            placeholder="选择需要的技能（AI 将据此推荐队友）"
            style="width: 100%"
          >
            <el-option v-for="s in SKILLS" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker
            v-model="createForm.deadline"
            type="datetime"
            placeholder="组队截止"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="doCreate"
          >创建</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'
import { useUserStore } from '../stores/user'
import { SKILLS, TEAM_STATUS_TYPE } from '../utils/constants'
import {
  connectWebSocket,
  isWsOpen,
  onWsMessage
} from '../utils/websocket'
import { openUserMenu } from '../utils/userMenu'
import { shouldSendOnEnter } from '../utils/assistant'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const competitions = ref([])
const teams = ref([])
const teamsPage = ref(1)
const teamsPageSize = 9
const teamsTotal = ref(0)
const filters = reactive({
  competitionId: null,
  skill: '',
  status: '',
  keyword: ''
})

const createVisible = ref(false)
const creating = ref(false)
const createForm = reactive({
  competitionId: null,
  title: '',
  description: '',
  maxMembers: 3,
  skills: [],
  deadline: ''
})

// 大厅聊天
const hallMsgs = ref([])
const hallOpen = ref(false)
const hallSending = ref(false)
const hallLoadingEarlier = ref(false)
const recentHallMsgs = computed(() => hallMsgs.value.slice(-2))
function onHallKeydown(event) { if (shouldSendOnEnter(event)) { event.preventDefault(); sendHall() } }
function scrollHallBottom() { nextTick(() => { const el = hallListRef.value; if (el) el.scrollTop = el.scrollHeight }) }
function hallNearBottom() { const el = hallListRef.value; return !el || el.scrollHeight - el.scrollTop - el.clientHeight < 80 }
function mergeHall(incoming) { hallMsgs.value = [...new Map([...hallMsgs.value, ...incoming].map(m => [m.id,m])).values()].sort((a,b) => a.id-b.id) }
const hallText = ref('')
const hallListRef = ref(null)
const hallPage = ref(1)
const hallPageSize = 30
const hallLoadedAll = ref(false)
let hallTimer = null
let teamStatusTimer = null
let unsubHall = null

// WebSocket 实时推送：新大厅消息追加（按 id 去重）
function handleWs(msg) {
  if (msg.type === 'HALL' && msg.data) {
    const exist = hallMsgs.value.some((m) => m.id === msg.data.id)
    if (!exist) {
      const follow = hallNearBottom()
      mergeHall([msg.data])
      if (follow) scrollHallBottom()
    }
  }
}

// 快捷申请
const applyVisible = ref(false)
const applyTeam = ref(null)
const applyIntro = ref('')
const applying = ref(false)

async function loadHall() {
  // 聊天接口需要登录；访客只加载公开队伍，避免 401 将广场跳转到登录页。
  if (!store.isLogin) return
  const follow = hallNearBottom()
  try {
    const data = await api.get('/chat/hall', {
      params: { page: 1, size: hallPageSize }
    })
    mergeHall(data.records || [])
    hallLoadedAll.value = hallMsgs.value.length >= data.total
  } catch (e) {
    /* 接口层提示错误，保留当前消息 */
  }
  if (follow) scrollHallBottom()
}

async function loadHallEarlier() {
  if (!store.isLogin || hallLoadedAll.value || hallLoadingEarlier.value) return
  hallLoadingEarlier.value = true
  const el = hallListRef.value, height = el?.scrollHeight || 0, top = el?.scrollTop || 0
  try {
    const data = await api.get('/chat/hall', {
      params: { page: hallPage.value + 1, size: hallPageSize }
    })
    const older = data.records || []
    mergeHall(older)
    hallPage.value = data.page
    hallLoadedAll.value = hallMsgs.value.length >= data.total
    await nextTick()
    if (el) el.scrollTop = top + el.scrollHeight - height
  } catch (e) {
    /* 接口层提示错误 */
  } finally { hallLoadingEarlier.value = false }
}

async function sendHall() {
  if (!store.isLogin || hallSending.value) return
  const content = hallText.value.trim()
  if (!content) return
  hallSending.value = true
  try {
    await api.post('/chat/hall', { content })
    if (hallText.value.trim() === content) hallText.value = ''
    await loadHall()
    scrollHallBottom()
  } catch { /* 失败保留草稿，接口层显示错误 */ }
  finally { hallSending.value = false }
}

function openHallUserMenu(event, message) {
  openUserMenu(event, { userId: message.userId, userName: message.userName })
}

function openApply(t) {
  if (t.joined) { goDetail(t.id); return }
  if (!store.isLogin) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  applyTeam.value = t
  applyIntro.value = ''
  applyVisible.value = true
}

async function doApply() {
  if (applying.value) return
  applying.value = true
  try {
    await api.post(`/teams/${applyTeam.value.id}/apply`, {
      intro: applyIntro.value
    })
    ElMessage.success('申请已提交，等待队长审批')
    applyVisible.value = false
  } catch {
    // 保留申请内容，方便重试。
  } finally {
    applying.value = false
  }
}

let teamRequestId = 0
async function load() {
  const requestId = ++teamRequestId
  const params = { page: teamsPage.value, size: teamsPageSize }
  if (filters.competitionId) params.competitionId = filters.competitionId
  if (filters.skill) params.skill = filters.skill
  if (filters.status) params.status = filters.status
  if (filters.keyword) params.keyword = filters.keyword
  try {
    const data = await api.get('/teams', { params })
    if (requestId !== teamRequestId) return
    teams.value = data.records || []
    teamsTotal.value = data.total || 0
  } catch {
    /* 接口层已提示，保留原列表 */
  }
}

function onTeamPageChange(page) {
  teamsPage.value = page
  load()
}

function onFilterChange() {
  teamsPage.value = 1
  load()
}

async function loadCompetitions() {
  competitions.value = await api.get('/competitions')
}

function goDetail(id) {
  router.push(`/teams/${id}`)
}

function openCreate() {
  if (!store.isLogin) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  createForm.competitionId = filters.competitionId
  createVisible.value = true
}

async function doCreate() {
  if (creating.value) return
  if (!createForm.competitionId || !createForm.title) {
    ElMessage.warning('请填写竞赛和标题')
    return
  }
  creating.value = true
  try {
    const team = await api.post('/teams', createForm)
    ElMessage.success('创建成功！去看看 AI 推荐的队友吧')
    createVisible.value = false
    router.push(`/teams/${team.id}`)
  } catch {
    // 接口层提示错误，保留创建表单。
  } finally {
    creating.value = false
  }
}

onMounted(() => {
  if (route.query.competitionId) {
    filters.competitionId = Number(route.query.competitionId)
  }
  loadCompetitions()
  load()
  connectWebSocket()
  teamStatusTimer = setInterval(load, 30000)
  loadHall()
  unsubHall = onWsMessage(handleWs)
  // WS 断开时降级轮询兜底
  hallTimer = setInterval(() => {
    if (!isWsOpen()) loadHall()
  }, 5000)
})

onBeforeUnmount(() => {
  if (teamStatusTimer) clearInterval(teamStatusTimer)
  if (hallTimer) clearInterval(hallTimer)
  if (unsubHall) unsubHall()
})
</script>

<style scoped>
.filter-card {
  margin-bottom: 16px;
}
.team-card {
  cursor: pointer;
}
.team-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.team-title {
  font-size: 15px;
  font-weight: 600;
}
.team-meta {
  color: #606266;
  font-size: 13px;
  margin-top: 6px;
}
.team-skills {
  margin-top: 10px;
}
.team-actions {
  margin-top: 12px;
}
.hall-chat {
  margin-top: 16px;
}
.hall-msg-list {
  height: 260px;
  overflow-y: auto;
  background: #f5f7fa;
  border-radius: 6px;
  padding: 10px 12px;
}
.hall-msg {
  font-size: 13px;
  padding: 4px 0;
  line-height: 1.6;
}
.hall-name {
  color: #409eff;
  font-weight: 600;
  cursor: pointer;
  margin-right: 6px;
}
.hall-name:hover {
  text-decoration: underline;
}
.hall-content {
  color: #303133;
}
.hall-time {
  color: #c0c4cc;
  font-size: 11px;
  margin-left: 8px;
}
.hall-input {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}
.team-card {
  cursor: default;
  height: 100%;
}
.team-emblem {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #8594ad;
  font-size: 11px;
  margin-bottom: 20px;
}
.team-emblem > .el-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  background: #edf2ff;
  color: #6184e5;
  border-radius: 10px;
  font-size: 22px;
}
.team-head {
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}
.team-title {
  color: #24324b;
  text-decoration: none;
  line-height: 1.6;
}
.team-title:hover {
  color: #3867ed;
}
.team-head .el-tag {
  flex-shrink: 0;
}
.team-meta {
  display: flex;
  gap: 7px;
  align-items: center;
  font-size: 12px;
  line-height: 1.8;
  color: #7b889f;
}
.team-progress {
  margin-top: 16px;
}
.team-skills {
  min-height: 32px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 16px;
}
.team-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid #edf0f5;
  padding-top: 15px;
}
.hall-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
}
.hall-heading small {
  color: #8c98ac;
  font-size: 11px;
  font-weight: 400;
}
.hall-msg {
  padding: 10px 0;
  border-bottom: 1px solid #e9edf4;
}
.hall-chat {
  margin-top: 28px;
}
.team-square{padding-bottom:135px}
.hall-dock{position:fixed;left:260px;right:166px;bottom:22px;z-index:900;display:flex;align-items:center;gap:18px;min-height:92px;padding:16px 22px;text-align:left;font:inherit;border:1px solid #dfe7f5;border-radius:18px;background:rgba(255,255,255,.97);box-shadow:0 10px 40px #243c6b18;backdrop-filter:blur(18px);cursor:pointer;transition:box-shadow .2s,border-color .2s}.hall-dock:hover{border-color:#a9bdf0;box-shadow:0 12px 40px #3867ed23}.hall-dock-icon,.hall-room-icon{display:grid;place-items:center;flex-shrink:0;width:46px;height:46px;border-radius:15px;background:#edf2ff;color:#5279dc;font-size:24px}.hall-dock-label{flex-shrink:0}.hall-dock-label strong{display:block;font-size:14px;color:#304460}.hall-dock-label small{display:block;font-size:10px;color:#8c9bb2;margin-top:7px}.hall-preview{min-width:0;flex:1;padding-left:20px;border-left:1px solid #e8edf6;display:flex;flex-direction:column;gap:9px}.hall-preview-line{display:flex;gap:10px;min-width:0;font-size:12px}.hall-preview-line b{color:#59739c;max-width:95px;white-space:nowrap;text-overflow:ellipsis;overflow:hidden;flex-shrink:0}.hall-preview-line>span{color:#8795aa;white-space:nowrap;text-overflow:ellipsis;overflow:hidden}.hall-preview-empty{font-size:12px;color:#96a3b5;line-height:1.7}.hall-dock-action{color:#5479d8;font-size:12px;display:flex;align-items:center;gap:8px;white-space:nowrap}.hall-room{height:100%;display:flex;flex-direction:column;min-height:0;color:#344862}.hall-room-header{display:flex;align-items:center;gap:14px;padding:25px 26px 20px}.hall-room-header h2{font-size:21px;margin:6px 0 0}.hall-eyebrow{font-size:10px;letter-spacing:2px;color:#92a3bd}.hall-close{margin-left:auto;flex-shrink:0;width:34px;height:34px;border:0;background:#f1f5fb;color:#7185a4;border-radius:10px;font-size:25px;cursor:pointer}.hall-room-notice{display:flex;align-items:center;gap:8px;margin:0 26px 18px;font-size:12px;color:#92a0b4}.hall-live-dot{width:6px;height:6px;border-radius:50%;background:#65b6a5}.hall-msg-list{flex:1;min-height:0;height:auto;overflow:auto;padding:24px;background:#f7f9fd;overscroll-behavior:contain;border-top:1px solid #edf1f8}.hall-load-more{display:block;margin:0 auto 20px;border:0;background:#eaf0fb;color:#7086aa;border-radius:20px;padding:7px 16px;font-size:11px;cursor:pointer}.hall-msg{display:flex;align-items:flex-start;gap:10px;margin-bottom:23px;padding:0;border:0}.hall-avatar{display:grid;place-items:center;flex-shrink:0;width:34px;height:34px;border:0;border-radius:11px;background:#e9efff;color:#5275ce;font-size:14px;cursor:pointer}.hall-message-body{min-width:0;max-width:84%}.hall-message-meta{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:7px}.hall-name{border:0;padding:0;margin:0;background:transparent;font-size:12px;color:#607696;font-weight:600;cursor:pointer}.hall-message-meta time{font-size:10px;color:#9ca9bc}.hall-content{padding:12px 16px;border:1px solid #e7edf6;border-radius:0 14px 14px 14px;background:white;color:#40516a;font-size:14px;line-height:1.8;white-space:pre-wrap;overflow-wrap:anywhere}.hall-msg.mine{flex-direction:row-reverse}.mine .hall-message-meta{justify-content:flex-end}.mine .hall-content{background:#3867ed;color:white;border-color:#3867ed;border-radius:14px 0 14px 14px}.hall-input{display:block;margin:0;padding:16px 24px 20px;border-top:1px solid #e9eef6;background:white}.hall-input :deep(.el-textarea__inner){box-shadow:none;padding:4px 0;line-height:1.8;background:transparent}.hall-composer-footer{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-top:8px}.hall-composer-footer>span{font-size:11px;color:#98a4b7}.hall-composer-footer .el-icon{margin-left:7px}.hall-empty{text-align:center;color:#93a2b7;font-size:13px;padding:60px 15px;line-height:1.8}.hall-empty>.el-icon{display:block;margin:0 auto 18px;font-size:40px;color:#9bb2e7}.hall-empty strong{display:block;color:#5c7293;font-size:16px}.hall-dock:focus-visible,.hall-close:focus-visible{outline:2px solid #3867ed;outline-offset:3px}
@media(max-width:1100px){.hall-dock{left:228px;gap:12px;padding:14px 16px}.hall-dock-label small{display:none}.hall-dock-action{font-size:0}.hall-dock-action .el-icon{font-size:16px}}
@media(max-width:767px){.hall-dock{left:12px;right:12px;bottom:82px;min-height:90px;padding:12px;gap:10px;border-radius:15px}.hall-dock-icon{width:35px;height:35px;border-radius:11px;font-size:20px}.hall-dock-label{display:none}.hall-preview{padding-left:0;border:0;gap:10px}.hall-preview-line{font-size:11px;gap:8px}.hall-preview-line b{max-width:65px}.team-square{padding-bottom:185px}.hall-room-header{padding:20px 18px 16px}.hall-room-notice{margin:0 18px 16px;font-size:11px}.hall-msg-list{padding:18px 12px}.hall-input{padding:12px 16px calc(16px + env(safe-area-inset-bottom))}.hall-message-body{max-width:83%}}

</style>
<style>
.hall-drawer .el-drawer__body{padding:0;overflow:hidden}
.hall-drawer{border-radius:22px 0 0 22px}
@media(max-width:767px){.hall-drawer{border-radius:0}}
</style>
