<template>
  <div v-loading="loading">
    <PageHeading
      title="赛事详情"
      description="核对报名时间、参赛条件与来源，再决定是否组队或申请。"
      eyebrow="COMPETITION DETAIL"
    >
      <el-button size="small" @click="router.push('/advice')">回到参赛推荐</el-button>
    </PageHeading>

    <el-result
      v-if="failed"
      icon="warning"
      title="赛事加载失败"
      sub-title="赛事可能已被删除，或网络暂时不可用。"
    >
      <template #extra>
        <el-button @click="load">重试</el-button>
        <el-button @click="router.push('/')">返回首页</el-button>
      </template>
    </el-result>

    <template v-if="competition">
      <el-card shadow="never" class="block">
        <div class="detail-head">
          <div>
            <div class="tags">
              <el-tag size="small" :type="COMPETITION_STATUS_TYPE[competition.status] || 'info'">
                {{ competitionStatusLabel(competition.status) }}
              </el-tag>
              <el-tag v-if="competition.level" size="small" effect="plain">{{ competition.level }}</el-tag>
              <el-tag v-if="competition.competitionYear" size="small" effect="plain">{{ competition.competitionYear }} 年</el-tag>
            </div>
            <h2>{{ competition.name }}</h2>
            <p class="organizer">{{ competition.organizer || '主办方待公布' }}</p>
          </div>
          <el-button type="primary" @click="router.push({ path: '/teams', query: { competitionId: competition.id } })">
            查看该赛事队伍
          </el-button>
        </div>

        <el-descriptions :column="2" size="small" class="dates">
          <el-descriptions-item label="报名开始">{{ formatDateTime(competition.signupStart) }}</el-descriptions-item>
          <el-descriptions-item label="报名截止">{{ formatDateTime(competition.signupEnd) }}</el-descriptions-item>
          <el-descriptions-item label="开赛时间">{{ formatDateTime(competition.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="届次 / 赛道">
            {{ competition.edition || '未注明' }} / {{ competition.track || '未注明' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="competition.status === '已结束'"
          class="inner-alert"
          type="info"
          :closable="false"
          show-icon
          title="本期报名已截止"
          description="这只表示报名截止，不代表整场比赛已经结束。"
        />

        <div v-if="competition.tags" class="chip-line">
          <span class="label">方向标签</span>
          <el-tag v-for="tag in tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
        </div>

        <section class="section">
          <h3>参赛条件</h3>
          <p v-if="competition.eligibilityText" class="body-text">{{ competition.eligibilityText }}</p>
          <el-alert v-else type="warning" :closable="false" show-icon title="平台尚未维护参赛条件，报名前请以官方公告为准" />
        </section>

        <section class="section">
          <h3>赛事简介</h3>
          <p class="body-text">{{ competition.description || '暂无简介' }}</p>
        </section>

        <div v-if="competition.sourceUrl" class="source">
          <el-icon><Link /></el-icon>
          <span>来源：</span>
          <el-link type="primary" :href="competition.sourceUrl" target="_blank" rel="noopener noreferrer">
            {{ competition.sourceUrl }}
          </el-link>
        </div>
      </el-card>

      <el-card shadow="never" class="block">
        <template #header>
          <div class="teams-header"><span>该赛事下的队伍（{{ teams.length }}）</span><span class="hint">仅展示真实队伍，申请仍需队长审批</span></div>
        </template>
        <div v-if="teams.length" class="team-grid">
          <el-card v-for="team in teams" :key="team.id" shadow="hover" class="team-card">
            <div class="team-top">
              <h4>{{ team.title }}</h4>
              <el-tag size="small" :type="TEAM_STATUS_TYPE[team.status] || 'info'">{{ team.status }}</el-tag>
            </div>
            <p class="team-meta">队长：{{ team.captainName || '未知' }} · 成员 {{ team.memberCount }} / {{ team.maxMembers }}</p>
            <p class="team-meta">招募截止：{{ formatDateTime(team.deadline) }}</p>
            <div class="chip-line">
              <el-tag v-for="skill in team.skills || []" :key="skill" size="small" type="warning" effect="plain">{{ skill }}</el-tag>
            </div>
            <div class="team-actions">
              <el-button link type="primary" size="small" @click="router.push(`/teams/${team.id}`)">查看队伍</el-button>
              <el-button
                v-if="team.status === '招募中'"
                link
                size="small"
                @click="router.push(`/teams/${team.id}?apply=1`)"
              >申请加入</el-button>
            </div>
          </el-card>
        </div>
        <el-empty v-else description="暂时没有该赛事的队伍" :image-size="60" />
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Link } from '@element-plus/icons-vue'
import api from '../api'
import PageHeading from '../components/PageHeading.vue'
import { TEAM_STATUS_TYPE } from '../utils/constants'
import {
  COMPETITION_STATUS_TYPE,
  competitionStatusLabel
} from '../utils/competitionStatus'
import { formatDateTime } from '../utils/advice'

const route = useRoute()
const router = useRouter()
const competition = ref(null)
const teams = ref([])
const loading = ref(false)
const failed = ref(false)

const tags = computed(() =>
  (competition.value?.tags || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
)

async function load() {
  loading.value = true
  failed.value = false
  try {
    competition.value = await api.get(`/competitions/${route.params.id}`)
    const data = await api.get('/teams', {
      params: { competitionId: route.params.id, page: 1, size: 12 }
    })
    teams.value = data.records || []
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.detail-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
  flex-wrap: wrap;
}
.detail-head h2 {
  font-size: 24px;
  margin: 12px 0 6px;
  line-height: 1.5;
}
.organizer {
  color: #75839b;
  font-size: 12px;
  margin: 0;
}
.tags {
  display: flex;
  gap: 7px;
  flex-wrap: wrap;
}
.dates {
  margin-top: 22px;
}
.inner-alert {
  margin-top: 14px;
}
.chip-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}
.label {
  color: #8b97aa;
  font-size: 11px;
}
.section {
  margin-top: 24px;
}
.section h3 {
  font-size: 15px;
  margin-bottom: 8px;
}
.body-text {
  white-space: pre-wrap;
  color: #42526e;
  font-size: 12px;
  line-height: 1.9;
  margin: 0;
}
.source {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 22px;
  padding-top: 14px;
  border-top: 1px solid #edf0f5;
  color: #8b97aa;
  font-size: 11px;
  word-break: break-all;
}
.teams-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.hint {
  color: #95a0b2;
  font-size: 11px;
}
.team-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}
.team-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: flex-start;
}
.team-top h4 {
  font-size: 14px;
  margin: 0;
  line-height: 1.6;
}
.team-meta {
  color: #7b889f;
  font-size: 11px;
  margin: 8px 0 0;
}
.team-actions {
  display: flex;
  gap: 10px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid #edf0f5;
}
@media (max-width: 1000px) {
  .team-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 700px) {
  .team-grid {
    grid-template-columns: 1fr;
  }
}
</style>
