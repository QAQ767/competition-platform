<template>
  <div v-loading="loading">
    <PageHeading
      title="团队协作空间"
      description="让分工清晰，让进度可见，一起完成下一场挑战。"
      eyebrow="BETTER TOGETHER"
    />
    <el-result
      v-if="loadFailed"
      icon="warning"
      title="队伍加载失败"
      sub-title="队伍可能已解散，或网络暂时不可用。"
      ><template #extra
        ><el-button @click="load">重试</el-button
        ><el-button @click="router.push('/teams')"
          >返回组队广场</el-button
        ></template
      ></el-result
    >
    <template v-if="detail">
      <el-card shadow="never" class="block">
        <div class="team-head">
          <h3 class="team-title">{{ detail.team.title }}</h3>
          <div class="team-head-right">
            <el-button
              v-if="isInTeam && !isCaptain"
              type="warning"
              size="small"
              plain
              @click="leaveTeam"
              >退出队伍</el-button
            >
            <el-button v-if="isCaptain" size="small" plain @click="openRename"
              ><el-icon class="text-icon" aria-hidden="true"><Edit /></el-icon>
              修改队名</el-button
            >
            <el-button v-if="isCaptain" size="small" plain @click="openDescEdit"
              ><el-icon class="text-icon" aria-hidden="true"
                ><EditPen
              /></el-icon>
              编辑简介</el-button
            >
            <el-button
              v-if="isCaptain"
              type="danger"
              size="small"
              plain
              @click="disband"
              >解散队伍</el-button
            >
            <el-tag :type="TEAM_STATUS_TYPE[detail.team.status] || 'info'">{{
              detail.team.status
            }}</el-tag>
          </div>
        </div>
        <el-descriptions :column="2" size="small" style="margin-top: 12px">
          <el-descriptions-item label="目标竞赛">{{
            detail.team.competitionName
          }}</el-descriptions-item>
          <el-descriptions-item label="队长">{{
            detail.team.captainName
          }}</el-descriptions-item>
          <el-descriptions-item label="成员数"
            >{{ detail.team.memberCount }} /
            {{ detail.team.maxMembers }}</el-descriptions-item
          >
          <el-descriptions-item label="截止时间">{{
            detail.team.deadline || '未设置'
          }}</el-descriptions-item>
        </el-descriptions>
        <div class="team-desc">{{ detail.team.description || '暂无简介' }}</div>
        <div class="team-skills">
          <el-tag
            v-for="s in detail.team.skills"
            :key="s"
            size="small"
            type="warning"
            effect="plain"
            style="margin-right: 6px"
          >
            {{ s }}
          </el-tag>
        </div>
      </el-card>

      <el-card shadow="never" class="block">
        <template #header>团队成员（{{ detail.members.length }}）</template>
        <el-table :data="detail.members" size="small">
          <el-table-column label="姓名">
            <template #default="{ row }">
              <span
                class="name-link"
                @contextmenu.prevent="
                  openUserMenu($event, {
                    userId: row.userId,
                    userName: row.userName
                  })
                "
                >{{ row.userName }}</span
              >
            </template>
          </el-table-column>
          <el-table-column label="角色">
            <template #default="{ row }">
              <el-tag
                :type="row.role === '队长' ? 'danger' : 'info'"
                size="small"
                >{{ row.role }}</el-tag
              >
            </template>
          </el-table-column>
          <el-table-column prop="joinedTime" label="加入时间" width="180" />
          <el-table-column v-if="isCaptain" label="操作" width="80">
            <template #default="{ row }">
              <el-button
                v-if="row.role !== '队长'"
                type="danger"
                size="small"
                link
                @click="openKick(row)"
                >移出</el-button
              >
            </template>
          </el-table-column>
        </el-table>
        <div v-if="canApply" style="margin-top: 16px">
          <el-button type="primary" @click="applyVisible = true"
            >申请加入该队伍</el-button
          >
        </div>
        <el-alert
          v-else-if="isCaptain"
          type="info"
          :closable="false"
          show-icon
          title="你是本队队长，可通过下方选项卡管理申请与使用 AI 推荐"
          style="margin-top: 8px"
        />
        <el-alert
          v-else-if="!store.isLogin"
          type="info"
          :closable="false"
          title="登录后可申请加入该队伍"
          style="margin-top: 8px"
        />
      </el-card>

      <el-card v-if="isInTeam" shadow="never" class="block">
        <el-tabs v-model="captainTab" @tab-change="onTabChange">
          <el-tab-pane label="备赛计划" name="task">
            <TeamPreparation
              :key="detail.team.id"
              :team-id="detail.team.id"
              :is-captain="isCaptain"
              :members="detail.members"
              :resources="resources"
            />
          </el-tab-pane>
          <el-tab-pane v-if="isCaptain" label="申请管理" name="apply">
            <el-table :data="detail.applications" size="small">
              <el-table-column prop="userName" label="申请人" width="100" />
              <el-table-column prop="intro" label="自我介绍" />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag
                    :type="APPLY_STATUS_TYPE[row.status] || 'info'"
                    size="small"
                    >{{ row.status }}</el-tag
                  >
                </template>
              </el-table-column>
              <el-table-column label="操作" width="160">
                <template #default="{ row }">
                  <template v-if="row.status === '待审批'">
                    <el-button
                      type="success"
                      size="small"
                      @click="handleApply(row, 'approve')"
                      >通过</el-button
                    >
                    <el-button
                      type="danger"
                      size="small"
                      @click="handleApply(row, 'reject')"
                      >拒绝</el-button
                    >
                  </template>
                  <span v-else style="color: #909399; font-size: 12px"
                    >已处理</span
                  >
                </template>
              </el-table-column>
            </el-table>
            <el-empty
              v-if="!detail.applications.length"
              description="暂无申请"
              :image-size="60"
            />
          </el-tab-pane>

          <el-tab-pane v-if="isCaptain" label="AI 推荐队友" name="ai">
            <el-alert
              type="success"
              :closable="false"
              show-icon
              title="AI 按「技能契合度40% + 竞赛方向30% + 参赛状态20% + 经验10%」加权匹配，只推荐愿意被邀请的用户"
              style="margin-bottom: 16px"
            />
            <el-row :gutter="16" v-loading="aiLoading">
              <el-col
                :xs="24"
                :md="12"
                v-for="u in recommendList"
                :key="u.userId"
                style="margin-bottom: 16px"
              >
                <el-card shadow="hover">
                  <div class="rec-head">
                    <span class="rec-name">{{ u.nickname }}</span>
                    <el-tag
                      :type="COMPETE_STATUS[u.competeStatus]?.type || 'info'"
                      size="small"
                    >
                      {{
                        COMPETE_STATUS[u.competeStatus]?.label ||
                        u.competeStatus
                      }}
                    </el-tag>
                  </div>
                  <div class="rec-meta">{{ u.college }} · {{ u.major }}</div>
                  <div class="rec-meta">
                    <el-tag
                      v-for="s in u.skills"
                      :key="s"
                      size="small"
                      effect="plain"
                      style="margin-right: 6px"
                      >{{ s }}</el-tag
                    >
                  </div>
                  <div class="rec-score">
                    <span class="score-label">匹配度</span>
                    <el-progress
                      :percentage="u.matchScore"
                      :stroke-width="14"
                      :color="scoreColor(u.matchScore)"
                    />
                  </div>
                  <div class="rec-reason">
                    <el-tag
                      v-if="u.reasonSource === 'LLM'"
                      type="danger"
                      size="small"
                      effect="dark"
                      style="margin-right: 6px"
                      >AI 推荐</el-tag
                    >
                    <el-tag
                      v-else
                      type="info"
                      size="small"
                      effect="plain"
                      style="margin-right: 6px"
                      >匹配分析</el-tag
                    >
                    {{ u.reason }}
                  </div>
                  <el-button type="primary" size="small" @click="invite(u)"
                    >一键邀请</el-button
                  >
                </el-card>
              </el-col>
            </el-row>
            <el-empty
              v-if="!aiLoading && !recommendList.length"
              description="暂无可推荐用户（候选池为空）"
            />
          </el-tab-pane>

          <el-tab-pane label="训练资料" name="resource">
            <div v-loading="resLoading">
              <el-upload
                :show-file-list="false"
                :http-request="uploadResource"
                :before-upload="beforeUpload"
                style="margin-bottom: 12px"
              >
                <el-button type="primary" size="small"
                  >＋ 上传资料（文档/图片/压缩包，≤20MB）</el-button
                >
              </el-upload>
              <el-table :data="resources" size="small">
                <el-table-column label="文件名" min-width="200">
                  <template #default="{ row }">
                    <el-link type="primary" :href="row.url" target="_blank">{{
                      row.name
                    }}</el-link>
                  </template>
                </el-table-column>
                <el-table-column prop="type" label="类型" width="80" />
                <el-table-column label="上传人" width="90">
                  <template #default="{ row }">
                    <span
                      class="name-link"
                      @contextmenu.prevent="
                        openUserMenu($event, {
                          userId: row.uploaderId,
                          userName: row.uploaderName
                        })
                      "
                      >{{ row.uploaderName }}</span
                    >
                  </template>
                </el-table-column>
                <el-table-column prop="createdAt" label="时间" width="170" />
                <el-table-column label="操作" width="80">
                  <template #default="{ row }">
                    <el-button
                      v-if="row.uploaderId === store.user.id || isCaptain"
                      type="danger"
                      size="small"
                      link
                      @click="deleteResource(row)"
                      >删除</el-button
                    >
                  </template>
                </el-table-column>
              </el-table>
              <el-empty
                v-if="!resources.length"
                description="暂无资料"
                :image-size="60"
              />
            </div>
          </el-tab-pane>

          <el-tab-pane label="团队讨论" name="discussion">
            <div v-loading="discLoading">
              <div class="disc-list" ref="discListRef">
                <div v-for="m in discussions" :key="m.id" class="disc-item">
                  <b
                    class="disc-name"
                    @contextmenu.prevent="
                      openUserMenu($event, {
                        userId: m.userId,
                        userName: m.userName
                      })
                    "
                    >{{ m.userName }}</b
                  >
                  <span class="disc-content">{{ m.content }}</span>
                  <span class="disc-time">{{ m.createTime }}</span>
                  <el-button
                    v-if="m.userId === store.user.id || isCaptain"
                    type="danger"
                    size="small"
                    link
                    @click="deleteDiscussion(m)"
                    >删除</el-button
                  >
                </div>
              </div>
              <div class="disc-input">
                <el-input
                  v-model="discText"
                  placeholder="和队友讨论一下分工/进度/问题吧"
                  @keyup.enter="sendDiscussion"
                />
                <el-button
                  type="primary"
                  :disabled="!discText.trim()"
                  @click="sendDiscussion"
                  >发送</el-button
                >
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </template>

    <el-dialog v-model="applyVisible" title="申请加入队伍" width="420px">
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

    <el-dialog v-model="renameVisible" title="修改队伍名" width="420px">
      <el-input
        v-model="renameTitle"
        maxlength="50"
        show-word-limit
        placeholder="输入新的队伍名"
      />
      <template #footer>
        <el-button @click="renameVisible = false">取消</el-button>
        <el-button type="primary" :loading="renaming" @click="doRename"
          >保存</el-button
        >
      </template>
    </el-dialog>

    <el-dialog v-model="descVisible" title="编辑队伍简介" width="460px">
      <el-input
        v-model="descText"
        type="textarea"
        :rows="5"
        maxlength="1000"
        show-word-limit
        placeholder="描述队伍定位、目标与分工"
      />
      <template #footer>
        <el-button @click="descVisible = false">取消</el-button>
        <el-button type="primary" :loading="descSaving" @click="doSaveDesc"
          >保存</el-button
        >
      </template>
    </el-dialog>

    <el-dialog
      v-model="kickVisible"
      title="移出成员（请填写理由）"
      width="420px"
    >
      <p style="margin-top: 0">
        将「{{ kickMember?.userName }}」移出队伍，理由会通过站内消息通知对方：
      </p>
      <el-input
        v-model="kickReason"
        type="textarea"
        :rows="3"
        placeholder="例如：训练态度不积极，长期不参加训练"
      />
      <template #footer>
        <el-button @click="kickVisible = false">取消</el-button>
        <el-button type="danger" :loading="kicking" @click="doKick"
          >确认移出</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import TeamPreparation from '../components/TeamPreparation.vue'
