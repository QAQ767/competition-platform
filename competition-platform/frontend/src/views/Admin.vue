<template>
  <div>
    <el-card shadow="never">
      <el-tabs v-model="tab">
        <el-tab-pane label="竞赛管理" name="competition">
          <div class="card-head">
            <el-button type="primary" size="small" @click="addVisible = true">＋ 新增竞赛</el-button>
          </div>
          <el-table :data="list" size="small" v-loading="loading" style="margin-top: 12px">
            <el-table-column prop="name" label="竞赛名称" min-width="160" />
            <el-table-column prop="organizer" label="主办方" min-width="140" />
            <el-table-column label="级别" width="80">
              <template #default="{ row }"><el-tag size="small">{{ row.level }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="signupStart" label="报名开始" width="160" />
            <el-table-column prop="signupEnd" label="报名截止" width="160" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="{ '报名中': 'success', '即将开始': 'warning', '已结束': 'info' }[row.status]" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button type="danger" size="small" link @click="remove(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="反馈管理" name="feedback">
          <el-table :data="feedbacks" size="small" v-loading="fbLoading">
            <el-table-column prop="userName" label="提交人" width="90" />
            <el-table-column prop="type" label="类型" width="100" />
            <el-table-column prop="content" label="内容" min-width="260" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === '待处理' ? 'warning' : 'success'" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="提交时间" width="170" />
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === '待处理'"
                  type="success"
                  size="small"
                  link
                  @click="handleFeedback(row)"
                >标记已处理</el-button>
                <span v-else style="color: #909399; font-size: 12px">已处理</span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!feedbacks.length" description="暂无反馈" :image-size="60" />
          <el-pagination
            v-if="fbTotal > fbPageSize"
            layout="prev, pager, next, total"
            :total="fbTotal"
            :page-size="fbPageSize"
            :current-page="fbPage"
            style="justify-content: center; margin-top: 8px"
            @current-change="onFbPageChange"
          />
        </el-tab-pane>

        <el-tab-pane label="操作日志" name="logs">
          <el-table :data="logs" size="small" v-loading="logLoading">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="userName" label="操作人" width="90" />
            <el-table-column prop="action" label="动作" width="110" />
            <el-table-column prop="detail" label="详情" min-width="220" />
            <el-table-column prop="createTime" label="时间" width="170" />
          </el-table>
          <el-empty v-if="!logs.length" description="暂无操作日志" :image-size="60" />
          <el-pagination
            v-if="logTotal > logPageSize"
            layout="prev, pager, next, total"
            :total="logTotal"
            :page-size="logPageSize"
            :current-page="logPage"
            style="justify-content: center; margin-top: 8px"
            @current-change="onLogPageChange"
          />
        </el-tab-pane>

        <el-tab-pane label="成果审核" name="achievement">
          <div style="margin-bottom: 12px">
            <el-radio-group v-model="achStatus" size="small" @change="loadAchievements">
              <el-radio-button label="待审核">待审核</el-radio-button>
              <el-radio-button label="已通过">已通过</el-radio-button>
              <el-radio-button label="已拒绝">已拒绝</el-radio-button>
            </el-radio-group>
          </div>
          <el-table :data="achList" size="small" v-loading="achLoading">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="userName" label="发布人" width="90" />
            <el-table-column prop="name" label="成果名称" min-width="150" />
            <el-table-column prop="competitionName" label="竞赛" width="130" />
            <el-table-column prop="award" label="获奖情况" min-width="110" />
            <el-table-column label="证明" width="90">
              <template #default="{ row }">
                <el-link type="primary" :href="row.proof" target="_blank" :underline="false">查看</el-link>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <template v-if="row.status === '待审核'">
                  <el-button type="success" size="small" link @click="reviewAchievement(row, 'approve')">通过</el-button>
                  <el-button type="danger" size="small" link @click="reviewAchievement(row, 'reject')">拒绝</el-button>
                </template>
                <el-button v-else type="danger" size="small" link @click="removeAchievement(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!achList.length" description="暂无成果" :image-size="60" />
          <el-pagination
            v-if="achTotal > achPageSize"
            layout="prev, pager, next, total"
            :total="achTotal"
            :page-size="achPageSize"
            :current-page="achPage"
            style="justify-content: center; margin-top: 8px"
            @current-change="onAchPageChange"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="addVisible" title="新增竞赛" width="520px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="主办方"><el-input v-model="form.organizer" /></el-form-item>
        <el-form-item label="级别">
          <el-select v-model="form.level" style="width: 100%">
            <el-option v-for="l in LEVELS" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item label="报名开始"><el-date-picker v-model="form.signupStart" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" /></el-form-item>
        <el-form-item label="报名截止"><el-date-picker v-model="form.signupEnd" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" /></el-form-item>
        <el-form-item label="比赛时间"><el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doAdd">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { LEVELS } from '../utils/constants'

