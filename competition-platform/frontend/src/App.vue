<template>
  <el-container class="app-shell">
    <el-header class="navbar">
      <div class="nav-left">
        <span class="logo">🏆 竞赛组队平台</span>
        <router-link to="/" class="nav-link">首页</router-link>
        <router-link to="/teams" class="nav-link">组队广场</router-link>
        <router-link to="/stats" class="nav-link">数据统计</router-link>
        <router-link to="/achievements" class="nav-link">成果墙</router-link>
        <router-link to="/chat" class="nav-link">
          私聊
          <el-badge v-if="store.isLogin && dmUnread > 0" :value="dmUnread" :max="99" class="dm-badge" />
        </router-link>
        <router-link to="/feedback" class="nav-link">反馈</router-link>
        <router-link to="/admin" class="nav-link" v-if="store.isAdmin">
          后台管理
          <el-badge v-if="adminPending > 0" :value="adminPending" :max="99" class="dm-badge" />
        </router-link>
      </div>
      <div class="nav-right">
        <el-popover placement="bottom" :width="340" trigger="click" @show="loadNotifications">
          <template #reference>
            <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="bell">
              <el-button :icon="Bell" circle />
            </el-badge>
          </template>
          <div class="notice-head">
            <span>消息通知</span>
            <el-button type="primary" link size="small" @click="clearAllNotifications">全部已读</el-button>
          </div>
          <div class="notice-list">
            <div v-if="notifications.length === 0" class="notice-empty">暂无消息</div>
            <div v-for="n in notifications" :key="n.id" class="notice-item" :class="{ unread: !n.isRead }">
              <div class="notice-content" @click="markRead(n)">{{ n.content }}</div>
              <div class="notice-time" @click="markRead(n)">{{ n.createTime }}</div>
              <div v-if="n.type === 'INVITE' && !n.isRead" class="notice-actions">
                <el-button type="success" size="small" @click.stop="handleInvite(n, 'accept')">接受邀请</el-button>
                <el-button type="danger" size="small" plain @click.stop="handleInvite(n, 'reject')">拒绝</el-button>
              </div>
            </div>
          </div>
        </el-popover>
        <template v-if="store.isLogin">
          <el-dropdown @command="onCommand">
            <span class="user-name">
              <el-avatar :size="26" :src="store.user?.avatar || undefined">{{ (store.user?.nickname || '?').charAt(0) }}</el-avatar>
              {{ store.user?.nickname || store.user?.username }}
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="me">个人中心</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <el-button v-else type="primary" size="small" @click="$router.push('/login')">登录 / 注册</el-button>
      </div>
    </el-header>
    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
  <UserContextMenu />
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell } from '@element-plus/icons-vue'
import api from './api'
import { useUserStore } from './stores/user'
import { connectWebSocket, onWsMessage } from './utils/websocket'
import UserContextMenu from './components/UserContextMenu.vue'

const router = useRouter()
const store = useUserStore()
let dmTimer = null
let unsubWs = null

const notifications = ref([])
const unreadCount = computed(() => notifications.value.filter((n) => !n.isRead).length)
const dmUnread = ref(0)
const adminPending = ref(0)

/** 管理员待处理数量：未处理反馈 + 待审核成果 */
async function loadAdminPending() {
  if (!store.isAdmin) return
  try {
    const [fb, ach] = await Promise.all([
      api.get('/feedback/pending-count'),
      api.get('/achievements/pending-count')
    ])
    adminPending.value = (fb || 0) + (ach || 0)
  } catch (e) {
    /* 忽略轮询错误 */
  }
}

async function loadNotifications() {
  if (!store.isLogin) return
  notifications.value = await api.get('/notifications')
}

async function loadDmUnread() {
  if (!store.isLogin) return
  try {
    const convs = await api.get('/chat/dm/conversations')
    dmUnread.value = convs.reduce((sum, c) => sum + (c.unread || 0), 0)
  } catch (e) {
    /* 忽略轮询错误 */
  }
}

