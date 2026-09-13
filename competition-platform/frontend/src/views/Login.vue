<template>
  <div class="login-page">
    <section class="login-story">
      <span class="eyebrow">GREAT THINGS START TOGETHER</span>
      <div class="login-symbol">
        <el-icon><Connection /></el-icon>
      </div>
      <h1>你的下一场精彩，<br />从同行开始。</h1>
      <p>在这里找到志同道合的伙伴，<br />把一个人的热爱，变成一群人的可能。</p>
      <div class="login-values">
        <span
          ><el-icon><Trophy /></el-icon> 发现竞赛</span
        ><span
          ><el-icon><Connection /></el-icon> 遇见队友</span
        ><span
          ><el-icon><Reading /></el-icon> 共同成长</span
        >
      </div>
    </section>
    <el-card class="login-card" shadow="never">
      <span class="eyebrow">WELCOME TO CAMPUS TOGETHER</span>
      <h2 class="title">
        {{ tab === 'login' ? '欢迎回来' : '开启你的竞赛旅程' }}
      </h2>
      <p class="login-subtitle">
        {{
          tab === 'login'
            ? '登录账号，继续你的成长之旅'
            : '创建账号，遇见一起向前的伙伴'
        }}
      </p>
      <el-tabs v-model="tab" stretch>
        <el-tab-pane label="登录" name="login">
          <el-form :model="loginForm" label-position="top">
            <el-form-item label="用户名">
              <el-input
                v-model="loginForm.username"
                placeholder="请输入用户名"
                autocomplete="username"
              />
            </el-form-item>
            <el-form-item label="密码">
              <el-input
                v-model="loginForm.password"
                type="password"
                show-password
                placeholder="请输入密码"
                autocomplete="current-password"
                @keyup.enter="doLogin"
              />
            </el-form-item>
            <el-button
              type="primary"
              style="width: 100%"
              :loading="loading"
              @click="doLogin"
              >登 录</el-button
            >
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="注册" name="register">
          <el-form :model="regForm" label-position="top">
            <el-form-item label="用户名"
              ><el-input
                v-model="regForm.username"
                placeholder="3-20 位字母数字"
            /></el-form-item>
            <el-form-item label="密码"
              ><el-input
                v-model="regForm.password"
                type="password"
                show-password
            /></el-form-item>
            <el-form-item label="邮箱"
              ><el-input v-model="regForm.email"
            /></el-form-item>
            <el-form-item label="学院"
              ><el-input v-model="regForm.college"
            /></el-form-item>
            <el-form-item label="专业"
              ><el-input v-model="regForm.major"
            /></el-form-item>
            <el-button
              type="primary"
              style="width: 100%"
              :loading="loading"
              @click="doRegister"
              >注 册</el-button
            >
          </el-form>
        </el-tab-pane>
      </el-tabs>
      <div class="tip">
        演示账号：student1~student7 / admin1，密码均为 <b>123456</b>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'

const router = useRouter()
const route = useRoute()
function afterLogin() {
  const target = route.query.redirect
  const safe =
    typeof target === 'string' &&
    target.startsWith('/') &&
    !target.startsWith('//') &&
    !target.startsWith('/login') &&
    router.resolve(target).matched.length
  return router.replace(safe ? target : '/')
}
const store = useUserStore()

const tab = ref('login')
const loading = ref(false)
const loginForm = ref({ username: '', password: '' })
const regForm = ref({
  username: '',
  password: '',
  email: '',
  college: '',
  major: ''
})

async function doLogin() {
  if (loading.value) return
  if (!loginForm.value.username || !loginForm.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await store.login(loginForm.value)
    ElMessage.success('登录成功')
    afterLogin()
  } catch {
    // 接口层已提示错误，保留表单供用户重试。
  } finally {
    loading.value = false
  }
}

async function doRegister() {
  if (loading.value) return
  if (!regForm.value.username || !regForm.value.password) {
    ElMessage.warning('用户名和密码不能为空')
    return
  }
  loading.value = true
  try {
    await store.register(regForm.value)
    ElMessage.success('注册成功，已自动登录')
    afterLogin()
  } catch {
    // 注册失败时保留已填写的信息。
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  max-width: 1000px;
  margin: 36px auto;
  min-height: 580px;
  border: 1px solid #e2e9f6;
  border-radius: 20px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 18px 60px #274a8c08;
}
.login-story {
  position: relative;
  background: linear-gradient(145deg, #eaf1ff, #e4edff);
  padding: 48px 40px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.login-story .eyebrow {
  font-size: 9px;
  color: #7e96c3;
}
.login-symbol {
  display: grid;
  place-items: center;
  width: 90px;
  height: 90px;
  border: 1px solid #fff;
  border-radius: 25px;
  background: #ffffff66;
  color: #6087eb;
  font-size: 48px;
  margin: 36px 0 26px;
  transform: rotate(-8deg);
}
.login-story h1 {
  font-size: 31px;
  line-height: 1.6;
  letter-spacing: 1px;
}
.login-story p {
  color: #7a8fb2;
  font-size: 13px;
  line-height: 2;
}
.login-values {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  margin-top: 32px;
  font-size: 11px;
  color: #637da9;
}
.login-values span {
  display: flex;
  align-items: center;
  gap: 6px;
}
.login-card {
  border: 0;
  border-radius: 0;
  align-self: center;
}
.login-card :deep(.el-card__body) {
  padding: 44px;
}
.title {
  text-align: left;
  font-size: 27px;
  margin: 12px 0;
}
.login-subtitle {
  color: #8a98af;
  font-size: 12px;
  margin-bottom: 24px;
}
.login-card :deep(.el-tabs__header) {
  margin-bottom: 26px;
}
.login-card :deep(.el-input__wrapper) {
  min-height: 43px;
}
.login-card :deep(.el-button) {
  min-height: 44px;
  margin-top: 8px;
}
.tip {
  margin-top: 24px;
  font-size: 11px;
  line-height: 1.9;
  padding: 12px;
  border-radius: 8px;
  background: #f7f9fd;
  color: #8795ab;
  text-align: center;
}
@media (max-width: 1000px) {
  .login-story {
    padding: 32px 26px;
  }
  .login-story h1 {
    font-size: 26px;
  }
  .login-card :deep(.el-card__body) {
    padding: 28px;
  }
}
@media (max-width: 767px) {
  .login-page {
    grid-template-columns: 1fr;
    margin: 0 auto;
  }
  .login-story {
    padding: 24px;
  }
  .login-symbol,
  .login-values,
  .login-story p {
    display: none;
  }
  .login-story h1 {
    font-size: 24px;
    margin: 10px 0 0;
  }
  .login-card :deep(.el-card__body) {
    padding: 28px 24px;
  }
}
</style>
