<template>
  <div v-loading="loading">
    <el-card shadow="never" class="block">
      <template #header>
        <div class="card-head">
          <span>我的资料</span>
          <el-button size="small" @click="editVisible = true">编辑资料</el-button>
        </div>
      </template>
      <div class="avatar-row">
        <el-avatar :size="72" :src="store.user?.avatar || undefined">{{ (store.user?.nickname || '?').charAt(0) }}</el-avatar>
        <div class="avatar-actions">
          <el-upload :show-file-list="false" :http-request="uploadAvatar" :before-upload="beforeAvatar">
            <el-button size="small" :loading="avatarUploading">上传头像</el-button>
          </el-upload>
          <div class="avatar-tip">支持图片，≤20MB</div>
        </div>
      </div>
      <el-descriptions :column="2" size="small">
        <el-descriptions-item label="昵称">{{ store.user?.nickname }}</el-descriptions-item>
        <el-descriptions-item label="用户名">{{ store.user?.username }}</el-descriptions-item>
        <el-descriptions-item label="学院">{{ store.user?.college || '—' }}</el-descriptions-item>
        <el-descriptions-item label="专业">{{ store.user?.major || '—' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ store.user?.email || '—' }}</el-descriptions-item>
        <el-descriptions-item label="简介">{{ store.user?.intro || '—' }}</el-descriptions-item>
      </el-descriptions>
      <div class="skills-line">
        <span class="label">技能标签：</span>
        <el-tag v-for="s in store.user?.skills || []" :key="s.id" size="small" effect="plain" style="margin-right: 6px">{{ s.name }}</el-tag>
        <span v-if="!(store.user?.skills || []).length" style="color: #909399; font-size: 13px">暂无</span>
        <el-button size="small" link type="primary" style="margin-left: 6px" @click="openSkillsEdit">编辑标签</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="block">
      <template #header>🤖 我的参赛状态（AI 推荐候选池开关）</template>
      <el-alert type="info" :closable="false" show-icon
        title="只有「希望被邀请 / 可接受邀请」时，你才会被 AI 推荐给其他队长；切换后立即生效" style="margin-bottom: 16px" />
      <div class="status-grid">
        <div
          v-for="(item, key) in COMPETE_STATUS"
          :key="key"
          class="status-card"
          :class="{ active: store.user?.competeStatus === key }"
          @click="changeStatus(key)"
        >
          <div class="status-label">{{ item.label }}</div>
          <div class="status-desc">{{ statusDesc[key] }}</div>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="block">
      <template #header>我的队伍</template>
      <el-table :data="myTeams" size="small" @row-click="(row) => $router.push(`/teams/${row.id}`)">
        <el-table-column prop="title" label="队伍" />
        <el-table-column prop="competitionName" label="目标竞赛" width="180" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="TEAM_STATUS_TYPE[row.status] || 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="成员" width="90">
          <template #default="{ row }">{{ row.memberCount }} / {{ row.maxMembers }}</template>
        </el-table-column>
        <el-table-column label="角色" width="80">
          <template #default="{ row }">
            <el-tag :type="row.captainId === store.user?.id ? 'danger' : 'info'" size="small">
              {{ row.captainId === store.user?.id ? '队长' : '队员' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70">
          <template #default="{ row }">
            <el-button
              v-if="row.captainId !== store.user?.id"
              type="warning"
              size="small"
              link
              @click.stop="leaveTeam(row)"
            >退出</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!myTeams.length" description="还没有加入任何队伍" :image-size="60" />
    </el-card>

    <el-card shadow="never" class="block">
      <template #header>我的申请</template>
      <el-table :data="myApplications" size="small">
        <el-table-column prop="userName" label="申请于" width="100">
          <template #default="{ row }">{{ row.applyTime }}</template>
        </el-table-column>
        <el-table-column prop="intro" label="申请说明" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="APPLY_STATUS_TYPE[row.status] || 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <template v-if="row.status === '待审批' && row.invited">
              <el-button type="success" size="small" @click="handleInvite(row, 'accept')">接受</el-button>
              <el-button type="danger" size="small" plain @click="handleInvite(row, 'reject')">拒绝</el-button>
            </template>
            <span v-else style="color: #909399; font-size: 12px">—</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!myApplications.length" description="还没有申请记录" :image-size="60" />
    </el-card>

    <el-dialog v-model="skillsVisible" title="选择技能标签" width="460px">
      <p style="margin-top: 0; color: #909399; font-size: 13px">
        从常用标签中选择，也可以直接输入自定义标签（AI 推荐队友会按标签匹配）
      </p>
      <el-select
        v-model="skillForm"
        multiple
        filterable
        allow-create
        default-first-option
        :reserve-keyword="false"
        placeholder="输入或选择技能标签"
        style="width: 100%"
      >
        <el-option v-for="s in SKILLS" :key="s" :label="s" :value="s" />
      </el-select>
      <template #footer>
        <el-button @click="skillsVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingSkills" @click="saveSkills">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑资料" width="460px">
      <el-form :model="editForm" label-width="70px">
        <el-form-item label="昵称"><el-input v-model="editForm.nickname" /></el-form-item>
        <el-form-item label="学院"><el-input v-model="editForm.college" /></el-form-item>
        <el-form-item label="专业"><el-input v-model="editForm.major" /></el-form-item>
        <el-form-item label="简介">
          <el-input v-model="editForm.intro" type="textarea" :rows="3" placeholder="写上你的擅长方向，更容易被 AI 推荐" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveProfile">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { useUserStore } from '../stores/user'
import { COMPETE_STATUS, TEAM_STATUS_TYPE, APPLY_STATUS_TYPE, SKILLS } from '../utils/constants'

const store = useUserStore()
const loading = ref(false)
const myTeams = ref([])
const myApplications = ref([])
const editVisible = ref(false)
const saving = ref(false)
const avatarUploading = ref(false)
const editForm = reactive({ nickname: '', college: '', major: '', intro: '' })

// 技能标签编辑
const skillsVisible = ref(false)
const savingSkills = ref(false)
const skillForm = ref([])

function openSkillsEdit() {
  skillForm.value = (store.user?.skills || []).map((s) => s.name)
  skillsVisible.value = true
}

async function saveSkills() {
  savingSkills.value = true
  try {
    const user = await api.put('/user/skills', skillForm.value)
    store.setUser(user)
    ElMessage.success('技能标签已更新')
    skillsVisible.value = false
  } finally {
    savingSkills.value = false
  }
}

const statusDesc = {
  WANT_INVITE: '正在备战，欢迎队长邀请我入队',
  ACCEPT_INVITE: '有空位就拉我，我随叫随到',
  NOT_PARTICIPATE: '暂时不参赛，别推荐我',
  ONLY_VIEW: '只看看，不想被打扰'
}

function beforeAvatar(file) {
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请上传图片文件')
    return false
  }
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.warning('文件不能超过 20MB')
    return false
  }
  return true
}

