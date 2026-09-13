<template>
  <div class="avatar-uploader">
    <input
      ref="fileInput"
      class="avatar-file-input"
      type="file"
      accept="image/jpeg,image/png,image/webp,image/gif,image/bmp"
      aria-label="选择头像图片"
      @change="selectFile"
    />
    <el-button
      size="small"
      :loading="decoding"
      :disabled="saving"
      @click="fileInput.click()"
      >上传头像</el-button
    >
    <div class="avatar-upload-tip">原图 ≤50MB · 支持圆形裁剪与自动压缩</div>
    <el-dialog
      v-model="visible"
      title="裁剪头像"
      width="620px"
      :close-on-click-modal="false"
      :close-on-press-escape="!saving && !decoding"
      :show-close="!saving && !decoding"
      @opened="render"
      @closed="release"
    >
      <p class="crop-description">
        拖动照片选择范围，用滑块或滚轮缩放。手机上可直接拖动照片。
      </p>
      <div class="avatar-crop-layout">
        <div class="avatar-crop-editor">
          <div
            class="avatar-crop-stage"
            :class="{ 'is-busy': saving || decoding }"
            tabindex="0"
            role="group"
            aria-label="头像裁剪区域，可拖动或使用方向键调整位置"
            @pointerdown="startDrag"
            @pointermove="drag"
            @pointerup="endDrag"
            @pointercancel="endDrag"
            @lostpointercapture="endDrag"
            @wheel.prevent="wheelZoom"
            @keydown="moveWithKeyboard"
          >
            <canvas
              ref="cropCanvas"
              :width="CROP_SIZE * 2"
              :height="CROP_SIZE * 2"
            />
            <div class="avatar-crop-mask" aria-hidden="true" />
          </div>
          <div class="avatar-zoom">
            <span>缩放</span>
            <el-slider
              :model-value="zoom"
              :min="1"
              :max="4"
              :step="0.01"
              :disabled="saving || decoding"
              :format-tooltip="(value) => `${Math.round(value * 100)}%`"
              aria-label="头像缩放"
              @update:model-value="setZoom"
            />
            <span>{{ Math.round(zoom * 100) }}%</span>
          </div>
          <el-button size="small" :disabled="saving || decoding" @click="reset"
            >重置位置</el-button
          >
        </div>
        <aside class="avatar-crop-preview">
          <span class="preview-title">头像预览</span>
          <canvas
            ref="previewCanvas"
            width="160"
            height="160"
            aria-label="圆形头像预览"
          />
          <p>512 × 512 像素<br />自动压缩为 JPG</p>
          <span class="source-size">原图 {{ sourceSize }}</span>
        </aside>
      </div>
      <p v-if="error" class="avatar-crop-error" role="alert">{{ error }}</p>
      <template #footer>
        <div class="avatar-crop-footer">
          <el-button :disabled="saving || decoding" @click="fileInput.click()"
            >重新选择</el-button
          >
          <div>
            <el-button :disabled="saving || decoding" @click="visible = false"
              >取消</el-button
            >
            <el-button
              type="primary"
              :loading="saving"
              :disabled="!source || decoding"
              @click="save"
              >保存头像</el-button
            >
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, shallowRef } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { useUserStore } from '../stores/user'
import {
  CROP_SIZE,
  cropGeometry,
  drawAvatar,
  exportAvatar,
  validateAvatarFile
} from '../utils/avatar'

const store = useUserStore()
const fileInput = ref()
const cropCanvas = ref()
const previewCanvas = ref()
const visible = ref(false)
const decoding = ref(false)
const saving = ref(false)
const source = shallowRef(null)
const sourceBytes = ref(0)
const sourceSize = computed(() =>
  sourceBytes.value < 1024 * 1024
    ? `${Math.ceil(sourceBytes.value / 1024)} KB`
    : `${(sourceBytes.value / 1024 / 1024).toFixed(1)} MB`
)
const zoom = ref(1)
const error = ref('')
let geometry = null
let pointer = null
let sourceUrl = null
let generation = 0
let uploadedUrl = null