async function markRead(n) {
  if (n.isRead) return
  await api.post(`/notifications/${n.id}/read`)
  n.isRead = true
}

/** 全部标记为已读（清空铃铛角标） */
async function clearAllNotifications() {
  try {
    await api.post('/notifications/clear')
    notifications.value.forEach((n) => (n.isRead = true))
    ElMessage.success('已全部标记为已读')
  } catch (e) {
    /* 后端已提示 */
  }
}

/** 在铃铛里直接接受/拒绝入队邀请 */
async function handleInvite(n, action) {
  try {
    const invites = await api.get('/teams/invites/me')
    const invite = invites && invites.length ? invites[0] : null
    if (!invite) {
      ElMessage.warning('没有待处理的邀请')
      return
    }
    await api.post(`/teams/invites/${invite.id}/${action}`)
    ElMessage.success(action === 'accept' ? '已接受邀请，恭喜入队！' : '已拒绝该邀请')
    // 立即置灰并隐藏操作按钮，同时同步服务端已读状态
    n.isRead = true
    try {
      await api.post(`/notifications/${n.id}/read`)
    } catch (e) {
      /* 已读失败不影响主流程 */
    }
    loadNotifications()
  } catch (e) {
    /* 后端已弹出错误提示 */
  }
}

function onCommand(cmd) {
  if (cmd === 'me') {
    router.push('/me')
  } else if (cmd === 'logout') {
    store.logout()
    ElMessage.success('已退出登录')
    router.push('/')
  }
}

onMounted(() => {
  if (store.isLogin) {
    connectWebSocket()
    loadNotifications()
    loadDmUnread()
    loadAdminPending()
    dmTimer = setInterval(() => {
      loadNotifications()
      loadDmUnread()
      loadAdminPending()
    }, 5000)
    // WebSocket 实时推送：私聊消息/业务通知到达时立即刷新未读数
    unsubWs = onWsMessage((msg) => {
      if (msg.type === 'DM' && msg.data && msg.data.receiverId === store.user?.id) {
        loadDmUnread()
        loadNotifications()
      }
      if (msg.type === 'NOTIFY' && msg.data) {
        loadNotifications()
        if (msg.data.type === 'INVITE') {
          ElMessage.info('收到新的组队邀请，点击铃铛查看并处理')
        }
      }
    })
  }
})

onBeforeUnmount(() => {
  if (dmTimer) clearInterval(dmTimer)
  if (unsubWs) unsubWs()
})
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: #f5f7fa;
}
.navbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}
.nav-left {
  display: flex;
  align-items: center;
  gap: 20px;
}
.logo {
  font-size: 18px;
  font-weight: 700;
  color: #409eff;
  margin-right: 8px;
}
.nav-link {
  color: #606266;
  text-decoration: none;
  font-size: 15px;
}
.nav-link.router-link-active {
  color: #409eff;
  font-weight: 600;
}
.dm-badge {
  margin-left: 2px;
}
.nav-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.bell {
  cursor: pointer;
}
.user-name {
  cursor: pointer;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 6px;
  outline: none;
}
.main {
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
  padding-top: 24px;
}
.notice-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 6px;
  border-bottom: 1px solid #f0f2f5;
  font-size: 13px;
  color: #303133;
}
.notice-list {
  max-height: 320px;
  overflow-y: auto;
}
.notice-empty {
  color: #909399;
  text-align: center;
  padding: 20px 0;
  font-size: 13px;
}
.notice-item {
  padding: 10px 6px;
  border-bottom: 1px solid #f0f2f5;
  cursor: pointer;
}
.notice-item.unread {
  background: #ecf5ff;
}
.notice-content {
  font-size: 13px;
  color: #303133;
  line-height: 1.5;
}
.notice-time {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.notice-actions {
  margin-top: 8px;
  display: flex;
  gap: 8px;
}
</style>