import { useUserStore } from '../stores/user'
import {
  COMPETE_STATUS,
  TEAM_STATUS_TYPE,
  APPLY_STATUS_TYPE
} from '../utils/constants'
import {
  connectWebSocket,
  sendWsMessage,
  onWsMessage
} from '../utils/websocket'
import { openUserMenu } from '../utils/userMenu'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const loading = ref(false)
const loadFailed = ref(false)
const detail = ref(null)
const captainTab = ref('task')
const aiLoading = ref(false)
const recommendList = ref([])
const applyVisible = ref(false)
const applying = ref(false)
const applyIntro = ref('')
const kickVisible = ref(false)
const kicking = ref(false)
const kickMember = ref(null)
const kickReason = ref('')
// 修改队名
const renameVisible = ref(false)
const renaming = ref(false)
const renameTitle = ref('')
// 编辑简介
const descVisible = ref(false)
const descSaving = ref(false)
const descText = ref('')

// 训练资料
const resources = ref([])
const resLoading = ref(false)
// 团队讨论区
const discussions = ref([])
const discText = ref('')
const discLoading = ref(false)
const discListRef = ref(null)
let unsubDisc = null

const isCaptain = computed(
  () =>
    !!detail.value &&
    !!store.user &&
    detail.value.team.captainId === store.user.id
)
const isInTeam = computed(
  () =>
    !!detail.value &&
    !!store.user &&
    detail.value.members.some((m) => m.userId === store.user.id)
)
const canApply = computed(
  () =>
    !!detail.value &&
    store.isLogin &&
    !isCaptain.value &&
    !detail.value.members.some((m) => m.userId === store.user.id) &&
    detail.value.team.status !== '已满员'
)

