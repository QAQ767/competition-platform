import test from 'node:test'
import assert from 'node:assert/strict'
import { CROP_SIZE, MAX_AVATAR_SOURCE_SIZE, cropGeometry, validateAvatarFile } from '../src/utils/avatar.js'

test('accepts a 50MB source and rejects larger, empty or unsupported files', () => {
  assert.doesNotThrow(() => validateAvatarFile({ size: MAX_AVATAR_SOURCE_SIZE, type: 'image/jpeg' }))
  assert.throws(() => validateAvatarFile({ size: MAX_AVATAR_SOURCE_SIZE + 1, type: 'image/jpeg' }), /50MB/)
  assert.throws(() => validateAvatarFile({ size: 0, type: 'image/png' }), /非空/)
  assert.throws(() => validateAvatarFile({ size: 1024, type: 'image/svg+xml' }), /请选择/)
  assert.throws(() => validateAvatarFile({ size: 1024, type: 'image/heic' }), /HEIC/)
})

test('landscape, portrait and tiny sources always fill the crop at every zoom and drag boundary', () => {
  for (const [width, height] of [[4000, 2000], [2000, 4000], [12, 12], [1, 3000]]) {
    for (const zoom of [1, 1.5, 4]) {
      for (const [x, y] of [[0, 0], [-1e6, -1e6], [1e6, 1e6], [-40, -80]]) {
        const crop = cropGeometry(width, height, zoom, x, y)
        assert.ok(crop.x <= 0 && crop.y <= 0)
        assert.ok(crop.x + crop.width >= CROP_SIZE - 1e-6)
        assert.ok(crop.y + crop.height >= CROP_SIZE - 1e-6)
        assert.ok(Math.abs(crop.width / crop.height - width / height) < 1e-6)
      }
    }
  }
})
