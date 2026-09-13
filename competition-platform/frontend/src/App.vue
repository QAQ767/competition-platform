<template>
  <div class="app-shell" @keydown.esc="menuOpen = false">
    <a class="skip-link" href="#main-content">跳转到主要内容</a>
    <button
      v-if="menuOpen"
      class="sidebar-overlay"
      aria-label="关闭导航"
      @click="menuOpen = false"
    ></button>
    <aside id="main-navigation" class="sidebar" :class="{ open: menuOpen }">
      <router-link to="/" class="brand" @click="menuOpen = false">
        <span class="brand-mark"
          ><el-icon><Trophy /></el-icon
        ></span>
        <span><strong>竞赛同行</strong><small>CAMPUS TOGETHER</small></span>
      </router-link>
      <div class="nav-caption">探索与成长</div>
      <nav class="nav-list" aria-label="主要导航">
        <router-link
          v-for="item in mainNav"
          :key="item.path"
          :to="item.path"
          class="nav-link"
          :class="{ selected: isActive(item.path) }"
          @click="menuOpen = false"
        >
          <el-icon><component :is="item.icon" /></el-icon
          ><span>{{ item.label }}</span>
          <span v-if="item.path === '/teams'" class="nav-chip">组队</span>
        </router-link>
      </nav>
      <div class="nav-caption secondary-caption">我的空间</div>
      <nav class="nav-list" aria-label="个人导航">
        <router-link
          to="/chat"
          class="nav-link"
          :class="{ selected: isActive('/chat') }"
          @click="menuOpen = false"
          ><el-icon><ChatDotRound /></el-icon><span>私信消息</span
          ><el-badge
            v-if="store.isLogin && dmUnread > 0"
            :value="dmUnread"
            :max="99"
        /></router-link>
        <router-link
          to="/me"
          class="nav-link"
          :class="{ selected: isActive('/me') }"
          @click="menuOpen = false"
          ><el-icon><User /></el-icon><span>个人中心</span></router-link
        >
        <router-link
          to="/feedback"
          class="nav-link"
          :class="{ selected: isActive('/feedback') }"
          @click="menuOpen = false"
          ><el-icon><Message /></el-icon><span>意见反馈</span></router-link
        >
        <router-link
          v-if="store.isAdmin"
          to="/admin"
          class="nav-link"
          :class="{ selected: isActive('/admin') }"
          @click="menuOpen = false"
          ><el-icon><Setting /></el-icon><span>后台管理</span
          ><el-badge v-if="adminPending > 0" :value="adminPending" :max="99"
        /></router-link>
      </nav>
      <div class="sidebar-bottom">
        <div class="growth-card">
          <span class="growth-icon"
            ><el-icon><Aim /></el-icon></span
          ><strong>每一次练习，都算数</strong>
          <p>让今天的积累，成为赛场的底气。</p>
          <router-link to="/training" @click="menuOpen = false"
            >开启今日训练 <el-icon><ArrowRight /></el-icon
          ></router-link>
        </div>
        <span class="sidebar-footnote">高校竞赛组队与训练平台</span>
      </div>
    </aside>
    <div class="workspace">
      <header class="navbar">
        <div class="header-left">
          <el-button
            class="menu-toggle"
            :icon="Menu"
            circle
            aria-label="切换导航"
            aria-controls="main-navigation"
            :aria-expanded="menuOpen"
            @click="menuOpen = !menuOpen"
          /><span class="breadcrumb-root">工作台</span
          ><span class="breadcrumb-divider">/</span
          ><span class="breadcrumb-current">{{ currentTitle }}</span>
        </div>
        <div class="nav-right">
          <el-popover
            v-if="store.isLogin"
            placement="bottom-end"
            :width="320"
            trigger="click"
            @show="loadNotifications"
          >
            <template #reference>
              <el-badge
                :value="unreadCount"
                :hidden="unreadCount === 0"
                class="bell"
              >
                <el-button :icon="Bell" circle aria-label="消息通知" />
              </el-badge>
            </template>
            <div class="notice-head">
              <span>消息通知</span>
              <el-button
                type="primary"
                link
                size="small"
                @click="clearAllNotifications"
                >全部已读</el-button
              >
            </div>
            <div class="notice-list">
              <div v-if="notifications.length === 0" class="notice-empty">
                暂无消息
              </div>
              <div
                v-for="n in notifications"
                :key="n.id"
                class="notice-item"
                :class="{ unread: !n.isRead }"
              >
                <div class="notice-content" @click="markRead(n)">
                  {{ n.content }}
                </div>
                <div class="notice-time" @click="markRead(n)">
                  {{ n.createTime }}
                </div>
                <el-button
                  v-if="n.type === 'INVITE' && !n.inviteId"
                  link
                  type="primary"
                  @click="router.push('/me')"
                  >到个人中心查看邀请</el-button
                >
                <span
                  v-if="n.inviteId && n.inviteStatus !== '待审批'"
                  class="notice-time"
                  >{{ n.inviteStatus }}</span
                >
                <div
                  v-if="
                    n.type === 'INVITE' &&
                    n.inviteId &&
                    n.inviteStatus === '待审批'
                  "
                  class="notice-actions"
                >
                  <el-button
                    type="success"
                    size="small"
                    :loading="inviteBusy.has(n.inviteId)"
                    :disabled="inviteBusy.has(n.inviteId)"
                    @click.stop="handleInvite(n, 'accept')"
                    >接受邀请</el-button
                  >
                  <el-button
                    type="danger"
                    size="small"
                    plain
                    :disabled="inviteBusy.has(n.inviteId)"
                    @click.stop="handleInvite(n, 'reject')"
                    >拒绝</el-button
                  >
                </div>
              </div>
            </div>
          </el-popover>
          <template v-if="store.isLogin">
            <el-dropdown @command="onCommand">
              <span class="user-name">
                <el-avatar :size="32" :src="store.user?.avatar || undefined">{{
                  (store.user?.nickname || '?').charAt(0)
                }}</el-avatar>
                {{ store.user?.nickname || store.user?.username }}
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="me">个人中心</el-dropdown-item>
                  <el-dropdown-item divided command="logout"
                    >退出登录</el-dropdown-item
                  >
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <el-button
            v-else
            type="primary"
            size="small"
            @click="$router.push('/login')"
            >登录 / 注册</el-button
          >
        </div>
      </header>
      <main id="main-content" class="main" tabindex="-1">
        <router-view :key="route.path" />
      </main>
      <footer class="workspace-footer">
        <span>竞赛同行 · 让热爱相遇，让成长发生</span
        ><span>探索 / 协作 / 成长</span>
      </footer>
    </div>
  </div>
  <UserContextMenu />
