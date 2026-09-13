<template>
  <el-card shadow="never" class="connections">
    <template #header>
      <div class="connections-heading">
        <div>
          <UiLabel icon="Connection">我的关注与粉丝</UiLabel>
          <p>找到熟悉的伙伴，让下一次组队从这里开始。</p>
        </div>
        <el-button
          size="small"
          :loading="loading"
          :disabled="busy.size > 0"
          @click="load"
          >刷新列表</el-button
        >
      </div>
    </template>
    <el-tabs v-model="activeTab">
      <el-tab-pane
        :label="`我的关注${ready ? ` · ${following.length}` : ''}`"
        name="following"
      />
      <el-tab-pane
        :label="`关注我的人${ready ? ` · ${followers.length}` : ''}`"
        name="followers"
      />
    </el-tabs>
    <div v-if="error" class="connections-error" role="alert">
      <el-alert
        title="列表加载失败，请刷新重试"
        type="error"
        :closable="false"
        show-icon
      />
    </div>
    <div v-loading="loading" class="connections-content" :aria-busy="loading">
      <template v-if="ready && !error">
        <div class="connections-toolbar">
          <el-input
            v-model="keyword"
            placeholder="搜索昵称、用户名或学院"
            aria-label="搜索关注与粉丝"
            clearable
          >
            <template #prefix
              ><el-icon><Search /></el-icon
            ></template>
          </el-input>
          <span class="connections-total"
            >{{ keyword.trim() ? '找到' : '共' }}
            {{ filtered.length }} 位同学</span
          >
        </div>
        <div v-if="visibleUsers.length" class="connections-grid">
          <article
            v-for="user in visibleUsers"
            :key="user.id"
            class="connection-card"
            :data-user-id="user.id"
          >
            <div class="connection-identity">
              <router-link
                :to="`/users/${user.id}`"
                :aria-label="`查看${user.nickname || user.username}的主页`"
                class="connection-avatar"
              >
                <el-avatar :size="46" :src="user.avatar || undefined">{{
                  (user.nickname || user.username || '?').charAt(0)
                }}</el-avatar>
              </router-link>
              <div class="connection-info">
                <router-link
                  :to="`/users/${user.id}`"
                  class="connection-name"
                  >{{ user.nickname || user.username }}</router-link
                >
                <div class="connection-username">@{{ user.username }}</div>
              </div>
              <el-tag
                v-if="isFollowing(user.id) && followerIds.has(user.id)"
                size="small"
                effect="plain"
                type="success"
                >互相关注</el-tag
              >
            </div>
            <p class="connection-meta">
              {{
                [user.college, user.major].filter(Boolean).join(' · ') ||
                '暂未填写院系信息'
              }}
            </p>
            <p class="connection-intro">
              {{ user.intro || '这位同学还没有填写个人简介。' }}
            </p>
            <div class="connection-actions">
              <el-button
                size="small"
                :type="isFollowing(user.id) ? 'info' : 'primary'"
                :plain="isFollowing(user.id)"
                :loading="busy.has(user.id)"
                :disabled="loading"
                @click="toggleFollow(user)"
              >
                {{ isFollowing(user.id) ? '取消关注' : '回关' }}
              </el-button>
              <router-link
                v-if="isFollowing(user.id)"
                :to="{ path: '/chat', query: { to: user.id } }"
                class="connection-chat"
              >
                <el-icon><ChatDotRound /></el-icon>私聊
              </router-link>
              <router-link :to="`/users/${user.id}`" class="connection-home"
                >查看主页<el-icon><ArrowRight /></el-icon
              ></router-link>
            </div>
          </article>
        </div>
        <el-empty v-else :description="emptyDescription" :image-size="72">
          <el-button v-if="keyword.trim()" @click="keyword = ''"
            >清空搜索</el-button
          >
          <el-button v-else type="primary" plain @click="$router.push('/teams')"
            >去组队广场看看</el-button
          >
        </el-empty>
        <el-pagination
          v-if="filtered.length > pageSize"
          v-model:current-page="page"
          :page-size="pageSize"
          :total="filtered.length"
          :pager-count="5"
          layout="prev, pager, next"
          class="connections-pagination"
        />
      </template>
    </div>
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const activeTab = ref('following')
const following = ref([])
const followers = ref([])
const ready = ref(false)
const loading = ref(false)
const error = ref(false)
const busy = ref(new Set())
const keyword = ref('')
const page = ref(1)
const pageSize = 6
const followingIds = computed(
  () => new Set(following.value.map((user) => user.id))
)
const followerIds = computed(
  () => new Set(followers.value.map((user) => user.id))
)
const isFollowing = (id) => followingIds.value.has(id)
const filtered = computed(() => {
  const users =
    activeTab.value === 'following' ? following.value : followers.value
  const query = keyword.value.trim().toLowerCase()
  return users.filter((user) =>
    [user.nickname, user.username, user.college, user.major].some((value) =>
      (value || '').toLowerCase().includes(query)
    )
  )
})
const visibleUsers = computed(() =>
  filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize)
)
const emptyDescription = computed(() =>
  keyword.value.trim()
    ? '没有找到匹配的同学，换个关键词试试'
    : activeTab.value === 'following'
      ? '你还没有关注其他同学，去发现志同道合的队友吧'
      : '暂时还没有粉丝，完善资料让更多同学认识你'
)
watch([activeTab, keyword], () => {
  page.value = 1
})
watch(
  () => filtered.value.length,
  (total) => {
    page.value = Math.min(page.value, Math.max(1, Math.ceil(total / pageSize)))
  }
)

