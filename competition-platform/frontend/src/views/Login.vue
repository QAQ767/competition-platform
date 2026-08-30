<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2 class="title">🏆 高校学科竞赛组队与训练平台</h2>
      <el-tabs v-model="tab" stretch>
        <el-tab-pane label="登录" name="login">
          <el-form :model="loginForm" label-width="70px">
            <el-form-item label="用户名">
              <el-input v-model="loginForm.username" placeholder="如 student1" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="loginForm.password" type="password" show-password placeholder="如 123456" @keyup.enter="doLogin" />
            </el-form-item>
            <el-button type="primary" style="width: 100%" :loading="loading" @click="doLogin">登 录</el-button>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="注册" name="register">
          <el-form :model="regForm" label-width="70px">
            <el-form-item label="用户名"><el-input v-model="regForm.username" placeholder="3-20 位字母数字" /></el-form-item>
            <el-form-item label="密码"><el-input v-model="regForm.password" type="password" show-password /></el-form-item>
            <el-form-item label="邮箱"><el-input v-model="regForm.email" /></el-form-item>
            <el-form-item label="学院"><el-input v-model="regForm.college" /></el-form-item>
            <el-form-item label="专业"><el-input v-model="regForm.major" /></el-form-item>
            <el-button type="primary" style="width: 100%" :loading="loading" @click="doRegister">注 册</el-button>
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
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'

const router = useRouter()
const store = useUserStore()

const tab = ref('login')
const loading = ref(false)
const loginForm = ref({ username: '', password: '' })
const regForm = ref({ username: '', password: '', email: '', college: '', major: '' })

async function doLogin() {
  if (!loginForm.value.username || !loginForm.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await store.login(loginForm.value)
    ElMessage.success('登录成功')
    router.push('/')
  } finally {
    loading.value = false
  }
}

async function doRegister() {
  if (!regForm.value.username || !regForm.value.password) {
    ElMessage.warning('用户名和密码不能为空')
    return
  }
  loading.value = true
  try {
    await store.register(regForm.value)
    ElMessage.success('注册成功，已自动登录')
    router.push('/')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  justify-content: center;
  padding-top: 80px;
}
.login-card {
  width: 420px;
}
.title {
  text-align: center;
  margin: 0 0 20px;
  font-size: 20px;
}
.tip {
  margin-top: 16px;
  font-size: 12px;
  color: #909399;
  text-align: center;
}
</style>
