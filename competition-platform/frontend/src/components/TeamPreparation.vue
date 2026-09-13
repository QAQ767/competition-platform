<template>
  <section class="preparation" v-loading="loading">
    <div class="plan-heading">
      <div>
        <span class="eyebrow">PREPARE · PRACTICE · ACHIEVE</span>
        <h2>每一步准备，都离目标更近</h2>
        <p>明确分工，追踪进度，把训练与复盘留在同一个地方。</p>
      </div>
      <div v-if="isCaptain" class="plan-actions">
        <el-button
          v-if="!tasks.length && !failed"
          :icon="MagicStick"
          :disabled="loading"
          @click="planVisible = true"
          >生成阶段计划</el-button
        ><el-button type="primary" :icon="Plus" @click="openEditor()"
          >新建任务</el-button
        >
      </div>
    </div>
    <el-result
      v-if="failed"
      icon="warning"
      title="备赛计划加载失败"
      sub-title="请稍后重试。"
      ><template #extra
        ><el-button @click="load">重新加载</el-button></template
      ></el-result
    >
    <template v-else>
      <div class="plan-metrics">
        <div>
          <span>整体完成度</span><strong>{{ progress }}<small>%</small></strong
          ><el-progress
            :percentage="progress"
            :show-text="false"
            :stroke-width="4"
          />
        </div>
        <div>
          <span>已完成 / 全部任务</span
          ><strong
            >{{ completed }}<small> / {{ tasks.length }}</small></strong
          >
        </div>
        <div>
          <span>未来 3 天待办</span><strong>{{ dueSoon }}</strong>
        </div>
        <div :class="{ urgent: overdueCount > 0 }">
          <span>逾期待处理</span><strong>{{ overdueCount }}</strong>
        </div>
      </div>
      <el-alert
        v-if="overdueCount"
        type="warning"
        show-icon
        :closable="false"
        :title="`有 ${overdueCount} 项任务已逾期，请及时沟通进度或调整截止时间。`"
        class="deadline-alert"
      />
      <div class="phase-strip">
        <button
          v-for="(phase, index) in PHASES"
          :key="phase"
          class="phase-item"
          :class="{ active: phaseFilter === phase }"
          :aria-pressed="phaseFilter === phase"
          @click="phaseFilter = phaseFilter === phase ? '' : phase"
        >
          <span class="phase-number">0{{ index + 1 }}</span
          ><span
            ><strong>{{ phase }}</strong
            ><small
              >{{ phaseCount(phase, true) }} /
              {{ phaseCount(phase) }} 项完成</small
            ></span
          ><el-icon
            v-if="
              phaseCount(phase) && phaseCount(phase, true) === phaseCount(phase)
            "
            ><CircleCheck
          /></el-icon>
        </button>
      </div>
      <div class="plan-filters">
        <el-input
          v-model="keyword"
          :prefix-icon="Search"
          placeholder="搜索任务或负责人"
          aria-label="搜索备赛任务"
          clearable
        /><el-checkbox v-model="onlyMine">只看我的任务</el-checkbox
        ><el-checkbox v-model="onlyOverdue">只看逾期</el-checkbox
        ><el-button
          v-if="keyword || onlyMine || onlyOverdue || phaseFilter"
          link
          type="primary"
          @click="resetFilters"
          >清除筛选</el-button
        >
      </div>
      <el-empty v-if="!tasks.length" description="还没有备赛任务"
        ><p class="empty-tip">
          {{
            isCaptain
              ? '生成四阶段计划，或从第一项任务开始。'
              : '等待队长安排任务，一起开启备赛。'
          }}
        </p></el-empty
      >
      <el-empty v-else-if="!filtered.length" description="没有符合条件的任务"
        ><el-button @click="resetFilters">清除筛选</el-button></el-empty
      >
      <div v-else class="task-board">
        <div v-for="column in columns" :key="column.status" class="task-column">
          <div class="column-heading">
            <span class="status-dot" :class="column.color"></span>
            <h3>{{ column.status }}</h3>
            <span class="column-count">{{
              filtered.filter((t) => t.status === column.status).length
            }}</span>
          </div>
          <article
            v-for="task in filtered.filter((t) => t.status === column.status)"
            :key="task.id"
            class="task-item"
            :class="{ overdue: isOverdue(task) }"
            :data-task-id="task.id"
          >
            <div class="task-tags">
              <el-tag size="small" effect="plain" type="info">{{
                task.phase
              }}</el-tag
              ><el-tag
                v-if="task.priority !== '普通'"
                size="small"
                :type="task.priority === '紧急' ? 'danger' : 'warning'"
                >{{ task.priority }}</el-tag
              >
            </div>
            <h4>{{ task.title }}</h4>
            <p v-if="task.content" class="task-content">{{ task.content }}</p>
            <div class="task-meta">
              <el-icon><User /></el-icon
              ><span
                >{{ task.assigneeName || '待分配'
                }}<small
                  v-if="
                    task.assigneeId &&
                    !members.some((m) => m.userId === task.assigneeId)
                  "
                >
                  · 已离队，请重新分配</small
                ></span
              >
            </div>
            <div class="task-meta" :class="{ 'overdue-text': isOverdue(task) }">
              <el-icon><Clock /></el-icon
              ><span
                >{{ formatDeadline(task.deadline)
                }}{{ isOverdue(task) ? ' · 已逾期' : '' }}</span
              >
            </div>
            <a
              v-if="task.resourceUrl"
              class="task-resource"
              :href="task.resourceUrl"
              target="_blank"
              rel="noopener noreferrer"
              ><el-icon><Document /></el-icon>{{ task.resourceName
              }}<el-icon><TopRight /></el-icon></a
            ><span v-else-if="task.resourceId" class="task-meta"
              >关联资料已移除</span
            >
            <div
              v-if="task.status === '已完成' && task.completionNote"
              class="task-review"
            >
              <span>完成复盘</span>
              <p>{{ task.completionNote }}</p>
            </div>
            <div v-if="task.completedAt" class="completed-time">
              完成于 {{ formatDeadline(task.completedAt) }}
            </div>
            <div class="task-actions">
              <template v-if="canOperate(task)"
                ><el-button
                  v-if="task.status === '待完成'"
                  link
                  type="primary"
                  :disabled="busy.has(task.id)"
                  @click="changeStatus(task, '进行中')"
                  >开始任务</el-button
                ><el-button
                  v-if="task.status !== '已完成'"
                  link
                  type="success"
                  :disabled="busy.has(task.id)"
                  @click="openComplete(task)"
                  >完成任务</el-button
                ><el-button
                  v-else
                  link
                  :disabled="busy.has(task.id)"
                  @click="changeStatus(task, '待完成')"
                  >重新打开</el-button
                ></template
              ><template v-if="isCaptain"
                ><el-button
                  link
                  :disabled="busy.has(task.id)"
                  @click="openEditor(task)"
                  >编辑</el-button
                ><el-button
                  link
                  type="danger"
                  :disabled="busy.has(task.id)"
                  @click="removeTask(task)"
                  >删除</el-button
                ></template
              >
            </div>
          </article>
          <div
            v-if="!filtered.some((t) => t.status === column.status)"
            class="column-empty"
          >
            暂无任务
          </div>
        </div>
      </div>
    </template>
    <el-dialog
      v-model="editorVisible"
      :title="editingId ? '编辑备赛任务' : '新建备赛任务'"
      width="580px"
      :close-on-click-modal="!saving"
      :close-on-press-escape="!saving"
      :show-close="!saving"
      ><el-form :model="form" label-position="top" :disabled="saving"
        ><el-form-item label="任务标题" required
          ><el-input
            v-model="form.title"
            maxlength="100"
            show-word-limit
            placeholder="例如：完成第一轮模拟训练" /></el-form-item
        ><el-form-item label="任务说明"
          ><el-input
            v-model="form.content"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="说明目标、产出和验收要求"
        /></el-form-item>
        <div class="form-grid">
          <el-form-item label="备赛阶段"
            ><el-select v-model="form.phase"
              ><el-option
                v-for="phase in PHASES"
                :key="phase"
                :value="phase"
                :label="phase" /></el-select></el-form-item
          ><el-form-item label="优先级"
            ><el-select v-model="form.priority"
              ><el-option
                v-for="priority in ['普通', '重要', '紧急']"
                :key="priority"
                :value="priority"
                :label="priority" /></el-select
          ></el-form-item>
        </div>
        <el-form-item label="负责人"
          ><el-select
            v-model="form.assigneeId"
            clearable
            placeholder="选择当前队员，可暂不分配"
            ><el-option
              v-for="member in members"
              :key="member.userId"
              :value="member.userId"
              :label="member.userName" /></el-select></el-form-item
        ><el-form-item label="截止时间"
          ><el-date-picker
            v-model="form.deadline"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="设置任务截止时间"
            style="width: 100%" /></el-form-item
        ><el-form-item label="关联训练资料"
          ><el-select
            v-model="form.resourceId"
            clearable
            placeholder="关联本队已上传的资料"
            ><el-option
              v-for="resource in resources"
              :key="resource.id"
              :value="resource.id"
              :label="resource.name" /></el-select></el-form-item></el-form
      ><template #footer
        ><el-button :disabled="saving" @click="editorVisible = false"
          >取消</el-button
        ><el-button type="primary" :loading="saving" @click="save">{{
          editingId ? '保存修改' : '创建任务'
        }}</el-button></template
      ></el-dialog
    >
    <el-dialog
      v-model="planVisible"
      title="生成四阶段备赛计划"
      width="460px"
      :close-on-click-modal="!generating"
      :show-close="!generating"
      ><p class="dialog-description">
        准备阶段 → 专项训练 → 模拟冲刺 →
        作品提交。系统按目标时间安排四项初始任务，生成后可逐项调整负责人和截止时间。
      </p>
      <el-form label-position="top"
        ><el-form-item label="备赛目标时间"
          ><el-date-picker
            v-model="targetDate"
            :disabled="generating"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="留空使用竞赛开赛时间"
            style="width: 100%" /></el-form-item
      ></el-form>
      <p class="empty-tip">
        目标时间需至少在一天后。已有任务的队伍请直接编辑现有计划。
      </p>
      <template #footer
        ><el-button :disabled="generating" @click="planVisible = false"
          >取消</el-button
        ><el-button type="primary" :loading="generating" @click="generate"
          >生成计划</el-button
        ></template
      ></el-dialog
    >
    <el-dialog
      v-model="completeVisible"
      title="完成任务，留下这次的收获"
      width="460px"
      :close-on-click-modal="!completing"
      :show-close="!completing"
      ><p>{{ completingTask?.title }}</p>
      <el-input
        v-model="completionNote"
        type="textarea"
        :rows="4"
        maxlength="1000"
        show-word-limit
        placeholder="可选：完成了什么、遇到哪些问题、下次如何改进"
        :disabled="completing"
      /><template #footer
        ><el-button :disabled="completing" @click="completeVisible = false"
          >取消</el-button
        ><el-button type="primary" :loading="completing" @click="complete"
          >确认完成</el-button
        ></template
      ></el-dialog
    >
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Plus, Search } from '@element-plus/icons-vue'
import api from '../api'
import { useUserStore } from '../stores/user'