function scoreColor(score) {
  if (score >= 80) return '#67c23a'
  if (score >= 60) return '#409eff'
  return '#e6a23c'
}

async function load() {
  loadFailed.value = false
  loading.value = true
  try {
    detail.value = await api.get(`/teams/${route.params.id}`)
    if (isInTeam.value) {
      loadResources()
      loadDiscussions()
    }
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

// ===== 团队讨论区 =====
async function loadDiscussions() {
  discLoading.value = true
  try {
    const data = await api.get(`/teams/${detail.value.team.id}/discussions`, {
      params: { page: 1, size: 50 }
    })
    discussions.value = data.records || []
  } catch (e) {
    /* 非成员无权查看，忽略 */
  } finally {
    discLoading.value = false
  }
  nextTick(() => {
    const el = discListRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function handleDiscWs(msg) {
  if (
    msg.type === 'TEAM' &&
    msg.data &&
    msg.data.teamId === detail.value?.team.id
  ) {
    const exist = discussions.value.some((m) => m.id === msg.data.id)
    if (!exist) {
      discussions.value.push(msg.data)
      nextTick(() => {
        const el = discListRef.value
        if (el) el.scrollTop = el.scrollHeight
      })
    }
  }
}

async function sendDiscussion() {
  const content = discText.value.trim()
  if (!content) return
  // 优先 WebSocket 实时推送
  if (sendWsMessage({ type: 'TEAM', teamId: detail.value.team.id, content })) {
    discText.value = ''
    return
  }
  // WS 未连接时降级为 REST
  await api.post(`/teams/${detail.value.team.id}/discussions`, { content })
  discText.value = ''
  loadDiscussions()
}

async function deleteDiscussion(m) {
  await ElMessageBox.confirm('确定删除这条讨论消息吗？', '删除确认', {
    type: 'warning'
  })
  await api.delete(`/discussions/${m.id}`)
  ElMessage.success('已删除')
  loadDiscussions()
}

// ===== 训练资料 =====
async function loadResources() {
  try {
    resources.value = await api.get(`/teams/${detail.value.team.id}/resources`)
  } catch (e) {
    /* 非成员无权查看，忽略 */
  }
}

function beforeUpload(file) {
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.warning('文件不能超过 20MB')
    return false
  }
  return true
}

async function uploadResource({ file }) {
  resLoading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    const data = await api.post('/files/upload', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    await api.post(`/teams/${detail.value.team.id}/resources`, {
      title: file.name,
      url: data.url
    })
    ElMessage.success('上传成功')
    loadResources()
  } finally {
    resLoading.value = false
  }
}

async function deleteResource(row) {
  await ElMessageBox.confirm(`确定删除资料「${row.name}」吗？`, '删除确认', {
    type: 'warning'
  })
  await api.delete(`/resources/${row.id}`)
  ElMessage.success('已删除')
  loadResources()
}

async function handleApply(row, action) {
  await api.post(
    `/teams/${detail.value.team.id}/applications/${row.id}/${action}`
  )
  ElMessage.success(
    action === 'approve' ? '已通过，申请人已入队' : '已拒绝该申请'
  )
  load()
}

async function loadRecommend() {
  if (!isCaptain.value) return
  aiLoading.value = true
  try {
    recommendList.value = await api.get('/ai/recommend', {
      params: { teamId: detail.value.team.id }
    })
  } finally {
    aiLoading.value = false
  }
}

/** 切换选项卡时触发：进入 AI 推荐页才加载（避免每次进详情页都等 LLM 生成理由） */
function onTabChange(name) {
  if (name === 'ai') {
    loadRecommend()
  }
}

async function invite(u) {
  await ElMessageBox.confirm(
    `确定邀请「${u.nickname}」（匹配度 ${u.matchScore}%）加入队伍吗？`,
    '发送邀请',
    { type: 'info' }
  )
  await api.post(`/teams/${detail.value.team.id}/invite`, { userId: u.userId })
  ElMessage.success(`已向 ${u.nickname} 发送邀请`)
}

async function doApply() {
  if (applying.value) return
  applying.value = true
  try {
    await api.post(`/teams/${detail.value.team.id}/apply`, {
      intro: applyIntro.value
    })
    ElMessage.success('申请已提交，等待队长审批')
    applyVisible.value = false
    load()
  } finally {
    applying.value = false
  }
}

function openKick(row) {
  kickMember.value = row
  kickReason.value = ''
  kickVisible.value = true
}

async function doKick() {
  if (kicking.value) return
  if (!kickReason.value.trim()) {
    ElMessage.warning('请填写踢出理由')
    return
  }
  kicking.value = true
  try {
    await api.post(
      `/teams/${detail.value.team.id}/members/${kickMember.value.userId}/kick`,
      {
        reason: kickReason.value
      }
    )
    ElMessage.success('已移出该成员')
    kickVisible.value = false
    load()
  } finally {
    kicking.value = false
  }
}

async function disband() {
  await ElMessageBox.confirm(
    '确定解散该队伍吗？全体成员将收到通知，此操作不可撤销！',
    '解散队伍',
    {
      type: 'warning',
      confirmButtonText: '确认解散',
      cancelButtonText: '再想想'
    }
  )
  await api.post(`/teams/${detail.value.team.id}/disband`)
  ElMessage.success('队伍已解散')
  router.push('/teams')
}

async function leaveTeam() {
  await ElMessageBox.confirm(
    `确定退出队伍「${detail.value.team.title}」吗？队长将收到通知。`,
    '退出队伍',
    {
      type: 'warning',
      confirmButtonText: '确认退出',
      cancelButtonText: '再想想'
    }
  )
  await api.post(`/teams/${detail.value.team.id}/leave`)
  ElMessage.success('已退出队伍')
  router.push('/teams')
}

function openRename() {
  renameTitle.value = detail.value.team.title || ''
  renameVisible.value = true
}

async function doRename() {
  if (renaming.value) return
  const title = renameTitle.value.trim()
  if (!title) {
    ElMessage.warning('队伍名不能为空')
    return
  }
  renaming.value = true
  try {
    await api.put(`/teams/${detail.value.team.id}/title`, { title })
    ElMessage.success('队伍名已更新')
    renameVisible.value = false
    load()
  } finally {
    renaming.value = false
  }
}

function openDescEdit() {
  descText.value = detail.value.team.description || ''
  descVisible.value = true
}

async function doSaveDesc() {
  if (descSaving.value) return
  descSaving.value = true
  try {
    await api.put(`/teams/${detail.value.team.id}/description`, {
      description: descText.value
    })
    ElMessage.success('队伍简介已更新')
    descVisible.value = false
    load()
  } finally {
    descSaving.value = false
  }
}

onMounted(() => {
  connectWebSocket()
  unsubDisc = onWsMessage(handleDiscWs)
  load()
})

onBeforeUnmount(() => {
  if (unsubDisc) unsubDisc()
})
</script>

<style scoped>
.block {
  margin-bottom: 16px;
}
.disc-list {
  height: 320px;
  overflow-y: auto;
  background: #f5f7fa;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 10px;
}
.disc-item {
  font-size: 13px;
  padding: 5px 0;
  line-height: 1.6;
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.disc-name {
  color: #409eff;
  white-space: nowrap;
  cursor: pointer;
}
.name-link {
  cursor: pointer;
  color: #409eff;
}
.name-link:hover {
  text-decoration: underline;
}
.disc-content {
  color: #303133;
}
.disc-time {
  color: #c0c4cc;
  font-size: 11px;
  margin-left: auto;
  white-space: nowrap;
}
.disc-input {
  display: flex;
  gap: 10px;
}
.team-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.team-head-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.team-title {
  margin: 0;
}
.team-desc {
  color: #606266;
  font-size: 14px;
  margin-top: 10px;
}
.team-skills {
  margin-top: 12px;
}
.rec-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.rec-name {
  font-size: 15px;
  font-weight: 600;
}
.rec-meta {
  color: #606266;
  font-size: 13px;
  margin-top: 6px;
}
.rec-score {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.score-label {
  font-size: 13px;
  color: #303133;
  white-space: nowrap;
}
.rec-reason {
  background: #f0f9eb;
  border-radius: 4px;
  padding: 8px 10px;
  font-size: 13px;
  color: #529b2e;
  margin: 10px 0;
  line-height: 1.5;
}
.team-head {
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 16px;
}
.team-head-right {
  flex-wrap: wrap;
}
.team-title {
  font-size: 21px;
  line-height: 1.6;
}
.disc-item {
  flex-wrap: wrap;
}
.disc-content {
  overflow-wrap: anywhere;
  min-width: 0;
}
.rec-score :deep(.el-progress) {
  flex: 1;
}
</style>