function release() {
  generation++
  if (sourceUrl) URL.revokeObjectURL(sourceUrl)
  sourceUrl = null
  source.value = null
  geometry = null
  pointer = null
  uploadedUrl = null
}

async function selectFile(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || saving.value || decoding.value) return
  try {
    validateAvatarFile(file)
  } catch (e) {
    ElMessage.warning(e.message)
    return
  }
  decoding.value = true
  error.value = ''
  const current = ++generation
  const url = URL.createObjectURL(file)
  try {
    const image = new Image()
    image.src = url
    await image.decode()
    if (current !== generation) return
    // 保留足够裁剪清晰度，同时降低大照片在拖动时的渲染开销。
    const ratio = Math.min(
      1,
      2048 / Math.max(image.naturalWidth, image.naturalHeight)
    )
    const prepared = document.createElement('canvas')
    prepared.width = Math.max(1, Math.round(image.naturalWidth * ratio))
    prepared.height = Math.max(1, Math.round(image.naturalHeight * ratio))
    prepared
      .getContext('2d')
      .drawImage(image, 0, 0, prepared.width, prepared.height)
    if (sourceUrl) URL.revokeObjectURL(sourceUrl)
    sourceUrl = url
    source.value = prepared
    sourceBytes.value = file.size
    visible.value = true
    await nextTick()
    reset()
  } catch {
    ElMessage.error('无法读取这张图片，文件可能已损坏，请尝试 JPG 或 PNG 图片')
  } finally {
    if (sourceUrl !== url) URL.revokeObjectURL(url)
    decoding.value = false
  }
}

function render() {
  if (!source.value || !geometry) return
  if (cropCanvas.value) drawAvatar(cropCanvas.value, source.value, geometry)
  if (previewCanvas.value)
    drawAvatar(previewCanvas.value, source.value, geometry)
}

function reset() {
  if (!source.value) return
  zoom.value = 1
  const fit = cropGeometry(source.value.width, source.value.height, 1, 0, 0)
  geometry = {
    ...fit,
    x: (CROP_SIZE - fit.width) / 2,
    y: (CROP_SIZE - fit.height) / 2
  }
  uploadedUrl = null
  render()
}

function setZoom(value) {
  if (!geometry || saving.value || decoding.value) return
  const nextZoom = Math.min(4, Math.max(1, value))
  const ratio = nextZoom / zoom.value
  geometry = cropGeometry(
    source.value.width,
    source.value.height,
    nextZoom,
    CROP_SIZE / 2 - (CROP_SIZE / 2 - geometry.x) * ratio,
    CROP_SIZE / 2 - (CROP_SIZE / 2 - geometry.y) * ratio
  )
  zoom.value = nextZoom
  uploadedUrl = null
  render()
}

function startDrag(event) {
  if (
    !geometry ||
    saving.value ||
    decoding.value ||
    pointer ||
    event.button !== 0
  )
    return
  event.preventDefault()
  event.currentTarget.focus()
  event.currentTarget.setPointerCapture(event.pointerId)
  pointer = { id: event.pointerId, x: event.clientX, y: event.clientY }
}
function move(dx, dy) {
  geometry = cropGeometry(
    source.value.width,
    source.value.height,
    zoom.value,
    geometry.x + dx,
    geometry.y + dy
  )
  uploadedUrl = null
  render()
}
function drag(event) {
  if (
    !pointer ||
    pointer.id !== event.pointerId ||
    saving.value ||
    decoding.value
  )
    return
  const ratio = CROP_SIZE / event.currentTarget.getBoundingClientRect().width
  move((event.clientX - pointer.x) * ratio, (event.clientY - pointer.y) * ratio)
  pointer.x = event.clientX
  pointer.y = event.clientY
}
function endDrag() {
  pointer = null
}
function wheelZoom(event) {
  setZoom(zoom.value + (event.deltaY < 0 ? 0.1 : -0.1))
}
function moveWithKeyboard(event) {
  const directions = {
    ArrowLeft: [-8, 0],
    ArrowRight: [8, 0],
    ArrowUp: [0, -8],
    ArrowDown: [0, 8]
  }
  if (!geometry || saving.value || decoding.value || !directions[event.key])
    return
  event.preventDefault()
  move(...directions[event.key])
}

