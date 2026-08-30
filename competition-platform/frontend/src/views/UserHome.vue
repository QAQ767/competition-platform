<template>
  <div v-loading="loading">
    <el-card shadow="never" class="block">
      <div class="user-head">
        <el-avatar :size="64" :src="home?.user?.avatar || undefined">{{ (home?.user?.nickname || '?').charAt(0) }}</el-avatar>
        <div class="user-info">
          <div class="user-name">{{ home?.user?.nickname }}
            <el-tag v-if="home?.isSelf" size="small" type="info" style="margin-left: 6px">我</el-tag>
          </div>
          <div class="user-meta">用户名：{{ home?.user?.username }} ｜ {{ home?.user?.college || '—' }} · {{ home?.user?.major || '—' }}</div>
          <div class="user-meta">简介：{{ home?.user?.intro || '—' }}</div>
          <div class="user-meta">粉丝 {{ home?.followerCount }} · 关注 {{ home?.followingCount }} ｜ 参赛状态：
            <el-tag size="small" :type="COMPETE_STATUS[home?.user?.competeStatus]?.type || 'info'">
              {{ COMPETE_STATUS[home?.user?.competeStatus]?.label || home?.user?.competeStatus }}
            </el-tag>
          </div>
        </div>
        <div class="user-actions" v-if="store.isLogin && !home?.isSelf">
          <el-button v-if="home?.isFollowing" type="info" size="small" plain @click="toggleFollow">已关注</el-button>
          <el-button v-else type="primary" size="small" @click="toggleFollow">＋ 关注</el-button>
          <el-button type="success" size="small" @click="goChat">💬 私聊</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="block">
      <template #header>🏆 近期获奖记录（已审核通过）</template>
      <el-row :gutter="16">
        <el-col :span="8" v-for="a in home?.achievements || []" :key="a.id" style="margin-bottom: 16px">
          <el-card shadow="hover">
            <div class="ach-head">
              <span class="ach-name">{{ a.name }}</span>
              <el-tag size="small">{{ a.level }}</el-tag>
            </div>
            <div class="ach-meta">🏅 {{ a.award }}</div>
            <div class="ach-meta">🎯 {{ a.competitionName }} · {{ a.awardYear || '—' }}</div>
            <div class="ach-meta" v-if="a.proof">📄 <el-link type="primary" :href="a.proof" target="_blank" :underline="false">查看证明</el-link></div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-if="!home?.achievements?.length" description="暂无获奖记录" :image-size="60" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'
import { useUserStore } from '../stores/user'
import { COMPETE_STATUS } from '../utils/constants'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const loading = ref(false)
const home = ref(null)

async function load() {
  loading.value = true
  try {
    home.value = await api.get(`/users/${route.params.id}`)
  } finally {
    loading.value = false
  }
}

async function toggleFollow() {
  if (!store.isLogin) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  if (home.value.isFollowing) {
    await api.delete(`/users/${route.params.id}/follow`)
    home.value.isFollowing = false
    home.value.followerCount--
    ElMessage.success('已取消关注')
  } else {
    await api.post(`/users/${route.params.id}/follow`)
    home.value.isFollowing = true
    home.value.followerCount++
    ElMessage.success('关注成功，现在可以私聊对方了')
  }
}

function goChat() {
  if (!store.isLogin) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  router.push({ path: '/chat', query: { to: Number(route.params.id) } })
}

onMounted(load)
</script>

<style scoped>
.block {
  margin-bottom: 16px;
}
.user-head {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}
.user-info {
  flex: 1;
}
.user-name {
  font-size: 20px;
  font-weight: 700;
}
.user-meta {
  color: #606266;
  font-size: 13px;
  margin-top: 6px;
}
.user-actions {
  display: flex;
  gap: 8px;
}
.ach-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.ach-name {
  font-size: 15px;
  font-weight: 600;
}
.ach-meta {
  color: #606266;
  font-size: 13px;
  margin-top: 6px;
}
</style>