</template>
<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Bell,
  Menu,
  Trophy,
  Connection,
  Reading,
  Medal,
  DataAnalysis
} from '@element-plus/icons-vue'
import api from './api'
import { useUserStore } from './stores/user'
import { connectWebSocket, onWsMessage } from './utils/websocket'
import UserContextMenu from './components/UserContextMenu.vue'

const router = useRouter()
const route = useRoute()
const menuOpen = ref(false)
const mainNav = [
  { path: '/', label: '发现竞赛', icon: Trophy },
  { path: '/teams', label: '组队广场', icon: Connection },
  { path: '/training', label: '训练中心', icon: Reading },
  { path: '/achievements', label: '荣誉成果', icon: Medal },
  { path: '/stats', label: '数据概览', icon: DataAnalysis }
]
const isActive = (path) =>
  path === '/'
    ? route.path === '/'
    : route.path === path || route.path.startsWith(path + '/')
const currentTitle = computed(
  () =>
    mainNav.find((item) => isActive(item.path))?.label ||
    {
      '/chat': '私信消息',
      '/me': '个人中心',
      '/feedback': '意见反馈',
      '/admin': '后台管理',
      '/login': '登录与注册'
    }[route.path] ||
    '用户主页'
)
const store = useUserStore()
let dmTimer = null
let unsubWs = null

const notifications = ref([])
const inviteBusy = ref(new Set())
const unreadCount = computed(
  () => notifications.value.filter((n) => !n.isRead).length
)
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
  try {
    const data = await api.get('/notifications')
    if (store.isLogin) notifications.value = data || []
  } catch {
    // 请求错误由接口层提示，等待下次刷新。
  }
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
  if (!n.inviteId || inviteBusy.value.has(n.inviteId)) return
  inviteBusy.value.add(n.inviteId)
  try {
    await api.post(`/teams/invites/${n.inviteId}/${action}`)
    n.inviteStatus = action === 'accept' ? '已通过' : '已拒绝'
    n.isRead = true
    ElMessage.success(
      action === 'accept' ? '已接受邀请，恭喜入队！' : '已拒绝该邀请'
    )
    await loadNotifications()
  } catch {
    await loadNotifications()
  } finally {
    inviteBusy.value.delete(n.inviteId)
  }
}

async function onCommand(cmd) {
  if (cmd === 'me') {
    router.push('/me')
  } else if (cmd === 'logout') {
    await store.logout()
    ElMessage.success('已退出登录')
    router.push('/')
  }
}

