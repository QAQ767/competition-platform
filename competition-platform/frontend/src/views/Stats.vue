<template>
  <div v-loading="loading">
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-num" :style="{ color: card.color }">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>🔥 竞赛热度排行（组队数量 Top 5）</template>
      <div ref="chartRef" style="height: 360px"></div>
      <el-empty v-if="!stats?.hotCompetitions?.length" description="暂无数据" :image-size="60" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import api from '../api'

const loading = ref(false)
const stats = ref(null)
const chartRef = ref(null)
let chart = null

const cards = computed(() => [
  { label: '平台用户数', value: stats.value?.userCount ?? '-', color: '#409eff' },
  { label: '队伍总数', value: stats.value?.teamCount ?? '-', color: '#67c23a' },
  { label: '竞赛总数', value: stats.value?.competitionCount ?? '-', color: '#e6a23c' },
  { label: '获奖记录数', value: stats.value?.achievementCount ?? '-', color: '#f56c6c' }
])

function renderChart() {
  const hot = stats.value?.hotCompetitions || []
  chart = echarts.init(chartRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 20, bottom: 40 },
    xAxis: {
      type: 'category',
      data: hot.map((h) => h.name),
      axisLabel: { interval: 0, rotate: hot.length > 3 ? 15 : 0 }
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        type: 'bar',
        data: hot.map((h) => h.teamCount),
        barWidth: 46,
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#409eff' },
            { offset: 1, color: '#a0cfff' }
          ])
        },
        label: { show: true, position: 'top' }
      }
    ]
  })
}

function onResize() {
  chart && chart.resize()
}

async function load() {
  loading.value = true
  try {
    stats.value = await api.get('/stats/overview')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await load()
  if (stats.value?.hotCompetitions?.length) {
    renderChart()
  }
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart && chart.dispose()
})
</script>

<style scoped>
.stat-card {
  text-align: center;
}
.stat-num {
  font-size: 30px;
  font-weight: 700;
}
.stat-label {
  color: #909399;
  font-size: 13px;
  margin-top: 6px;
}
</style>
