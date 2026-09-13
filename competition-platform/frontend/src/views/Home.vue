<template>
  <div class="home-page">
    <PageHeading
      title="发现竞赛，遇见并肩的伙伴"
      description="从一个想法出发，找到你的队伍，让每一次挑战都成为成长。"
      eyebrow="EXPLORE YOUR POSSIBILITIES"
      ><span class="today-label"
        ><el-icon><Calendar /></el-icon>{{ today }}</span
      ></PageHeading
    >
    <section class="hero">
      <div class="hero-copy">
        <span class="hero-kicker">● &nbsp; 在这里，让热爱集结</span>
        <h2>一个人有想法，<br />一群人有<span>无限可能。</span></h2>
        <p>发现适合你的竞赛 · 找到技能互补的队友 · 一起向更高处进发</p>
        <div class="hero-actions">
          <el-button type="primary" size="large" @click="router.push('/teams')"
            >寻找我的队友 <el-icon><Right /></el-icon></el-button
          ><el-button size="large" @click="router.push('/training')"
            >探索训练资源 <el-icon><ArrowRight /></el-icon
          ></el-button>
        </div>
        <div class="hero-caption">
          <span class="mini-avatars"><i>竞</i><i>赛</i><i>同</i><i>行</i></span
          ><span>不同的专长，相同的目标</span>
        </div>
      </div>
      <div class="hero-art" aria-hidden="true">
        <div class="orbit"></div>
        <div class="orbit orbit-two"></div>
        <span class="art-spark">✦</span>
        <div class="floating-label label-code">
          <el-icon><Monitor /></el-icon
          ><span>技能互补<small>一起解决难题</small></span>
        </div>
        <div class="trophy-stage">
          <svg viewBox="0 0 240 240">
            <defs>
              <linearGradient id="cup" x1="0" y1="0" x2="1" y2="1">
                <stop stop-color="#82a7ff" />
                <stop offset="1" stop-color="#3867ed" />
              </linearGradient>
            </defs>
            <ellipse
              cx="123"
              cy="215"
              rx="70"
              ry="9"
              fill="#3867ed"
              opacity=".1"
            />
            <path
              d="M73 55H43v23c0 30 17 42 42 42M167 55h30v23c0 30-17 42-42 42"
              fill="none"
              stroke="#8cacf9"
              stroke-width="12"
            />
            <path
              d="M70 37h100v58c0 37-23 59-50 59S70 132 70 95Z"
              fill="url(#cup)"
            />
            <path
              d="M81 45h11v54c0 19 5 31 13 40-15-8-24-22-24-44Z"
              fill="#b8d0ff"
              opacity=".65"
            />
            <path
              d="M111 152h18v32h-18zM92 182h56l10 21H82Z"
              fill="url(#cup)"
            />
            <rect x="76" y="202" width="88" height="11" rx="5" fill="#557bdd" />
            <path
              d="m120 65 8 16 18 3-13 12 3 18-16-9-16 9 3-18-13-12 18-3z"
              fill="#f2f6ff"
            />
          </svg>
        </div>
        <div class="floating-label label-team">
          <el-icon><CircleCheckFilled /></el-icon
          ><span>并肩作战<small>让梦想更进一步</small></span>
        </div>
      </div>
    </section>
    <div class="quick-links">
      <router-link
        v-for="item in shortcuts"
        :key="item.path"
        :to="item.path"
        class="quick-link"
        ><span class="quick-icon" :class="item.color"
          ><el-icon><component :is="item.icon" /></el-icon></span
        ><span
          ><strong>{{ item.title }}</strong
          ><small>{{ item.description }}</small></span
        ><el-icon class="quick-arrow"><ArrowRight /></el-icon
      ></router-link>
    </div>
    <section aria-labelledby="competition-title">
      <div class="section-heading">
        <div>
          <h2 id="competition-title">值得奔赴的下一场挑战</h2>
          <p>探索竞赛机会，从这里开启你的参赛旅程</p>
        </div>
        <span class="result-count" aria-live="polite">{{
          loading
            ? '正在加载…'
            : failed
              ? '加载未完成'
              : '共 ' + list.length + ' 项竞赛'
        }}</span>
      </div>
      <el-card shadow="never" class="filter-card"
        ><el-input
          v-model="keyword"
          :prefix-icon="Search"
          placeholder="搜索竞赛名称 / 简介"
          aria-label="搜索竞赛"
          clearable
          class="search-input"
          @keyup.enter="load"
          @clear="load"
        /><el-select
          v-model="level"
          placeholder="全部级别"
          aria-label="竞赛级别"
          clearable
          @change="load"
          ><el-option
            v-for="l in LEVELS"
            :key="l"
            :label="l"
            :value="l" /></el-select
        ><el-select
          v-model="status"
          placeholder="全部状态"
          aria-label="竞赛状态"
          clearable
          @change="load"
          ><el-option
            v-for="s in ['报名中', '即将开始', '已结束']"
            :key="s"
            :label="s"
            :value="s" /></el-select
        ><el-button type="primary" :loading="loading" @click="load"
          >搜索竞赛</el-button
        ></el-card
      >
      <div v-if="loading" class="competition-grid" aria-label="正在加载竞赛">
        <el-card v-for="n in 3" :key="n" shadow="never"
          ><el-skeleton :rows="5" animated
        /></el-card>
      </div>
      <el-result
        v-else-if="failed"
        icon="warning"
        title="竞赛暂时加载失败"
        sub-title="请检查网络连接后重试。"
        ><template #extra
          ><el-button type="primary" @click="load"
            >重新加载</el-button
          ></template
        ></el-result
      >
      <div v-else-if="list.length" class="competition-grid">
        <el-card
          v-for="(c, index) in list"
          :key="c.id"
          shadow="hover"
          class="competition-card"
          ><div class="comp-cover" :class="'cover-' + (index % 4)">
            <span class="cover-category">{{ c.level }} · 学科竞赛</span
            ><span class="cover-symbol" aria-hidden="true"
              ><el-icon
                ><component :is="competitionIcon(c.name)" /></el-icon></span
            ><span class="cover-line" aria-hidden="true"></span>
          </div>
          <div class="comp-body">
            <div class="comp-head">
              <el-tag :type="statusType(c.status)" size="small">{{
                c.status
              }}</el-tag
              ><span class="comp-level">{{ c.level }}</span>
            </div>
            <h3>{{ c.name }}</h3>
            <p class="comp-desc">
              {{ c.description || '发现新的挑战，与志同道合的伙伴一起探索。' }}
            </p>
            <div class="comp-meta">
              <el-icon><OfficeBuilding /></el-icon
              ><span>{{ c.organizer || '主办方待公布' }}</span>
            </div>
            <div class="comp-meta">
              <el-icon><Calendar /></el-icon
              ><span>报名截止 {{ formatDate(c.signupEnd) }}</span>
            </div>
            <div class="comp-footer">
              <span
                ><el-icon><Timer /></el-icon>{{ deadlineLabel(c) }}</span
              ><el-button
                v-if="c.status !== '已结束'"
                type="primary"
                link
                @click="goTeam(c.id)"
                >前往组队 <el-icon><ArrowRight /></el-icon></el-button
              ><span v-else>期待下一次相遇</span>
            </div>
          </div></el-card
        >
      </div>
      <el-empty v-else description="暂时没有符合条件的竞赛"
        ><el-button @click="resetFilters">重置筛选</el-button></el-empty
      >
    </section>
    <div class="home-note">
      <el-icon><InfoFilled /></el-icon
      ><span>没有找到想参加的赛事？把它告诉我们，一起丰富校园竞赛地图。</span
      ><router-link to="/feedback">提交赛事反馈 →</router-link>
    </div>
  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  Search,
  Connection,
  Reading,
  Medal,
  Monitor,
  DataAnalysis,
  Trophy,
  Cpu
} from '@element-plus/icons-vue'
import api from '../api'
import { LEVELS } from '../utils/constants'
const router = useRouter()
const keyword = ref(''),
  level = ref(''),
  status = ref(''),
  list = ref([]),
  loading = ref(false),
  failed = ref(false)