async function load() {
  if (loading.value || busy.value.size) return
  loading.value = true
  try {
    const [outgoing, incoming] = await Promise.all([
      api.get('/users/me/following'),
      api.get('/users/me/followers')
    ])
    following.value = outgoing
    followers.value = incoming
    ready.value = true
    error.value = false
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

async function toggleFollow(user) {
  if (busy.value.has(user.id) || loading.value) return
  busy.value.add(user.id)
  const wasFollowing = isFollowing(user.id)
  try {
    if (wasFollowing) {
      await api.delete(`/users/${user.id}/follow`)
      following.value = following.value.filter((item) => item.id !== user.id)
    } else {
      await api.post(`/users/${user.id}/follow`)
      following.value = [user, ...following.value]
    }
    ElMessage.success(wasFollowing ? '已取消关注' : '回关成功，现在可以私聊了')
  } catch {
    // 失败时保留原来的列表和关注状态，接口层提示错误。
  } finally {
    busy.value.delete(user.id)
  }
}

onMounted(load)
</script>

<style scoped>
.connections-heading,
.connections-toolbar,
.connection-identity,
.connection-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.connections-heading,
.connections-toolbar {
  justify-content: space-between;
}
.connections-heading p {
  margin: 8px 0 0;
  color: #7a889d;
  font-size: 13px;
  line-height: 1.6;
}
.connections-content {
  min-height: 160px;
}
.connections-toolbar {
  margin: 4px 0 20px;
}
.connections-toolbar .el-input {
  max-width: 340px;
}
.connections-total {
  color: #7a889d;
  font-size: 13px;
  white-space: nowrap;
}
.connections-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
.connection-card {
  min-width: 0;
  padding: 20px;
  border: 1px solid #e7edf5;
  border-radius: 16px;
  background: linear-gradient(140deg, #f8fbff, #fff 60%);
}
.connection-avatar {
  flex-shrink: 0;
  line-height: 0;
}
.connection-info {
  flex: 1;
  min-width: 0;
}
.connection-name {
  font-weight: 600;
  color: #24364b;
  text-decoration: none;
  overflow-wrap: anywhere;
}
.connection-name:hover {
  color: #3376e8;
}
.connection-username {
  font-size: 12px;
  color: #7a889d;
  margin-top: 4px;
  overflow-wrap: anywhere;
}
.connection-meta,
.connection-intro {
  font-size: 13px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.connection-meta {
  margin: 14px 0 4px;
  color: #617188;
}
.connection-intro {
  color: #8a96a7;
  margin: 0 0 18px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 44px;
}
.connection-actions {
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 14px;
  border-top: 1px solid #edf1f7;
}
.connection-home,
.connection-chat {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #3376e8;
  font-size: 13px;
  text-decoration: none;
}
.connection-home {
  margin-left: auto;
}
.connections-pagination {
  justify-content: center;
  margin-top: 22px;
}
@media (max-width: 900px) {
  .connections-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 480px) {
  .connections-heading,
  .connections-toolbar {
    flex-wrap: wrap;
  }
  .connections-toolbar .el-input {
    max-width: none;
  }
  .connection-card {
    padding: 14px;
  }
  .connection-identity {
    flex-wrap: wrap;
    gap: 8px;
  }
}
</style>
