export const MAX_AVATAR_SOURCE_SIZE = 50 * 1024 * 1024
export const AVATAR_SIZE = 512
export const CROP_SIZE = 320

export function validateAvatarFile(file) {
  if (!file || !file.size) throw new Error('请选择非空图片文件')
  if (file.size > MAX_AVATAR_SOURCE_SIZE)
    throw new Error('原图不能超过 50MB，请选择较小的图片')
  if (!/^image\/(jpeg|png|webp|gif|bmp)$/i.test(file.type)) {
    throw new Error(
      '请选择 JPG、PNG、WebP、GIF 或 BMP 图片；HEIC 图片请先转为 JPG'
    )
  }
}

// 所有坐标使用固定的逻辑画布尺寸，与显示尺寸、设备像素比无关。
export function cropGeometry(width, height, zoom, x, y) {
  const scale = Math.max(CROP_SIZE / width, CROP_SIZE / height) * zoom
  const drawWidth = width * scale
  const drawHeight = height * scale
  return {
    width: drawWidth,
    height: drawHeight,
    x: Math.max(CROP_SIZE - drawWidth, Math.min(0, x)),
    y: Math.max(CROP_SIZE - drawHeight, Math.min(0, y))
  }
}

export function drawAvatar(canvas, source, geometry) {
  const ctx = canvas.getContext('2d')
  const ratio = canvas.width / CROP_SIZE
  ctx.fillStyle = '#fff'
  ctx.fillRect(0, 0, canvas.width, canvas.height)
  ctx.imageSmoothingEnabled = true
  ctx.imageSmoothingQuality = 'high'
  ctx.drawImage(
    source,
    geometry.x * ratio,
    geometry.y * ratio,
    geometry.width * ratio,
    geometry.height * ratio
  )
}

export async function exportAvatar(source, geometry) {
  const canvas = document.createElement('canvas')
  canvas.width = canvas.height = AVATAR_SIZE
  drawAvatar(canvas, source, geometry)
  const blob = await new Promise((resolve) =>
    canvas.toBlob(resolve, 'image/jpeg', 0.88)
  )
  if (!blob || blob.size > 1024 * 1024)
    throw new Error('头像压缩失败，请重新选择图片')
  return blob
}
