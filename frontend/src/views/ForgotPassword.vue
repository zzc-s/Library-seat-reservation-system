<template>
  <section class="card">
    <h2>找回密码</h2>
    <p class="hint">请输入注册邮箱获取验证码，然后重置密码。</p>

    <form @submit.prevent="sendCode" class="form">
      <label>
        <span>邮箱</span>
        <input v-model.trim="email" type="email" required placeholder="例如：123456@qq.com" />
      </label>
      <button type="submit" :disabled="sending || !email">获取验证码</button>
    </form>

    <form @submit.prevent="doReset" class="form">
      <label>
        <span>验证码</span>
        <input v-model.trim="code" required placeholder="6位验证码" />
      </label>
      <label>
        <span>新密码</span>
        <input v-model="newPassword" type="password" required minlength="6" />
      </label>
      <button type="submit" :disabled="reseting || !code || !newPassword">重置密码</button>
    </form>

    <p v-if="success" class="success">{{ success }}</p>
    <p v-if="error" class="error">{{ error }}</p>
    <RouterLink to="/login" class="back">返回登录</RouterLink>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import http from '../lib/http'

const email = ref('')
const code = ref('')
const newPassword = ref('')
const sending = ref(false)
const reseting = ref(false)
const success = ref('')
const error = ref('')
const router = useRouter()

const emailPattern = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/

async function sendCode() {
  error.value = ''
  success.value = ''
  if (!emailPattern.test(email.value)) {
    error.value = '邮箱格式不正确'
    return
  }
  sending.value = true
  try {
    await http.post('/api/auth/forgot', { email: email.value })
    success.value = '验证码已发送，请查收邮箱'
  } catch (e: any) {
    error.value = e?.message || e?.response?.data?.message || '发送失败，请稍后重试'
  } finally {
    sending.value = false
  }
}

async function doReset() {
  error.value = ''
  success.value = ''
  if (!emailPattern.test(email.value)) {
    error.value = '邮箱格式不正确'
    return
  }
  reseting.value = true
  try {
    await http.post('/api/auth/reset', {
      email: email.value,
      code: code.value,
      newPassword: newPassword.value
    })
    success.value = '重置成功，请使用新密码登录'
    setTimeout(() => router.replace({ name: 'login' }), 800)
  } catch (e: any) {
    error.value = e?.message || e?.response?.data?.message || '重置失败，请检查验证码'
  } finally {
    reseting.value = false
  }
}
</script>

<style scoped src="../styles/views/ForgotPassword.css"></style>

