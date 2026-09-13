<template>
  <div>
    <PageHeading
      title="让平台更懂你的需要"
      description="分享建议、反馈问题，或告诉我们你期待参加的赛事。"
      eyebrow="WE ARE LISTENING"
    />
    <el-card shadow="never">
      <template #header><UiLabel icon="Message">提交反馈</UiLabel></template>
      <el-form :model="form" label-width="80px" style="max-width: 620px">
        <el-form-item label="反馈类型">
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="赛事申请（平台没有的竞赛）" value="赛事申请" />
            <el-option label="功能建议" value="功能建议" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="反馈内容">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="4"
            placeholder="例如：想参加「全国大学生服务外包创新创业大赛」，平台里没有这个赛事，希望管理员添加"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit"
            >提交反馈</el-button
          >
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>我的反馈记录</template>
      <el-table :data="myList" size="small">
        <el-table-column prop="type" label="类型" width="110" />
        <el-table-column prop="content" label="内容" min-width="240" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag
              :type="row.status === '待处理' ? 'warning' : 'success'"
              size="small"
              >{{ row.status }}</el-tag
            >
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="提交时间" width="170" />
      </el-table>
      <el-empty
        v-if="!myList.length"
        description="暂无反馈记录"
        :image-size="60"
      />
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const form = reactive({ type: '赛事申请', content: '' })
const submitting = ref(false)
const myList = ref([])
const page = ref(1)
const pageSize = 10
const total = ref(0)

async function load() {
  const data = await api.get('/feedback/my', {
    params: { page: page.value, size: pageSize }
  })
  myList.value = data.records || []
  total.value = data.total || 0
}

function onPageChange(p) {
  page.value = p
  load()
}

async function submit() {
  if (!form.content.trim()) {
    ElMessage.warning('请填写反馈内容')
    return
  }
  submitting.value = true
  try {
    await api.post('/feedback', form)
    ElMessage.success('反馈已提交，管理员处理后会更新状态')
    form.content = ''
    load()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>