watch(
  () => store.isLogin,
  (isLogin) => {
    if (dmTimer) clearInterval(dmTimer)
    if (unsubWs) unsubWs()
    dmTimer = null
    unsubWs = null
    notifications.value = []
    dmUnread.value = 0
    adminPending.value = 0
    if (isLogin) {
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
        if (
          msg.type === 'DM' &&
          msg.data &&
          msg.data.receiverId === store.user?.id
        ) {
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
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  if (dmTimer) clearInterval(dmTimer)
  if (unsubWs) unsubWs()
})
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
}
.sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 100;
  width: 224px;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-right: 1px solid #e8edf5;
  padding: 30px 16px 20px;
  overflow-y: auto;
}
.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  text-decoration: none;
  padding: 0 12px 36px;
}
.brand-mark {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: #3867ed;
  color: white;
  font-size: 24px;
  box-shadow: 0 5px 12px #3867ed28;
}
.brand strong {
  display: block;
  font-size: 21px;
  letter-spacing: 1px;
}
.brand small {
  display: block;
  color: #8794aa;
  font-size: 8px;
  letter-spacing: 1.5px;
  margin-top: 5px;
}
.nav-caption {
  font-size: 11px;
  color: #929db0;
  padding: 0 17px;
  margin-bottom: 12px;
}
.secondary-caption {
  margin-top: 32px;
}
.nav-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.nav-link {
  display: flex;
  align-items: center;
  gap: 13px;
  min-height: 45px;
  border-radius: 9px;
  padding: 0 16px;
  text-decoration: none;
  color: #68768d;
  font-size: 13px;
  transition:
    background 0.2s,
    color 0.2s;
}
.nav-link > .el-icon {
  font-size: 19px;
}
.nav-link:hover {
  background: #f6f8fc;
  color: #3867ed;
}
.nav-link.selected {
  color: #3867ed;
  background: #edf2ff;
  font-weight: 600;
}
.nav-chip {
  margin-left: auto;
  font-size: 9px;
  border: 1px solid #dfe6f4;
  border-radius: 4px;
  padding: 2px 4px;
  color: #8a97ac;
  font-weight: 400;
}
.sidebar-bottom {
  margin-top: auto;
  padding-top: 40px;
}
.growth-card {
  background: linear-gradient(135deg, #f0f4ff, #f7f9ff);
  border: 1px solid #e9effc;
  border-radius: 12px;
  padding: 18px 14px;
}
.growth-icon {
  display: block;
  font-size: 23px;
  color: #3867ed;
  margin-bottom: 10px;
}
.growth-card strong {
  font-size: 12px;
}
.growth-card p {
  font-size: 10px;
  color: #8591a5;
  line-height: 1.8;
  margin: 8px 0 14px;
}
.growth-card a {
  color: #3867ed;
  text-decoration: none;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.sidebar-footnote {
  display: block;
  text-align: center;
  font-size: 10px;
  color: #9aa5b6;
  margin-top: 22px;
}
.workspace {
  margin-left: 224px;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.navbar {
  height: 76px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 36px;
  background: #ffffffed;
  border-bottom: 1px solid #e8edf5;
}
.header-left,
.nav-right {
  display: flex;
  align-items: center;
  gap: 18px;
}
.header-left {
  font-size: 12px;
}
.breadcrumb-root,
.breadcrumb-divider {
  color: #939fb1;
}
.breadcrumb-current {
  font-weight: 500;
}
.user-name {
  display: flex;
  align-items: center;
  gap: 9px;
  color: #52617a;
  cursor: pointer;
  font-size: 12px;
}
.main {
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  padding: 32px 36px;
  flex: 1;
  min-width: 0;
  outline: none;
}
.workspace-footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 36px 24px;
  font-size: 10px;
  color: #96a1b3;
}
.menu-toggle {
  display: none;
}
.skip-link {
  position: fixed;
  top: -100px;
  left: 240px;
  z-index: 3000;
  background: #fff;
  padding: 12px;
}
.skip-link:focus {
  top: 10px;
}
.sidebar-overlay {
  display: none;
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

@media (max-width: 1100px) {
  .sidebar {
    width: 204px;
  }
  .workspace {
    margin-left: 204px;
  }
  .main {
    padding: 28px 24px;
  }
  .navbar {
    padding: 0 24px;
  }
}
@media (max-width: 767px) {
  .sidebar {
    transform: translateX(-100%);
    visibility: hidden;
    width: 224px;
    transition: transform 0.2s;
  }
  .sidebar.open {
    transform: translateX(0);
    visibility: visible;
  }
  .sidebar-overlay {
    display: block;
    position: fixed;
    inset: 0;
    border: 0;
    background: #15213b65;
    z-index: 99;
  }
  .workspace {
    margin-left: 0;
  }
  .navbar {
    height: 64px;
    padding: 0 16px;
  }
  .menu-toggle {
    display: inline-flex;
  }
  .header-left {
    gap: 10px;
  }
  .breadcrumb-root,
  .breadcrumb-divider {
    display: none;
  }
  .nav-right {
    gap: 12px;
  }
  .main {
    padding: 24px 16px;
  }
  .workspace-footer {
    padding: 16px;
    font-size: 9px;
  }
  .workspace-footer > :last-child {
    display: none;
  }
  .skip-link {
    left: 16px;
  }
}
</style>
