<template>
  <teleport to="body">
    <!-- 全局右键菜单：查看主页 / 私聊 / 关注 / 邀请组队 -->
    <div
      v-if="state.visible"
      class="uctx-overlay"
      @click="close"
      @contextmenu.prevent="close"
    ></div>
    <div
      v-if="state.visible"
      class="uctx-menu"
      :style="{ left: state.x + 'px', top: state.y + 'px' }"
    >
      <div class="uctx-title">
        <div class="uctx-nick">{{ state.userName || '用户' }}</div>
        <div class="uctx-user">用户名：{{ state.username || '—' }}</div>
      </div>
      <div class="uctx-item" @click="goHome">
        <el-icon class="text-icon" aria-hidden="true"><Document /></el-icon>
        查看主页
      </div>
      <div class="uctx-item" @click="dm">
        <el-icon class="text-icon" aria-hidden="true"><ChatDotRound /></el-icon>
        私聊
      </div>
      <div class="uctx-item" @click="follow">
        <el-icon class="text-icon" aria-hidden="true"><Plus /></el-icon> 关注
      </div>
      <div class="uctx-divider"></div>
      <div class="uctx-item" @click="openInviteDialog">
        <el-icon class="text-icon" aria-hidden="true"><Aim /></el-icon> 邀请组队
      </div>
    </div>
  </teleport>

  <!-- 邀请组队：选择自己担任队长的队伍 -->
  <el-dialog v-model="inviteVisible" title="邀请组队" width="440px">
    <p v-if="!store.isLogin" style="margin: 0">请先登录后再邀请他人组队</p>
    <template v-else>
      <p style="margin-top: 0">
        邀请 <b>{{ state.userName || '该用户' }}</b> 加入你的队伍：
      </p>
      <div v-if="!captainTeams.length" style="color: #909399; font-size: 13px">
        你还没有可以邀请的队伍（需要是该队队长且队伍未满员）。
      </div>
      <el-radio-group
        v-model="chosenTeamId"
        style="display: flex; flex-direction: column; gap: 8px"
      >
        <el-radio
          v-for="t in captainTeams"
          :key="t.id"
          :value="t.id"
          style="margin-right: 0"
        >
          {{ t.title }}（{{ t.memberCount }}/{{ t.maxMembers }}）
        </el-radio>
      </el-radio-group>
    </template>
    <template #footer>
      <el-button @click="inviteVisible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="inviting"
        :disabled="!chosenTeamId"
        @click="doInvite"
        >发送邀请</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'
import { useUserStore } from '../stores/user'
import { userMenuState as state, closeUserMenu } from '../utils/userMenu'

const router = useRouter()
const store = useUserStore()

const captainTeams = ref([])
const chosenTeamId = ref(null)
const inviteVisible = ref(false)
const inviting = ref(false)

// 打开菜单时拉取一次用户名展示
watch(
  () => [state.visible, state.userId],
  async ([visible, userId]) => {
    if (!visible || !userId) return
    try {
      const home = await api.get(`/users/${userId}`)
      state.username = home.user?.username || ''
    } catch (e) {
      /* 忽略 */
    }
  }
)

function close() {
  closeUserMenu()
}

function goHome() {
  close()
  router.push(`/users/${state.userId}`)
}

function dm() {
  close()
  if (!store.isLogin) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  router.push({ path: '/chat', query: { to: state.userId } })
}

async function follow() {
  close()
  if (!store.isLogin) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  await api.post(`/users/${state.userId}/follow`)
  ElMessage.success('关注成功，现在可以私聊对方了')
}

async function openInviteDialog() {
  if (!store.isLogin) {
    close()
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  chosenTeamId.value = null
  try {
    const teams = await api.get('/user/me/teams')
    captainTeams.value = (teams || []).filter(
      (t) =>
        t.captainId === store.user?.id &&
        t.memberCount < t.maxMembers &&
        t.status !== '已满员'
    )
    if (captainTeams.value.length === 1) {
      chosenTeamId.value = captainTeams.value[0].id
    }
    inviteVisible.value = true
  } catch (e) {
    ElMessage.error('获取我的队伍失败')
  }
}

async function doInvite() {
  if (inviting.value) return
  if (!chosenTeamId.value) {
    ElMessage.warning('请选择一个队伍')
    return
  }
  inviting.value = true
  try {
    await api.post(`/teams/${chosenTeamId.value}/invite`, {
      userId: state.userId
    })
    ElMessage.success(`已向 ${state.userName || '对方'} 发送入队邀请`)
    inviteVisible.value = false
  } catch (e) {
    /* 后端已弹错误提示 */
  } finally {
    inviting.value = false
  }
}
</script>

<style scoped>
.uctx-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
}
.uctx-menu {
  position: fixed;
  z-index: 2001;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  padding: 6px 0;
  min-width: 168px;
}
.uctx-title {
  padding: 8px 14px;
  border-bottom: 1px solid #f0f2f5;
}
.uctx-nick {
  font-weight: 700;
  font-size: 14px;
}
.uctx-user {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.uctx-item {
  padding: 9px 14px;
  font-size: 13px;
  cursor: pointer;
  color: #303133;
}
.uctx-item:hover {
  background: #ecf5ff;
  color: #409eff;
}
.uctx-divider {
  height: 1px;
  background: #f0f2f5;
  margin: 4px 0;
}
</style>