async function save() {
  if (!source.value || saving.value || decoding.value) return
  saving.value = true
  error.value = ''
  try {
    // 资料保存失败时重试复用已上传文件，避免生成重复图片。
    if (!uploadedUrl) {
      const blob = await exportAvatar(source.value, geometry)
      const form = new FormData()
      form.append('file', blob, 'avatar.jpg')
      const data = await api.post('/files/upload', form)
      uploadedUrl = data.url
    }
    const user = await api.put('/user/profile', { avatar: uploadedUrl })
    store.setUser(user)
    visible.value = false
    ElMessage.success('头像已更新')
  } catch {
    error.value = '头像保存失败，裁剪结果已保留，请重试。'
  } finally {
    saving.value = false
  }
}

onBeforeUnmount(release)
</script>

<style scoped>
.avatar-file-input {
  display: none;
}
.avatar-upload-tip {
  margin-top: 8px;
  color: #77849a;
  font-size: 12px;
  line-height: 1.6;
}
.crop-description {
  margin: 0 0 20px;
  color: #617188;
  line-height: 1.7;
}
.avatar-crop-layout {
  display: flex;
  gap: 28px;
  align-items: flex-start;
}
.avatar-crop-editor {
  width: 320px;
  max-width: 100%;
  min-width: 0;
}
.avatar-crop-stage {
  width: 100%;
  aspect-ratio: 1;
  position: relative;
  overflow: hidden;
  background: #182238;
  border-radius: 12px;
  touch-action: none;
  cursor: grab;
}
.avatar-crop-stage:active {
  cursor: grabbing;
}
.avatar-crop-stage:focus-visible {
  outline: 3px solid #3867ed;
  outline-offset: 3px;
}
.avatar-crop-stage.is-busy {
  cursor: wait;
}
.avatar-crop-stage canvas {
  display: block;
  width: 100%;
  height: 100%;
}
.avatar-crop-mask {
  position: absolute;
  inset: 0;
  border: 2px solid #fff;
  border-radius: 50%;
  box-shadow: 0 0 0 100px rgba(9, 18, 35, 0.64);
  pointer-events: none;
}
.avatar-zoom {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 14px 0;
  font-size: 12px;
  white-space: nowrap;
}
.avatar-zoom .el-slider {
  flex: 1;
  min-width: 0;
}
.avatar-zoom span:last-child {
  min-width: 34px;
  text-align: right;
}
.avatar-crop-preview {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding-top: 8px;
  text-align: center;
}
.preview-title {
  color: #24324b;
  font-weight: 600;
}
.avatar-crop-preview canvas {
  border-radius: 50%;
  width: 96px;
  height: 96px;
  box-shadow: 0 0 0 5px #f0f4fb;
}
.avatar-crop-preview p,
.source-size {
  color: #77849a;
  font-size: 12px;
  line-height: 1.8;
  margin: 0;
}
.avatar-crop-error {
  color: #c45656;
  font-size: 13px;
  line-height: 1.7;
}
.avatar-crop-footer {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}
@media (max-width: 540px) {
  .avatar-crop-layout {
    flex-direction: column;
    align-items: center;
    gap: 20px;
  }
  .avatar-crop-preview {
    flex-direction: row;
    flex-wrap: wrap;
    justify-content: center;
    gap: 14px;
  }
  .avatar-crop-preview canvas {
    width: 56px;
    height: 56px;
  }
  .preview-title {
    font-size: 13px;
  }
  .source-size {
    width: 100%;
  }
}
</style>