async function uploadAvatar({ file }) {
  avatarUploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    const data = await api.post('/files/upload', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    const user = await api.put('/user/profile', { avatar: data.url })
    store.setUser(user)
    ElMessage.success('头像已更新')
  } finally {
    avatarUploading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const [teams, applications] = await Promise.all([
      api.get('/user/me/teams'),
      api.get('/user/me/applications')
    ])
    myTeams.value = teams
    myApplications.value = applications
  } finally {
    loading.value = false
  }
}

/** 处理收到的入队邀请（接受/拒绝），并同步把相关邀请通知标记已读（让铃铛熄灭） */
async function handleInvite(row, action) {
  await api.post(`/teams/invites/${row.id}/${action}`)
  ElMessage.success(action === 'accept' ? '已接受邀请，恭喜入队！' : '已拒绝该邀请')
  try {
    const notifs = await api.get('/notifications')
    const unreadInvites = (notifs || []).filter((n) => n.type === 'INVITE' && !n.isRead)
    await Promise.all(unreadInvites.map((n) => api.post(`/notifications/${n.id}/read`)))
  } catch (e) {
    /* 已读同步失败不影响主流程 */
  }
  load()
}

/** 退出队伍（队员） */
async function leaveTeam(row) {
  await ElMessageBox.confirm(`确定退出队伍「${row.title}」吗？队长将收到通知。`, '退出队伍', {
    type: 'warning',
    confirmButtonText: '确认退出',
    cancelButtonText: '再想想'
  })
  await api.post(`/teams/${row.id}/leave`)
  ElMessage.success('已退出队伍')
  load()
}

async function changeStatus(key) {
  if (store.user?.competeStatus === key) return
  const user = await api.put('/user/compete-status', { competeStatus: key })
  store.setUser(user)
  ElMessage.success(`参赛状态已切换为「${COMPETE_STATUS[key].label}」`)
}

function openEdit() {
  editForm.nickname = store.user?.nickname || ''
  editForm.college = store.user?.college || ''
  editForm.major = store.user?.major || ''
  editForm.intro = store.user?.intro || ''
  editVisible.value = true
}

async function saveProfile() {
  saving.value = true
  try {
    const user = await api.put('/user/profile', editForm)
    store.setUser(user)
    ElMessage.success('资料已更新')
    editVisible.value = false
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.block {
  margin-bottom: 16px;
}
.avatar-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
.avatar-actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.avatar-tip {
  font-size: 12px;
  color: #909399;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.skills-line {
  margin-top: 14px;
  font-size: 14px;
}
.label {
  color: #606266;
}
.status-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.status-card {
  border: 2px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
}
.status-card:hover {
  border-color: #409eff;
}
.status-card.active {
  border-color: #409eff;
  background: #ecf5ff;
}
.status-label {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.status-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
  line-height: 1.5;
}
</style>
