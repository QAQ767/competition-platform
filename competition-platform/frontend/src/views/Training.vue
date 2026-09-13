<template>
  <div>
    <PageHeading
      title="把每一天的练习，变成赛场上的底气"
      description="找到适合自己的训练资源，记录进步，与伙伴一起持续成长。"
      eyebrow="A LITTLE BETTER EVERY DAY"
    />
    <el-card shadow="never" class="block">
      <template #header
        ><div class="training-header">
          <span
            ><el-icon><Reading /></el-icon> 我的训练空间</span
          ><small>保持好奇，持续精进</small>
        </div></template
      >
      <el-tabs v-model="tab" @tab-change="onTabChange">
        <!-- 1. 训练资源 -->
        <el-tab-pane label="训练资源" name="sites">
          <div class="filters">
            <el-radio-group
              v-model="filters.competition"
              @change="onFilterChange"
            >
              <el-radio-button :value="''">全部</el-radio-button>
              <el-radio-button
                v-for="c in TRAINING_COMPETITIONS"
                :key="c"
                :value="c"
                >{{ c }}</el-radio-button
              >
            </el-radio-group>
            <div class="tag-row">
              <el-tag
                v-for="t in tags"
                :key="t"
                :type="filters.tag === t ? 'primary' : 'info'"
                :effect="filters.tag === t ? 'dark' : 'plain'"
                style="cursor: pointer; margin-right: 6px; margin-bottom: 6px"
                @click="toggleTag(t)"
                >{{ t }}</el-tag
              >
            </div>
            <el-input
              v-model="filters.keyword"
              placeholder="搜索资源名/简介"
              clearable
              style="width: 220px"
              @keyup.enter="onFilterChange"
              @clear="onFilterChange"
            />
          </div>

          <el-row :gutter="16" v-loading="loadingSites">
            <el-col
              :xs="24"
              :sm="12"
              :xl="6"
              v-for="s in sites"
              :key="s.id"
              style="margin-bottom: 14px"
            >
              <el-card shadow="hover">
                <div class="site-head">
                  <span class="site-name">{{ s.name }}</span>
                  <button
                    type="button"
                    class="fav-star"
                    :class="{ on: favoriteIds.has(s.id) }"
                    :aria-label="
                      (favoriteIds.has(s.id) ? '取消收藏' : '收藏') + s.name
                    "
                    :aria-pressed="favoriteIds.has(s.id)"
                    @click="toggleFavorite(s)"
                  >
                    {{ favoriteIds.has(s.id) ? '★' : '☆' }}
                  </button>
                </div>
                <div class="site-tags">
                  <el-tag
                    size="small"
                    type="warning"
                    effect="plain"
                    style="margin-right: 4px"
                    >{{ s.competition }}</el-tag
                  >
                  <el-tag
                    size="small"
                    :type="TRAINING_DIFFICULTY_TYPE[s.difficulty] || 'info'"
                    effect="plain"
                    style="margin-right: 4px"
                    >{{ s.difficulty }}</el-tag
                  >
                  <el-tag
                    v-if="s.recommended"
                    size="small"
                    type="danger"
                    effect="dark"
                    >★ 推荐</el-tag
                  >
                </div>
                <div class="site-desc">{{ s.description }}</div>
                <div class="site-actions">
                  <a
                    :href="s.url"
                    target="_blank"
                    rel="noopener"
                    class="goto-btn"
                    >开始练习 ↗</a
                  >
                </div>
              </el-card>
            </el-col>
          </el-row>
          <el-empty
            v-if="!loadingSites && !sites.length"
            description="暂无训练资源"
            :image-size="60"
          />
          <el-pagination
            v-if="sitesTotal > sitesPageSize"
            layout="prev, pager, next, total"
            :total="sitesTotal"
            :page-size="sitesPageSize"
            :current-page="sitesPage"
            style="justify-content: center; margin-top: 8px"
            @current-change="onSitePage"
          />
        </el-tab-pane>

        <!-- 2. 训练打卡 -->
        <el-tab-pane label="今日打卡" name="checkin">
          <div class="checkin-panel" v-loading="loadingCheckin">
            <div class="checkin-left">
              <el-button
                :type="checkin.checkedToday ? 'success' : 'primary'"
                size="large"
                :disabled="checkin.checkedToday"
                @click="doCheckin"
                >{{
                  checkin.checkedToday ? '✓ 今日已打卡' : '一键打卡'
                }}</el-button
              >
              <div class="checkin-stats">
                <div class="stat">
                  <span class="num">{{ checkin.totalDays }}</span
                  ><span class="lab">累计天数</span>
                </div>
                <div class="stat">
                  <span class="num">{{ checkin.streakDays }}</span
                  ><span class="lab">连续天数</span>
                </div>
                <div class="stat">
                  <span class="num">{{
                    checkin.rank ? '#' + checkin.rank : '-'
                  }}</span
                  ><span class="lab">排行榜名次</span>
                </div>
              </div>
              <div class="checkin-level">
                当前等级：<el-tag
                  :type="CHECKIN_LEVEL_COLOR[checkin.level] || 'info'"
                  size="small"
                  >{{ checkin.level }}</el-tag
                >
                <span v-if="checkin.nextLevelNeed > 0" class="next-level"
                  >再打卡 {{ checkin.nextLevelNeed }} 天升级「{{
                    checkin.nextLevel
                  }}」</span
                >
                <span v-else class="next-level">已达最高等级 🏆</span>
              </div>
              <el-alert
                v-if="!store.isLogin"
                type="info"
                :closable="false"
                title="登录后即可每日训练打卡，积累天数冲榜"
                style="margin-top: 10px"
              />
            </div>
            <div class="checkin-cal">
              <div class="cal-title">{{ calTitle }}</div>
              <div class="cal-grid">
                <div
                  class="cal-week"
                  v-for="w in ['一', '二', '三', '四', '五', '六', '日']"
                  :key="w"
                >
                  {{ w }}
                </div>
                <div
                  v-for="(d, i) in calCells"
                  :key="i"
                  class="cal-cell"
                  :class="{ checked: d.checked, today: d.today }"
                >
                  {{ d.day || '' }}
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 3. 排行榜 -->
        <el-tab-pane label="打卡排行" name="rank">
          <div class="rank-head">
            <el-radio-group v-model="rankRange" @change="loadLeaderboard">
              <el-radio-button value="all">总榜</el-radio-button>
              <el-radio-button value="month">本月</el-radio-button>
            </el-radio-group>
          </div>
          <el-table :data="leaderboard" size="small" v-loading="loadingRank">
            <el-table-column label="名次" width="70">
              <template #default="{ row }">
                <span
                  :class="['rank-no', row.rank <= 3 ? 'top' + row.rank : '']"
                  >{{ row.rank }}</span
                >
              </template>
            </el-table-column>
            <el-table-column label="用户" min-width="160">
              <template #default="{ row }">
                <span
                  class="name-link"
                  @click="$router.push(`/users/${row.userId}`)"
                  @contextmenu.prevent="
                    openUserMenu($event, {
                      userId: row.userId,
                      userName: row.nickname
                    })
                  "
                >
                  <el-avatar :size="24" :src="row.avatar || undefined">{{
                    (row.nickname || '?').charAt(0)
                  }}</el-avatar>
                  {{ row.nickname }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="days" label="打卡天数" width="120">
              <template #default="{ row }"
                ><b class="rank-days">{{ row.days }}</b> 天</template
              >
            </el-table-column>
          </el-table>
          <el-empty
            v-if="!loadingRank && !leaderboard.length"
            description="暂无打卡记录"
            :image-size="60"
          />
        </el-tab-pane>

        <!-- 4. 我的收藏 -->
        <el-tab-pane label="我的收藏" name="fav">
          <div v-loading="loadingFav">
            <el-alert
              v-if="!store.isLogin"
              type="info"
              :closable="false"
              title="登录后可收藏训练资源"
              style="margin-bottom: 12px"
            />
            <el-row :gutter="16">
              <el-col
                :xs="24"
                :sm="12"
                :xl="6"
                v-for="s in favorites"
                :key="s.id"
                style="margin-bottom: 14px"
              >
                <el-card shadow="hover">
                  <div class="site-head">
                    <span class="site-name">{{ s.name }}</span>
                    <button
                      type="button"
                      class="fav-star on"
                      :aria-label="'取消收藏' + s.name"
                      @click="unfavorite(s)"
                    >
                      ★
                    </button>
                  </div>
                  <div class="site-tags">
                    <el-tag
                      size="small"
                      type="warning"
                      effect="plain"
                      style="margin-right: 4px"
                      >{{ s.competition }}</el-tag
                    >
                    <el-tag
                      size="small"
                      :type="TRAINING_DIFFICULTY_TYPE[s.difficulty] || 'info'"
                      effect="plain"
                      >{{ s.difficulty }}</el-tag
                    >
                  </div>
                  <div class="site-desc">{{ s.description }}</div>
                  <div class="site-actions">
                    <a
                      :href="s.url"
                      target="_blank"
                      rel="noopener"
                      class="goto-btn"
                      >开始练习 ↗</a
                    >
                  </div>
                </el-card>
              </el-col>
            </el-row>
            <el-empty
              v-if="store.isLogin && !loadingFav && !favorites.length"
              description="还没有收藏任何资源"
              :image-size="60"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { useUserStore } from '../stores/user'
import { openUserMenu } from '../utils/userMenu'
import {
  TRAINING_COMPETITIONS,
  TRAINING_DIFFICULTY_TYPE,
  CHECKIN_LEVEL_COLOR
} from '../utils/constants'

const store = useUserStore()
const tab = ref('sites')

// ===== 训练资源 =====
const tags = ref([])
const sites = ref([])
const sitesTotal = ref(0)
const sitesPage = ref(1)
const sitesPageSize = 12
const loadingSites = ref(false)
const filters = reactive({ competition: '', tag: '', keyword: '' })
const favoriteIds = ref(new Set())

async function loadTags() {
  try {
    tags.value = await api.get('/training/tags')
  } catch (e) {
    /* 忽略 */
  }
}

async function loadSites() {
  loadingSites.value = true
  try {
    const params = { page: sitesPage.value, size: sitesPageSize }
    if (filters.competition) params.competition = filters.competition
    if (filters.tag) params.tag = filters.tag
    if (filters.keyword) params.keyword = filters.keyword
    const data = await api.get('/training/sites', { params })
    sites.value = data.records || []
    sitesTotal.value = data.total || 0
  } finally {
    loadingSites.value = false
  }
}

function onFilterChange() {
  sitesPage.value = 1
  loadSites()
}

function toggleTag(t) {
  filters.tag = filters.tag === t ? '' : t
  onFilterChange()
}

function onSitePage(p) {
  sitesPage.value = p
  loadSites()
}

async function loadFavoriteIds() {
  if (!store.isLogin) return
  try {
    const ids = await api.get('/training/favorites/ids')
    favoriteIds.value = new Set(ids || [])
  } catch (e) {
    /* 忽略 */
  }
}

async function toggleFavorite(s) {
  if (!store.isLogin) {
    ElMessage.warning('请先登录后再收藏')
    return
  }
  const on = await api.post(`/training/sites/${s.id}/favorite`)
  if (on) {
    favoriteIds.value.add(s.id)
    ElMessage.success(`已收藏「${s.name}」`)
  } else {
    favoriteIds.value.delete(s.id)
    ElMessage.info(`已取消收藏「${s.name}」`)
  }
}

// ===== 打卡 =====
const checkin = reactive({
  checkedToday: false,
  totalDays: 0,
  streakDays: 0,
  level: '',
  nextLevel: '',
  nextLevelNeed: 0,
  rank: 0,
  monthDates: []
})
const loadingCheckin = ref(false)

const calTitle = computed(() => {
  const now = new Date()
  return `${now.getFullYear()} 年 ${now.getMonth() + 1} 月`
})

const calCells = computed(() => {
  const now = new Date()
  const year = now.getFullYear()
  const month = now.getMonth()
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const firstDay = new Date(year, month, 1).getDay() // 周日=0
  const offset = (firstDay + 6) % 7 // 周一起始
  const todayStr = `${year}-${String(month + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  const checkSet = new Set(checkin.monthDates || [])
  const cells = []
  for (let i = 0; i < offset; i++)
    cells.push({ day: '', checked: false, today: false })
  for (let d = 1; d <= daysInMonth; d++) {
    const ds = `${year}-${String(month + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`
    cells.push({ day: d, checked: checkSet.has(ds), today: ds === todayStr })
  }
  return cells
})

async function loadCheckin() {
  if (!store.isLogin) return
  loadingCheckin.value = true
  try {
    Object.assign(checkin, await api.get('/training/checkin/today'))
  } finally {
    loadingCheckin.value = false
  }
}

async function doCheckin() {
  if (!store.isLogin) {
    ElMessage.warning('请先登录')
    return
  }
  await api.post('/training/checkin')
  ElMessage.success('今日训练打卡成功 ✓')
  loadCheckin()
}

// ===== 排行榜 =====
const leaderboard = ref([])
const rankRange = ref('all')
const loadingRank = ref(false)

async function loadLeaderboard() {
  if (!store.isLogin) return
  loadingRank.value = true
  try {
    leaderboard.value = await api.get('/training/checkin/leaderboard', {
      params: { range: rankRange.value }
    })
  } finally {
    loadingRank.value = false
  }
}

// ===== 我的收藏 =====
const favorites = ref([])
const loadingFav = ref(false)

async function loadFavorites() {
  if (!store.isLogin) return
  loadingFav.value = true
  try {
    favorites.value = await api.get('/training/favorites')
  } finally {
    loadingFav.value = false
  }
}

async function unfavorite(s) {
  const on = await api.post(`/training/sites/${s.id}/favorite`)
  if (!on) {
    favorites.value = favorites.value.filter((x) => x.id !== s.id)
    favoriteIds.value.delete(s.id)
    ElMessage.info('已取消收藏')
  }
}

function onTabChange(name) {
  if (name === 'checkin') loadCheckin()
  else if (name === 'rank') loadLeaderboard()
  else if (name === 'fav') loadFavorites()
}

onMounted(() => {
  loadTags()
  loadSites()
  if (store.isLogin) {
    loadFavoriteIds()
  }
  if (store.isLogin) {
    loadCheckin()
    loadLeaderboard()
  }
})
</script>

<style scoped>
.block {
  margin-bottom: 16px;
}
.filters {
  margin-bottom: 14px;
}
.tag-row {
  margin-top: 10px;
}
.site-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.site-name {
  font-size: 15px;
  font-weight: 600;
}
.fav-star {
  cursor: pointer;
  font-size: 20px;
  color: #c0c4cc;
}
.fav-star.on {
  color: #f7ba2a;
}
.site-tags {
  margin-top: 8px;
}
.site-desc {
  color: #606266;
  font-size: 13px;
  margin-top: 8px;
  min-height: 38px;
}
.site-actions {
  margin-top: 10px;
}
.goto-btn {
  color: #409eff;
  text-decoration: none;
  font-size: 14px;
}
.goto-btn:hover {
  text-decoration: underline;
}
.checkin-panel {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}
.checkin-left {
  flex: 1;
  min-width: 280px;
}
.checkin-stats {
  display: flex;
  gap: 24px;
  margin: 16px 0;
}
.stat {
  text-align: center;
}
.stat .num {
  display: block;
  font-size: 26px;
  font-weight: 700;
  color: #409eff;
}
.stat .lab {
  font-size: 12px;
  color: #909399;
}
.checkin-level {
  font-size: 14px;
  color: #303133;
}
.next-level {
  margin-left: 8px;
  font-size: 13px;
  color: #909399;
}
.checkin-cal {
  width: 320px;
  background: #f5f7fa;
  border-radius: 8px;
  padding: 12px;
}
.cal-title {
  text-align: center;
  font-weight: 600;
  margin-bottom: 8px;
}
.cal-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
}
.cal-week {
  text-align: center;
  font-size: 12px;
  color: #909399;
}
.cal-cell {
  height: 34px;
  line-height: 34px;
  text-align: center;
  border-radius: 4px;
  font-size: 13px;
  color: #303133;
  background: #fff;
}
.cal-cell.checked {
  background: #409eff;
  color: #fff;
}
.cal-cell.today {
  border: 2px solid #f7ba2a;
}
.rank-head {
  margin-bottom: 12px;
}
.rank-no {
  font-weight: 700;
}
.rank-no.top1 {
  color: #f7ba2a;
}
.rank-no.top2 {
  color: #909399;
}
.rank-no.top3 {
  color: #cd7f32;
}
.rank-days {
  color: #409eff;
}
.name-link {
  color: #409eff;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.name-link:hover {
  text-decoration: underline;
}
.training-header,
.training-header > span {
  display: flex;
  align-items: center;
  gap: 9px;
}
.training-header {
  justify-content: space-between;
}
.training-header small {
  font-size: 11px;
  color: #8b98ac;
  font-weight: 400;
}
.filters {
  padding: 8px 0 20px;
}
.tag-row {
  margin: 18px 0 12px;
}
.filters .el-radio-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.filters :deep(.el-radio-button__inner) {
  border: 1px solid #e5eaf3;
  border-radius: 7px !important;
  box-shadow: none !important;
  font-size: 12px;
}
.site-head {
  gap: 12px;
}
.site-name {
  line-height: 1.6;
}
.fav-star {
  border: 0;
  background: transparent;
  padding: 4px;
  flex-shrink: 0;
}
.site-tags {
  display: flex;
  gap: 5px;
  flex-wrap: wrap;
  margin: 14px 0;
}
.site-desc {
  min-height: 66px;
  line-height: 1.8;
  font-size: 12px;
  color: #7b889f;
}
.site-actions {
  border-top: 1px solid #edf0f5;
  padding-top: 15px;
  margin-top: 16px;
}
.goto-btn {
  color: #3867ed;
  font-size: 12px;
  font-weight: 600;
}
.checkin-panel {
  padding: 20px 0;
}
.checkin-stats {
  margin: 30px 0;
  gap: 40px;
}
.checkin-cal {
  padding: 20px;
  background: #f2f5fd;
}
.cal-cell.checked {
  background: #3867ed;
}
.cal-title {
  margin-bottom: 18px;
}
.checkin-left {
  min-width: 0;
}
.checkin-cal {
  max-width: 100%;
}
@media (max-width: 767px) {
  .training-header small {
    display: none;
  }
  .checkin-stats {
    gap: 24px;
  }
  .checkin-panel {
    flex-direction: column;
  }
  .checkin-cal {
    width: 100%;
  }
}
</style>