const today = new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'long'
}).format(new Date())
const shortcuts = [
  {
    path: '/teams',
    title: '找到合拍的队友',
    description: '技能互补，让协作更有默契',
    icon: Connection,
    color: 'blue'
  },
  {
    path: '/training',
    title: '积累下一次突破',
    description: '精选资源，让训练更有方向',
    icon: Reading,
    color: 'green'
  },
  {
    path: '/achievements',
    title: '见证每一份荣誉',
    description: '记录成果，让努力被看见',
    icon: Medal,
    color: 'amber'
  }
]
function competitionIcon(name = '') {
  return /数学|建模|统计/.test(name)
    ? DataAnalysis
    : /程序|算法|软件|计算机/.test(name)
      ? Monitor
      : /电子|机器|智能/.test(name)
        ? Cpu
        : Trophy
}
function statusType(s) {
  return { 报名中: 'success', 即将开始: 'warning', 已结束: 'info' }[s] || 'info'
}
function formatDate(value) {
  return value ? String(value).slice(0, 10).replace(/-/g, '.') : '待公布'
}
function deadlineLabel(c) {
  if (c.status === '已结束') return '本期报名已结束'
  if (c.status === '即将开始') return '即将开放报名'
  const end = c.signupEnd
    ? new Date(String(c.signupEnd).replace(' ', 'T')).getTime()
    : NaN
  if (Number.isNaN(end)) return '关注报名时间'
  const days = Math.max(0, Math.ceil((end - Date.now()) / 86400000))
  return days === 0 ? '报名截止临近' : '距报名截止 ' + days + ' 天'
}
let requestId = 0
async function load() {
  const id = ++requestId
  loading.value = true
  failed.value = false
  try {
    const params = {}
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (level.value) params.level = level.value
    if (status.value) params.status = status.value
    const data = await api.get('/competitions', { params })
    if (id === requestId) list.value = data || []
  } catch {
    if (id === requestId) failed.value = true
  } finally {
    if (id === requestId) loading.value = false
  }
}
function resetFilters() {
  keyword.value = ''
  level.value = ''
  status.value = ''
  load()
}
function goTeam(id) {
  router.push({ path: '/teams', query: { competitionId: id } })
}
onMounted(load)
</script>
<style scoped>
.today-label {
  display: flex;
  align-items: center;
  gap: 7px;
  white-space: nowrap;
  color: #8592a7;
  font-size: 11px;
}
.hero {
  position: relative;
  display: grid;
  grid-template-columns: 1.3fr 1fr;
  min-height: 310px;
  overflow: hidden;
  border: 1px solid #dce6fc;
  border-radius: 17px;
  background: linear-gradient(115deg, #eef3ff, #edf3ff 45%, #e4edff);
}
.hero-copy {
  padding: 32px 36px;
  z-index: 1;
}
.hero-kicker {
  display: block;
  color: #5376c7;
  font-size: 11px;
  margin-bottom: 17px;
}
.hero h2 {
  font-size: clamp(25px, 2.3vw, 36px);
  line-height: 1.5;
  letter-spacing: 1px;
  margin-bottom: 12px;
}
.hero h2 > span {
  color: #3867ed;
}
.hero-copy > p {
  font-size: 11px;
  line-height: 1.8;
  color: #7083a5;
  margin-bottom: 22px;
}
.hero-actions {
  display: flex;
  gap: 10px;
}
.hero-actions .el-button {
  margin: 0;
  font-size: 12px;
  padding: 12px 18px;
}
.hero-actions .el-icon {
  margin-left: 9px;
}
.hero-caption {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 10px;
  color: #7183a3;
  margin-top: 21px;
}
.mini-avatars {
  display: flex;
  padding-left: 4px;
}
.mini-avatars i {
  width: 24px;
  height: 24px;
  border: 2px solid #f1f5ff;
  display: grid;
  place-items: center;
  border-radius: 50%;
  margin-left: -4px;
  background: #92abdf;
  font-style: normal;
  color: #fff;
  font-size: 9px;
}
.mini-avatars i:nth-child(2) {
  background: #83aaa6;
}
.mini-avatars i:nth-child(3) {
  background: #c3a17f;
}
.mini-avatars i:nth-child(4) {
  background: #9c94c5;
}
.hero-art {
  position: relative;
  display: grid;
  place-items: center;
}
.orbit {
  position: absolute;
  border: 1px solid #cbd9f5;
  width: 270px;
  height: 270px;
  border-radius: 50%;
  transform: rotate(-25deg) scaleY(0.72);
}
.orbit-two {
  width: 370px;
  height: 370px;
  border-style: dashed;
  opacity: 0.55;
}
.trophy-stage {
  width: 235px;
  height: 235px;
  transform: rotate(8deg);
  filter: drop-shadow(0 15px 12px #3b67d52b);
}
.floating-label {
  position: absolute;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid #fff;
  background: #ffffffdb;
  border-radius: 10px;
  padding: 12px 15px;
  box-shadow: 0 10px 30px #627dac12;
  font-size: 12px;
  color: #43587f;
}
.floating-label > .el-icon {
  font-size: 24px;
  color: #6e8ee1;
}
.floating-label small {
  display: block;
  font-size: 9px;
  color: #7c8ba5;
  margin-top: 5px;
}
.label-code {
  top: 44px;
  left: 2px;
  transform: rotate(-7deg);
}
.label-team {
  bottom: 43px;
  right: 27px;
  transform: rotate(6deg);
}
.label-team > .el-icon {
  color: #4da88b;
}
.art-spark {
  position: absolute;
  color: #8aa8e9;
  right: 52px;
  top: 100px;
  font-size: 24px;
}
.quick-links {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
  margin: 22px 0 34px;
}
.quick-link {
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 20px;
  background: white;
  border: 1px solid #e7ecf4;
  border-radius: 12px;
  text-decoration: none;
  transition:
    border-color 0.2s,
    transform 0.2s;
}
.quick-link:hover {
  border-color: #b6c9fa;
  transform: translateY(-2px);
}
.quick-icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 11px;
  font-size: 22px;
  flex-shrink: 0;
}
.blue {
  background: #edf2ff;
  color: #5c82ed;
}
.green {
  background: #eaf7f3;
  color: #4aa68a;
}
.amber {
  background: #fff6e8;
  color: #c79747;
}
.quick-link strong {
  display: block;
  font-size: 13px;
  font-weight: 600;
}
.quick-link small {
  display: block;
  color: #7b889f;
  font-size: 10px;
  margin-top: 7px;
}
.quick-arrow {
  margin-left: auto;
  color: #a7b3c6;
  font-size: 13px;
}
.section-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 15px;
  margin-bottom: 20px;
}
.section-heading h2 {
  font-size: 19px;
  margin-bottom: 8px;
}
.section-heading p {
  font-size: 11px;
  color: #7b889f;
  margin-bottom: 0;
}
.result-count {
  font-size: 11px;
  color: #7b889f;
  white-space: nowrap;
}
.filter-card .search-input {
  flex: 1;
  min-width: 180px;
}
.filter-card .el-select {
  width: 140px;
}
.filter-card .el-button {
  font-size: 12px;
}
.competition-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}
.competition-card :deep(.el-card__body) {
  padding: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
}
.comp-cover {
  position: relative;
  overflow: hidden;
  height: 108px;
  flex-shrink: 0;
  background: #edf2fc;
  color: #7294de;
  padding: 20px;
}
.cover-1 {
  background: #edf5f2;
  color: #81ada0;
}
.cover-2 {
  background: #f1effb;
  color: #9d91ce;
}
.cover-3 {
  background: #faf2e8;
  color: #c5a376;
}
.cover-category {
  position: relative;
  z-index: 1;
  font-size: 10px;
  border: 1px solid currentColor;
  border-radius: 4px;
  padding: 4px 7px;
}
.cover-symbol {
  position: absolute;
  right: 30px;
  top: 22px;
  font-size: 68px;
  transform: rotate(-12deg);
  opacity: 0.8;
}
.cover-line {
  width: 150px;
  height: 150px;
  border: 1px solid currentColor;
  border-radius: 50%;
  position: absolute;
  right: -16px;
  top: -18px;
  opacity: 0.15;
}
.cover-line::after {
  content: '';
  position: absolute;
  inset: -22px;
  border: 1px solid currentColor;
  border-radius: 50%;
}
.comp-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  flex: 1;
}
.comp-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}
.comp-level {
  font-size: 10px;
  color: #8793a7;
}
.comp-body h3 {
  font-size: 15px;
  line-height: 1.65;
  margin-bottom: 9px;
}
.comp-desc {
  font-size: 11px;
  color: #7b889f;
  line-height: 1.9;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 42px;
  margin-bottom: 18px;
}
.comp-meta {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  color: #7b889f;
  font-size: 10px;
  line-height: 1.7;
  margin-bottom: 8px;
}
.comp-meta .el-icon {
  margin-top: 2px;
  flex-shrink: 0;
  font-size: 13px;
}
.comp-footer {
  border-top: 1px solid #edf0f5;
  padding-top: 15px;
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.comp-footer > span {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 10px;
  color: #7b889f;
}
.comp-footer .el-button {
  font-size: 11px;
  min-height: 22px;
}
.comp-footer .el-button .el-icon {
  margin-left: 4px;
}
.home-note {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 28px;
  font-size: 10px;
  color: #7c8aa2;
  line-height: 1.8;
}
.home-note a {
  color: #6281c8;
  text-decoration: none;
  white-space: nowrap;
}
@media (max-width: 1200px) {
  .quick-link {
    padding: 16px 12px;
    gap: 9px;
  }
  .quick-link small {
    font-size: 9px;
  }
  .quick-arrow {
    display: none;
  }
  .competition-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .hero-copy {
    padding: 28px;
  }
  .label-code {
    left: -15px;
  }
  .label-team {
    right: 12px;
  }
}
@media (max-width: 900px) {
  .hero {
    grid-template-columns: 1.5fr 1fr;
  }
  .floating-label {
    display: none;
  }
  .hero-copy {
    padding: 25px;
  }
  .hero-actions .el-button {
    padding: 10px 12px;
  }
  .quick-icon {
    width: 34px;
    height: 34px;
    font-size: 19px;
  }
  .quick-link strong {
    font-size: 11px;
  }
}
@media (max-width: 767px) {
  .hero-actions {
    flex-wrap: wrap;
  }
  .today-label {
    display: none;
  }
  .hero {
    display: block;
  }
  .hero-copy {
    padding: 26px 22px;
  }
  .hero h2 {
    font-size: 28px;
  }
  .hero-art {
    display: none;
  }
  .hero-copy > p {
    max-width: 270px;
    line-height: 2;
  }
  .quick-links {
    grid-template-columns: 1fr;
    gap: 10px;
    margin: 18px 0 28px;
  }
  .quick-link {
    padding: 14px 16px;
    gap: 14px;
  }
  .quick-link strong {
    font-size: 13px;
  }
  .quick-link small {
    font-size: 10px;
  }
  .quick-arrow {
    display: block;
  }
  .competition-grid {
    grid-template-columns: 1fr;
  }
  .section-heading h2 {
    font-size: 17px;
  }
  .filter-card .search-input {
    flex: auto;
  }
  .home-note {
    flex-wrap: wrap;
    text-align: center;
  }
  .home-note > .el-icon {
    display: none;
  }
}
</style>
