<template>
  <div v-loading="loading">
    <PageHeading
      title="每一份努力，都值得被看见"
      description="记录赛场上的高光时刻，也为同行伙伴的成长喝彩。"
      eyebrow="CELEBRATE THE JOURNEY"
    />
    <el-card shadow="never" class="block">
      <template #header>
        <div class="card-head">
          <span><UiLabel icon="Trophy">荣誉成果</UiLabel></span>
          <el-button
            v-if="store.isLogin"
            type="primary"
            size="small"
            @click="openAdd"
            >＋ 发布成果</el-button
          >
        </div>
      </template>
      <el-row :gutter="16">
        <el-col
          :xs="24"
          :sm="12"
          :lg="8"
          v-for="a in list"
          :key="a.id"
          style="margin-bottom: 16px"
        >
          <el-card shadow="hover">
            <div class="ach-head">
              <span class="ach-name">{{ a.name }}</span>
              <el-tag :type="levelType(a.level)" size="small">{{
                a.level
              }}</el-tag>
            </div>
            <div class="ach-meta">
              <el-icon class="text-icon" aria-hidden="true"><Medal /></el-icon>
              {{ a.award }}
            </div>
            <div class="ach-meta">
              <el-icon class="text-icon" aria-hidden="true"><Aim /></el-icon>
              {{ a.competitionName }} · {{ a.awardYear || '—' }}
            </div>
            <div class="ach-meta" v-if="a.proof">
              <el-icon class="text-icon" aria-hidden="true"
                ><Document
              /></el-icon>
              <el-link
                type="primary"
                :href="a.proof"
                target="_blank"
                :underline="false"
                >查看证明</el-link
              >
            </div>
            <div class="ach-foot">
              <span class="ach-user">
                <span
                  v-if="a.teamName && a.teamId"
                  class="ach-link"
                  @click="$router.push(`/teams/${a.teamId}`)"
                  ><el-icon class="text-icon" aria-hidden="true"
                    ><Trophy
                  /></el-icon>
                  {{ a.teamName }}</span
                >
                <template v-if="a.teamName && a.userName"> · </template>
                <span
                  class="ach-link"
                  @click="$router.push(`/users/${a.userId}`)"
                  ><el-icon class="text-icon" aria-hidden="true"
                    ><User
                  /></el-icon>
                  {{ a.userName }}</span
                >
                <span> · {{ a.createdAt }}</span>
              </span>
              <el-button
                v-if="store.isAdmin"
                type="danger"
                size="small"
                link
                @click="remove(a)"
                >删除</el-button
              >
            </div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-if="!list.length" description="暂无已通过的成果" />
      <el-pagination
        v-if="total > pageSize"
        layout="prev, pager, next, total"
        :total="total"
        :page-size="pageSize"
        :current-page="page"
        style="justify-content: center; margin-top: 8px"
        @current-change="onPageChange"
      />
    </el-card>

    <el-card v-if="store.isLogin" shadow="never" class="block">
      <template #header
        ><UiLabel icon="DocumentChecked">我的成果 · 审核进度</UiLabel></template
      >
      <el-table :data="myList" size="small" v-loading="myLoading">
        <el-table-column prop="name" label="成果名称" min-width="180" />
        <el-table-column label="所在队伍" width="150">
          <template #default="{ row }">{{
            row.teamName || row.userName
          }}</template>
        </el-table-column>
        <el-table-column prop="competitionName" label="竞赛" width="150" />
        <el-table-column prop="award" label="获奖情况" min-width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag
              :type="ACH_STATUS_TYPE[row.status] || 'info'"
              size="small"
              >{{ row.status }}</el-tag
            >
          </template>
        </el-table-column>
        <el-table-column label="证明" width="90">
          <template #default="{ row }">
            <el-link
              type="primary"
              :href="row.proof"
              target="_blank"
              :underline="false"
              >查看</el-link
            >
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="提交时间" width="170">
          <template #default="{ row }">{{ row.createdAt }}</template>
        </el-table-column>
      </el-table>
      <el-empty
        v-if="!myList.length"
        description="还没有提交过成果"
        :image-size="60"
      />
    </el-card>

    <el-dialog
      v-model="addVisible"
      title="发布成果（需管理员审核）"
      width="480px"
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="成果需上传证明文件（证书/奖状照片等），经管理员审核通过后才会公开展示"
        style="margin-bottom: 12px"
      />
      <el-form :model="form" label-width="80px">
        <el-form-item label="成果名称" required>
          <el-input
            v-model="form.name"
            placeholder="如：全国大学生数学建模竞赛省二等奖"
          />
        </el-form-item>
        <el-form-item label="所属竞赛">
          <el-select
            v-model="form.competitionId"
            clearable
            placeholder="选择竞赛"
            style="width: 100%"
          >
            <el-option
              v-for="c in competitions"
              :key="c.id"
              :label="c.name"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="获奖队伍">
          <el-select
            v-model="form.teamId"
            placeholder="选择获奖队伍或个人奖项"
            style="width: 100%"
          >
            <el-option :value="null" label="个人奖项（不关联队伍）" />
            <el-option
              v-for="t in myTeams"
              :key="t.id"
              :label="`${t.title}（${t.competitionName}）`"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="form.level" style="width: 100%">
            <el-option v-for="l in LEVELS" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item label="获奖情况" required>
          <el-input
            v-model="form.award"
            placeholder="如：省级二等奖 / 国赛铜奖"
          />
        </el-form-item>
        <el-form-item label="获奖年份">
          <el-input-number v-model="form.awardYear" :min="2015" :max="2030" />
        </el-form-item>
        <el-form-item label="证明文件" required>
          <el-upload
            :show-file-list="false"
            :http-request="uploadProof"
            :before-upload="beforeProof"
          >
            <el-button size="small" :loading="proofUploading">{{
              form.proof ? '已上传，点击重新上传' : '＋ 上传证明（图片/PDF）'
            }}</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="!form.proof"
          @click="submit"
          >提交审核</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { useUserStore } from '../stores/user'
