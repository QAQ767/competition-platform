import test from 'node:test'
import assert from 'node:assert/strict'
import http from 'node:http'
import { fileURLToPath } from 'node:url'
import { createServer, preview } from 'vite'
import config from '../vite.config.js'

// 真实 HTTP 代理回归：不能把图片请求退回 SPA index.html。
test('dev and production preview serve uploaded image bytes through the backend proxy', async () => {
  const bytes = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j0V0AAAAASUVORK5CYII=', 'base64')
  const backend = http.createServer((req, res) => {
    if (req.url !== '/uploads/avatar.png') { res.writeHead(404); res.end(); return }
    res.writeHead(200, { 'Content-Type': 'image/png' })
    res.end(bytes)
  })
  await new Promise(resolve => backend.listen(0, '127.0.0.1', resolve))
  const target = `http://127.0.0.1:${backend.address().port}`
  const root = fileURLToPath(new URL('../', import.meta.url))
  try {
    for (const mode of ['server', 'preview']) {
      // 只替换已有代理的目标端口；缺少 /uploads 配置时仍会真实失败。
      const proxy = Object.fromEntries(Object.entries(config[mode].proxy).map(([key, value]) => [key, { ...value, target }]))
      const instance = mode === 'server'
        ? await createServer({ ...config, root, cacheDir: 'node_modules/.vite-proxy-test', configFile: false, server: { ...config.server, host: '127.0.0.1', port: 0, proxy } })
        : await preview({ ...config, root, configFile: false, preview: { host: '127.0.0.1', port: 0, proxy } })
      try {
        if (mode === 'server') await instance.listen()
        const url = `http://127.0.0.1:${instance.httpServer.address().port}`
        const image = await fetch(url + '/uploads/avatar.png')
        assert.equal(image.status, 200)
        assert.match(image.headers.get('content-type'), /^image\/png/, mode)
        assert.deepEqual(Buffer.from(await image.arrayBuffer()), bytes)
        assert.equal((await fetch(url + '/uploads/missing.png')).status, 404)
      } finally {
        if (mode === 'server') await instance.close()
        else await new Promise(resolve => instance.httpServer.close(resolve))
      }
    }
  } finally {
    await new Promise(resolve => backend.close(resolve))
  }
})
