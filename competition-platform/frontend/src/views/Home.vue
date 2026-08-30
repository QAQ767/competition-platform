<template>
  <div>
    <el-card shadow="never" class="filter-card">
      <el-input v-model="keyword" placeholder="搜索竞赛名称 / 简介" clearable style="width: 260px" @keyup.enter="load" />
      <el-select v-model="level" placeholder="级别" clearable style="width: 120px; margin-left: 10px">
        <el-option v-for="l in LEVELS" :key="l" :label="l" :value="l" />
      </el-select>
      <el-select v-model="status" placeholder="状态" clearable style="width: 120px; margin-left: 10px">
        <el-option label="报名中" value="报名中" />
        <el-option label="即将开始" value="即将开始" />
        <el-option label="已结束" value="已结束" />
      </el-select>
      <el-button type="primary" style="margin-left: 10px" @click="load">查询</el-button>
    </el-card>

    <el-row :gutter="16">
      <el-col :span="8" v-for="c in list" :key="c.id" style="margin-bottom: 16px">
        <el-card shadow="hover">
          <div class="comp-head">
            <span class="comp-name">{{ c.name }}</span>
            <el-tag :type="statusType(c.status)" size="small">{{ c.status }}</el-tag>
          </div>
          <div class="comp-meta">主办方：{{ c.organizer }}</div>
          <div class="comp-meta">级别：<el-tag size="small">{{ c.level }}</el-tag></div>
          <div class="comp-meta">报名截止：{{ c.signupEnd }}</div>
          <div class="comp-desc">{{ c.description }}</div>
          <el-button v-if="c.status !== '已结束'" type="primary" size="small" @click="goTeam(c.id)">为该竞赛组队</el-button>
        </el-card>
      </el-col>
    </el-row>
    <el-empty v-if="!list.length" description="暂无竞赛" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'
import { LEVELS } from '../utils/constants'

const router = useRouter()
const keyword = ref('')
const level = ref('')
const status = ref('')
const list = ref([])

function statusType(s) {
  return { '报名中': 'success', '即将开始': 'warning', '已结束': 'info' }[s] || 'info'
}

async function load() {
  const params = {}
  if (keyword.value) params.keyword = keyword.value
  if (level.value) params.level = level.value
  if (status.value) params.status = status.value
  list.value = await api.get('/competitions', { params })
}

function goTeam(id) {
  router.push({ path: '/teams', query: { competitionId: id } })
}

onMounted(load)
</script>

<style scoped>
.filter-card {
  margin-bottom: 16px;
}
.comp-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.comp-name {
  font-size: 16px;
  font-weight: 600;
}
.comp-meta {
  color: #606266;
  font-size: 13px;
  margin-top: 6px;
}
.comp-desc {
  color: #909399;
  font-size: 13px;
  margin: 8px 0 12px;
  min-height: 38px;
}
</style>