const tab = ref('competition')
const list = ref([])
const loading = ref(false)
const addVisible = ref(false)
const saving = ref(false)
const feedbacks = ref([])
const fbLoading = ref(false)
const fbPage = ref(1)
const fbPageSize = 10
const fbTotal = ref(0)
const logs = ref([])
const logLoading = ref(false)
const logPage = ref(1)
const logPageSize = 10
const logTotal = ref(0)
const achStatus = ref('待审核')
const achList = ref([])
const achLoading = ref(false)
const achPage = ref(1)
const achPageSize = 10
const achTotal = ref(0)
const form = reactive({
  name: '',
  organizer: '',
  level: '校级',
  signupStart: '',
  signupEnd: '',
  startTime: '',
  description: ''
})

async function load() {
  loading.value = true
  try {
    list.value = await api.get('/competitions')
  } finally {
    loading.value = false
  }
}

async function loadFeedback() {
  fbLoading.value = true
  try {
    const data = await api.get('/feedback', { params: { page: fbPage.value, size: fbPageSize } })
    feedbacks.value = data.records || []
    fbTotal.value = data.total || 0
  } finally {
    fbLoading.value = false
  }
}

function onFbPageChange(p) {
  fbPage.value = p
  loadFeedback()
}

async function loadLogs() {
  logLoading.value = true
  try {
    const data = await api.get('/admin/logs', { params: { page: logPage.value, size: logPageSize } })
    logs.value = data.records || []
    logTotal.value = data.total || 0
  } finally {
    logLoading.value = false
  }
}

function onLogPageChange(p) {
  logPage.value = p
  loadLogs()
}

async function loadAchievements() {
  achLoading.value = true
  try {
    const data = await api.get('/achievements/admin', {
      params: { status: achStatus.value, page: achPage.value, size: achPageSize }
    })
    achList.value = data.records || []
    achTotal.value = data.total || 0
  } finally {
    achLoading.value = false
  }
}

function onAchPageChange(p) {
  achPage.value = p
  loadAchievements()
}

async function reviewAchievement(row, action) {
  await ElMessageBox.confirm(
    `确定${action === 'approve' ? '通过' : '拒绝'}成果「${row.name}」的审核吗？`,
    '成果审核',
    { type: 'warning' }
  )
  await api.post(`/achievements/admin/${row.id}/${action}`)
  ElMessage.success(action === 'approve' ? '已通过，将公开展示' : '已拒绝')
  loadAchievements()
}

async function removeAchievement(row) {
  await ElMessageBox.confirm(`确定删除成果「${row.name}」吗？`, '删除确认', { type: 'warning' })
  await api.delete(`/achievements/${row.id}`)
  ElMessage.success('已删除')
  loadAchievements()
}

async function doAdd() {
  if (!form.name) {
    ElMessage.warning('请填写竞赛名称')
    return
  }
  saving.value = true
  try {
    await api.post('/competitions', form)
    ElMessage.success('竞赛已添加')
    addVisible.value = false
    Object.keys(form).forEach((k) => (form[k] = ''))
    form.level = '校级'
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除竞赛「${row.name}」吗？`, '删除确认', { type: 'warning' })
  await api.delete(`/competitions/${row.id}`)
  ElMessage.success('已删除')
  load()
}

async function handleFeedback(row) {
  await ElMessageBox.confirm(`确认将「${row.content.slice(0, 20)}...」标记为已处理吗？`, '处理反馈', { type: 'info' })
  await api.post(`/feedback/${row.id}/handle`)
  ElMessage.success('已标记为处理完成')
  loadFeedback()
}

onMounted(() => {
  load()
  loadFeedback()
  loadLogs()
  loadAchievements()
})
</script>

<style scoped>
.card-head {
  display: flex;
  justify-content: flex-end;
}
</style>