const props = defineProps({
  teamId: { type: Number, required: true },
  isCaptain: Boolean,
  members: { type: Array, default: () => [] },
  resources: { type: Array, default: () => [] }
})
const store = useUserStore()
const PHASES = ['准备阶段', '专项训练', '模拟冲刺', '作品提交']
const columns = [
  { status: '待完成', color: 'pending' },
  { status: '进行中', color: 'working' },
  { status: '已完成', color: 'done' }
]
const tasks = ref([]),
  loading = ref(false),
  failed = ref(false),
  busy = reactive(new Set())
const keyword = ref(''),
  onlyMine = ref(false),
  onlyOverdue = ref(false),
  phaseFilter = ref('')
const now = ref(Date.now())
const timer = setInterval(() => {
  now.value = Date.now()
}, 60000)
let requestId = 0
onBeforeUnmount(() => {
  clearInterval(timer)
  requestId++
})
const deadlineTime = (value) =>
  value ? new Date(value.replace(' ', 'T')).getTime() : NaN
const isOverdue = (task) =>
  task.status !== '已完成' && deadlineTime(task.deadline) < now.value
const completed = computed(
  () => tasks.value.filter((t) => t.status === '已完成').length
)
const progress = computed(() =>
  tasks.value.length
    ? Math.round((completed.value / tasks.value.length) * 100)
    : 0
)
const overdueCount = computed(() => tasks.value.filter(isOverdue).length)
const dueSoon = computed(
  () =>
    tasks.value.filter(
      (t) =>
        t.status !== '已完成' &&
        deadlineTime(t.deadline) >= now.value &&
        deadlineTime(t.deadline) <= now.value + 3 * 86400000
    ).length
)
const filtered = computed(() =>
  tasks.value
    .filter(
      (t) =>
        (!keyword.value.trim() ||
          (t.title + (t.assigneeName || '')).includes(keyword.value.trim())) &&
        (!onlyMine.value || t.assigneeId === store.user?.id) &&
        (!onlyOverdue.value || isOverdue(t)) &&
        (!phaseFilter.value || t.phase === phaseFilter.value)
    )
    .sort(
      (a, b) =>
        ({ 紧急: 0, 重要: 1, 普通: 2 })[a.priority] -
          { 紧急: 0, 重要: 1, 普通: 2 }[b.priority] ||
        (deadlineTime(a.deadline) || Infinity) -
          (deadlineTime(b.deadline) || Infinity) ||
        a.id - b.id
    )
)
const phaseCount = (phase, done = false) =>
  tasks.value.filter(
    (t) => t.phase === phase && (!done || t.status === '已完成')
  ).length