import { LEVELS } from '../utils/constants'

const ACH_STATUS_TYPE = {
  待审核: 'warning',
  已通过: 'success',
  已拒绝: 'danger'
}

const store = useUserStore()
const loading = ref(false)
const list = ref([])
const page = ref(1)
const pageSize = 9
const total = ref(0)
const myList = ref([])
const myLoading = ref(false)
const competitions = ref([])
const myTeams = ref([])
const addVisible = ref(false)
const saving = ref(false)
const proofUploading = ref(false)
const form = reactive({
  competitionId: null,
  teamId: null,
  name: '',
  level: '校级',
  award: '',
  awardYear: new Date().getFullYear(),
  proof: ''
})

function levelType(level) {
  return { 校级: 'info', 省级: 'warning', 国家级: 'success' }[level] || 'info'
}

async function load() {
  loading.value = true
  try {
    const data = await api.get('/achievements', {
      params: { page: page.value, size: pageSize }
    })
    list.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function onPageChange(p) {
  page.value = p
  load()
}

async function loadMy() {
  myLoading.value = true
  try {
    const data = await api.get('/achievements/my', {
      params: { page: 1, size: 50 }
    })
    myList.value = data.records || []
  } finally {
    myLoading.value = false
  }
}

async function loadCompetitions() {
  competitions.value = await api.get('/competitions')
}

async function loadMyTeams() {
  try {
    myTeams.value = await api.get('/user/me/teams')
  } catch (e) {
    myTeams.value = []
  }
}

function openAdd() {
  form.competitionId = null
  form.teamId = myTeams.value.length === 1 ? myTeams.value[0].id : null
  form.name = ''
  form.level = '校级'
  form.award = ''
  form.proof = ''
  addVisible.value = true
}

function beforeProof(file) {
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.warning('文件不能超过 20MB')
    return false
  }
  return true
}

async function uploadProof({ file }) {
  proofUploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    const data = await api.post('/files/upload', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    form.proof = data.url
    ElMessage.success('证明文件已上传')
  } finally {
    proofUploading.value = false
  }
}

async function submit() {
  if (!form.name || !form.award) {
    ElMessage.warning('请填写成果名称和获奖情况')
    return
  }
  if (!form.proof) {
    ElMessage.warning('请上传证明文件')
    return
  }
  saving.value = true
  try {
    await api.post('/achievements', form)
    ElMessage.success('已提交审核，管理员通过后将公开展示')
    addVisible.value = false
    Object.assign(form, {
      competitionId: null,
      teamId: null,
      name: '',
      level: '校级',
      award: '',
      proof: ''
    })
    load()
    loadMy()
  } finally {
    saving.value = false
  }
}

async function remove(a) {
  await ElMessageBox.confirm(`确定删除成果「${a.name}」吗？`, '删除确认', {
    type: 'warning'
  })
  await api.delete(`/achievements/${a.id}`)
  ElMessage.success('已删除')
  load()
}

onMounted(() => {
  load()
  if (store.isLogin) {
    loadCompetitions()
    loadMyTeams()
    loadMy()
  }
})
</script>

<style scoped>
.block {
  margin-bottom: 16px;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
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
.ach-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
  border-top: 1px solid #f0f2f5;
  padding-top: 8px;
}
.ach-user {
  color: #909399;
  font-size: 12px;
}
.ach-link {
  color: #409eff;
  cursor: pointer;
}
.ach-link:hover {
  text-decoration: underline;
}
</style>
