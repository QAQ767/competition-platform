<template>
  <div>
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
              v-if="store.isLogin && t.status === '招募中'"
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

    <el-card shadow="never" class="hall-chat">
      <template #header
        ><div class="hall-heading">
          <span>组队交流大厅</span
          ><small>打个招呼，认识未来的队友 · 点击昵称私聊，右键查看资料</small>
        </div></template
      >
      <div class="hall-msg-list" ref="hallListRef">
        <div
          v-if="!hallLoadedAll"
          class="hall-load-more"
          @click="loadHallEarlier"
        >
          <el-icon class="text-icon" aria-hidden="true"><ArrowUp /></el-icon
          >加载更早消息
        </div>
        <div v-for="m in hallMsgs" :key="m.id" class="hall-msg">
          <span
            class="hall-name"
            @click="startDm(m)"
            @contextmenu.prevent="
              openUserMenu($event, { userId: m.userId, userName: m.userName })
            "
            >{{ m.userName }}</span
          >
          <span class="hall-content">{{ m.content }}</span>
          <span class="hall-time">{{ m.createTime }}</span>
        </div>
      </div>
      <div class="hall-input">
        <el-input
          v-model="hallText"
          placeholder="和大厅的同学聊聊组队吧"
          @keyup.enter="sendHall"
          :disabled="!store.isLogin"
        />
        <el-button type="primary" :disabled="!store.isLogin" @click="sendHall"
          >发送</el-button
        >
      </div>
    </el-card>

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
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'
import { useUserStore } from '../stores/user'
import { SKILLS, TEAM_STATUS_TYPE } from '../utils/constants'
import {
  connectWebSocket,
  isWsOpen,
  sendWsMessage,
  onWsMessage
} from '../utils/websocket'
import { openUserMenu } from '../utils/userMenu'

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
      hallMsgs.value.push(msg.data)
      nextTick(() => {
        const el = hallListRef.value
        if (el) el.scrollTop = el.scrollHeight
      })
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
  hallPage.value = 1
  hallLoadedAll.value = false
  try {
    const data = await api.get('/chat/hall', {
      params: { page: 1, size: hallPageSize }
    })
    hallMsgs.value = data.records || []
    hallLoadedAll.value = (data.records || []).length >= data.total
  } catch (e) {
    /* 接口层提示错误，保留当前消息 */
  }
  nextTick(() => {
    const el = hallListRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

async function loadHallEarlier() {
  try {
    const data = await api.get('/chat/hall', {
      params: { page: hallPage.value + 1, size: hallPageSize }
    })
    const older = data.records || []
    hallMsgs.value = [...older, ...hallMsgs.value]
    hallPage.value = data.page
    hallLoadedAll.value = hallMsgs.value.length >= data.total
  } catch (e) {
    /* ignore */
  }
}

async function sendHall() {
  if (!store.isLogin) {
    ElMessage.warning('请先登录')
    return
  }
  const content = hallText.value.trim()
  if (!content) return
  // 优先走 WebSocket 实时推送（服务端广播回来会自动追加）
  if (sendWsMessage({ type: 'HALL', content })) {
    hallText.value = ''
    return
  }
  // WS 未连接时降级为 REST
  await api.post('/chat/hall', { content })
  hallText.value = ''
  loadHall()
}

function startDm(m) {
  if (!store.isLogin) {
    ElMessage.warning('请先登录')
    return
  }
  router.push({ path: '/chat', query: { to: m.userId } })
}

function openApply(t) {
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
</style>