const canOperate = (task) =>
  props.isCaptain ||
  (task.assigneeId === store.user?.id &&
    props.members.some((m) => m.userId === store.user?.id))
const formatDeadline = (value) =>
  value
    ? value.slice(0, 16).replace('T', ' ').replaceAll('-', '.')
    : '未设置截止时间'
function resetFilters() {
  keyword.value = ''
  onlyMine.value = false
  onlyOverdue.value = false
  phaseFilter.value = ''
}
async function load() {
  const id = ++requestId
  loading.value = true
  failed.value = false
  try {
    const data = await api.get(`/teams/${props.teamId}/tasks`)
    if (id === requestId) tasks.value = data || []
  } catch {
    if (id === requestId) failed.value = true
  } finally {
    if (id === requestId) loading.value = false
  }
}
watch(
  () => props.teamId,
  () => {
    tasks.value = []
    resetFilters()
    load()
  },
  { immediate: true }
)
const editorVisible = ref(false),
  editingId = ref(null),
  saving = ref(false)
const emptyForm = () => ({
  title: '',
  content: '',
  phase: '准备阶段',
  priority: '普通',
  assigneeId: null,
  deadline: '',
  resourceId: null
})
const form = reactive(emptyForm())
function openEditor(task) {
  editingId.value = task?.id || null
  Object.assign(form, emptyForm())
  if (task)
    for (const key of Object.keys(form))
      form[key] = task[key] ?? emptyForm()[key]
  if (!props.members.some((m) => m.userId === form.assigneeId))
    form.assigneeId = null
  if (!props.resources.some((r) => r.id === form.resourceId))
    form.resourceId = null
  editorVisible.value = true
}
async function save() {
  if (saving.value) return
  if (!form.title.trim()) return ElMessage.warning('请填写任务标题')
  saving.value = true
  try {
    const payload = {
      ...form,
      title: form.title.trim(),
      assigneeId: form.assigneeId || null,
      resourceId: form.resourceId || null,
      deadline: form.deadline || null
    }
    if (editingId.value) await api.put(`/tasks/${editingId.value}`, payload)
    else await api.post(`/teams/${props.teamId}/tasks`, payload)
    editorVisible.value = false
    ElMessage.success(editingId.value ? '任务已更新' : '任务已创建')
    await load()
  } catch {
    /* 接口层统一提示，保留表单方便重试。 */
  } finally {
    saving.value = false
  }
}
async function changeStatus(task, status, completionNote = null) {
  if (busy.has(task.id)) return false
  busy.add(task.id)
  try {
    await api.put(`/tasks/${task.id}/status`, { status, completionNote })
    ElMessage.success('任务状态已更新')
    await load()
    return true
  } catch {
    return false
  } finally {
    busy.delete(task.id)
  }
}
async function removeTask(task) {
  if (busy.has(task.id)) return
  try {
    await ElMessageBox.confirm(
      `确定删除任务「${task.title}」吗？`,
      '删除任务',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  if (busy.has(task.id)) return
  busy.add(task.id)
  try {
    await api.delete(`/tasks/${task.id}`)
    ElMessage.success('任务已删除')
    await load()
  } catch {
    /* 接口层提示 */
  } finally {
    busy.delete(task.id)
  }
}
const planVisible = ref(false),
  generating = ref(false),
  targetDate = ref('')
async function generate() {
  if (generating.value) return
  generating.value = true
  try {
    await api.post(`/teams/${props.teamId}/tasks/plan`, {
      targetDate: targetDate.value || null
    })
    planVisible.value = false
    ElMessage.success('四阶段计划已生成，可继续分配负责人')
    await load()
  } catch {
    /* 保留目标时间 */
  } finally {
    generating.value = false
  }
}
const completeVisible = ref(false),
  completingTask = ref(null),
  completionNote = ref(''),
  completing = ref(false)
function openComplete(task) {
  completingTask.value = task
  completionNote.value = ''
  completeVisible.value = true
}
async function complete() {
  if (completing.value || !completingTask.value) return
  completing.value = true
  try {
    if (
      await changeStatus(completingTask.value, '已完成', completionNote.value)
    )
      completeVisible.value = false
  } finally {
    completing.value = false
  }
}
</script>

<style scoped>
.plan-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  padding: 16px 0 24px;
}
.plan-heading h2 {
  font-size: 21px;
  margin-bottom: 10px;
}
.plan-heading p,
.empty-tip {
  font-size: 12px;
  color: #7b889f;
  line-height: 1.8;
  margin: 0;
}
.plan-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.plan-actions .el-button {
  margin: 0;
}
.plan-metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.plan-metrics > div {
  padding: 20px;
  border-radius: 12px;
  background: #f5f8ff;
  border: 1px solid #eaf0fc;
}
.plan-metrics span {
  color: #7b889f;
  font-size: 12px;
}
.plan-metrics strong {
  display: block;
  font-size: 30px;
  margin: 12px 0;
  color: #3867ed;
}
.plan-metrics small {
  font-size: 14px;
  font-weight: 500;
}
.plan-metrics .urgent {
  background: #fff7ee;
  border-color: #f4e5d1;
}
.plan-metrics .urgent strong {
  color: #bc842b;
}
.deadline-alert {
  margin-top: 18px;
}
.phase-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin: 25px 0;
}
.phase-item {
  border: 1px solid #e7ecf4;
  background: #fff;
  border-radius: 10px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  text-align: left;
  cursor: pointer;
  color: #52617a;
}
.phase-item.active {
  border-color: #7495f2;
  background: #f2f6ff;
}
.phase-number {
  font-size: 20px;
  color: #acbad4;
}
.phase-item strong {
  font-size: 12px;
}
.phase-item small {
  display: block;
  color: #8794aa;
  font-size: 10px;
  margin-top: 7px;
}
.phase-item .el-icon {
  color: #239c77;
  margin-left: auto;
}
.plan-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  margin: 20px 0;
}
.plan-filters .el-input {
  width: 260px;
}
.task-board {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  align-items: start;
}
.task-column {
  background: #f6f8fc;
  border-radius: 12px;
  padding: 14px;
  min-width: 0;
}
.column-heading {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 4px 3px 16px;
}
.column-heading h3 {
  font-size: 13px;
  margin: 0;
}
.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #9ba9c0;
}
.status-dot.working {
  background: #6287ed;
}
.status-dot.done {
  background: #43a586;
}
.column-count {
  margin-left: auto;
  font-size: 11px;
  color: #8895a9;
}
.task-item {
  background: #fff;
  border: 1px solid #e6ecf5;
  border-radius: 10px;
  padding: 17px;
  margin-bottom: 12px;
  overflow-wrap: anywhere;
}
.task-item.overdue {
  border-color: #ead6b6;
}
.task-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.task-item h4 {
  font-size: 14px;
  margin: 15px 0 10px;
  line-height: 1.7;
}
.task-content {
  font-size: 12px;
  line-height: 1.9;
  color: #77859b;
  white-space: pre-wrap;
}
.task-meta {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #7a889f;
  font-size: 11px;
  line-height: 1.8;
  margin-top: 9px;
}
.task-meta > .el-icon {
  flex-shrink: 0;
}
.task-meta small {
  font-size: 10px;
  color: #bc842b;
}
.task-meta.overdue-text {
  color: #bb842b;
}
.task-resource {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 11px;
  line-height: 1.8;
  margin-top: 12px;
  color: #3867ed;
  text-decoration: none;
}
.task-resource .el-icon {
  flex-shrink: 0;
}
.task-review {
  padding: 12px;
  background: #f0f8f5;
  border-radius: 8px;
  margin-top: 15px;
}
.task-review span {
  font-size: 10px;
  color: #399275;
}
.task-review p {
  color: #60796f;
  font-size: 11px;
  line-height: 1.8;
  white-space: pre-wrap;
  margin: 7px 0 0;
}
.completed-time {
  color: #8c9bae;
  font-size: 10px;
  margin-top: 12px;
}
.task-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
  padding-top: 9px;
  border-top: 1px solid #eff2f7;
}
.task-actions .el-button {
  margin: 0;
  font-size: 11px;
}
.task-actions:empty {
  display: none;
}
.column-empty {
  padding: 26px 0;
  color: #99a6ba;
  text-align: center;
  font-size: 12px;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.dialog-description {
  font-size: 13px;
  line-height: 1.9;
  color: #7b889f;
}
@media (max-width: 1200px) {
  .plan-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .phase-item {
    padding: 12px;
    gap: 7px;
  }
  .phase-number {
    font-size: 16px;
  }
  .task-board {
    gap: 10px;
  }
  .task-column {
    padding: 10px;
  }
  .task-item {
    padding: 12px;
  }
}
@media (max-width: 900px) {
  .task-board {
    grid-template-columns: 1fr;
  }
  .plan-metrics,
  .phase-strip {
    grid-template-columns: repeat(2, 1fr);
  }
  .plan-heading h2 {
    font-size: 18px;
  }
}
@media (max-width: 480px) {
  .plan-metrics > div {
    padding: 14px;
  }
  .plan-metrics strong {
    font-size: 25px;
  }
  .phase-item {
    gap: 7px;
    padding: 10px;
  }
  .phase-number {
    display: none;
  }
  .form-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
  .plan-filters {
    gap: 10px;
  }
  .plan-filters .el-input {
    width: 100%;
  }
}
</style>
