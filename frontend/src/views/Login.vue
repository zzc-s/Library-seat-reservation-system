<template>
  <section class="auth-card">
    <div class="tabs">
      <button :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</button>
      <button :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</button>
    </div>

    <h2>{{ mode === 'login' ? '登录' : '注册' }}</h2>

    <form v-if="mode === 'login'" @submit.prevent="onLogin">
      <label>
        <span>用户名</span>
        <input v-model.trim="username" required autocomplete="username" />
        <small class="field-hint">用户名：支持字母、数字、下划线</small>
      </label>
      <label>
        <span>密码</span>
        <input v-model="password" type="password" required autocomplete="current-password" />
        <small class="field-hint">密码：6-20个字符</small>
      </label>
      <button type="submit" :disabled="loading">登录</button>
    </form>

    <form v-else @submit.prevent="onRegister" enctype="multipart/form-data">
      <label>
        <span>用户名 <span class="required">*</span></span>
        <input v-model.trim="username" required autocomplete="username" placeholder="支持字母、数字、下划线" />
        <small class="field-hint">用户名：支持字母、数字、下划线</small>
      </label>
      <label>
        <span>邮箱 <span class="required">*</span></span>
        <input v-model.trim="email" type="email" required placeholder="用于找回/通知，例如 123456@qq.com" />
        <small class="field-hint">请输入有效的邮箱地址</small>
      </label>
      <div class="register-code-block">
        <label for="register-email-code" class="register-code-label">
          <span>邮箱验证码 <span class="required">*</span></span>
        </label>
        <div class="code-inline-register">
          <input
            id="register-email-code"
            v-model.trim="registerCode"
            class="code-field-input-register"
            type="text"
            required
            placeholder="6位验证码"
            maxlength="6"
            inputmode="numeric"
            autocomplete="one-time-code"
          />
          <button type="button" class="btn-send-code" :disabled="sendingCode || codeCooldown > 0 || !email" @click="sendRegisterCode">
            {{ codeCooldown > 0 ? `重新发送(${codeCooldown}s)` : (sendingCode ? '发送中...' : '发送验证码') }}
          </button>
        </div>
        <small class="field-hint">先发送验证码到邮箱，再填写验证码完成注册</small>
      </div>
      <label>
        <span>手机号</span>
        <input
          v-model.trim="phone"
          type="tel"
          inputmode="numeric"
          maxlength="11"
          autocomplete="tel"
          placeholder="11位中国大陆手机号"
        />
        <small class="field-hint">若填写，须为 11 位中国大陆有效号码，且不可与其他账号重复</small>
      </label>
      <label>
        <span>密码 <span class="required">*</span></span>
        <input v-model="password" type="password" required autocomplete="new-password" placeholder="6-20个字符" />
        <small class="field-hint">密码：6-20个字符</small>
      </label>
      <!-- 不要用 label 包住 file input：点击预览时会既触发 @click 又触发 label 的默认关联，导致文件选择框连弹两次 -->
      <div class="avatar-field">
        <span class="avatar-field-label">头像（可选）</span>
        <div class="avatar-upload">
          <input 
            type="file" 
            ref="avatarInput" 
            @change="handleAvatarChange" 
            accept="image/*" 
            style="display: none"
          />
          <div class="avatar-preview-wrapper">
            <div class="avatar-preview" @click="$refs.avatarInput?.click()">
              <img v-if="avatarPreview" :src="avatarPreview" alt="头像预览" />
              <div v-else class="avatar-placeholder">
                <span class="avatar-icon">📷</span>
                <span class="avatar-text">点击上传头像</span>
              </div>
            </div>
            <button type="button" @click="$refs.avatarInput?.click()" class="btn-select-avatar">
              {{ avatarPreview ? '更换头像' : '选择头像' }}
            </button>
            <button 
              v-if="avatarPreview" 
              type="button" 
              @click="removeAvatar" 
              class="btn-remove-avatar"
            >
              移除
            </button>
          </div>
        </div>
      </div>
      <button type="submit" :disabled="loading">注册并登录</button>
    </form>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="success" class="success">{{ success }}</p>
    <p class="hint" v-if="mode === 'register'">注册默认创建普通用户，管理员为预置账号</p>
    <RouterLink to="/forgot-password" class="link">忘记密码？</RouterLink>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import http from '../lib/http'
import { useAuthStore } from '../stores/auth'
import { useRouter } from 'vue-router'

const mode = ref<'login' | 'register'>('login')
const username = ref('')
const password = ref('')
const email = ref('')
const phone = ref('')
const registerCode = ref('')
const sendingCode = ref(false)
const codeCooldown = ref(0)
const loading = ref(false)
const error = ref('')
const success = ref('')
const auth = useAuthStore()
const router = useRouter()
const emailPattern = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/
const phonePattern = /^1[3-9]\d{9}$/
const avatarFile = ref<File | null>(null)
const avatarPreview = ref<string | null>(null)
const avatarInput = ref<HTMLInputElement | null>(null)

async function onLogin() {
  error.value = ''
  success.value = ''
  loading.value = true
  try {
    const res = await http.post('/api/auth/login', { username: username.value, password: password.value })
    const token = res.data.token as string
    const serverStartTime = res.data?.serverStartTime as number
    auth.setAuth(token, username.value)
    // 保存服务器启动时间，用于检测后端是否重启
    if (serverStartTime) {
      auth.setServerStartTime(serverStartTime)
    }
    
    // 等待一下确保 role 已从 token 中解析并设置完成
    await new Promise(resolve => setTimeout(resolve, 50))
    
    // 登录成功后，根据用户角色决定跳转路径
    const redirect = router.currentRoute.value.query.redirect as string
    if (redirect) {
      // 如果有重定向路径，检查是否是管理员访问普通用户页面
      // 如果是管理员且重定向到首页，应该跳转到数据看板
      if (auth.isAdmin && redirect === '/') {
        await router.replace('/dashboard')
      } else {
        await router.replace(redirect)
      }
    } else if (auth.isAdmin) {
      // 管理员跳转到数据看板
      await router.replace('/dashboard')
    } else {
      // 普通用户跳转到首页
      await router.replace('/')
    }
  } catch (e: any) {
    error.value = e?.message || e?.response?.data?.message || '登录失败，请检查用户名和密码'
  } finally {
    loading.value = false
  }
}

function handleAvatarChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) {
    // 验证文件类型
    if (!file.type.startsWith('image/')) {
      error.value = '请选择图片文件'
      return
    }
    // 验证文件大小
    if (file.size > 5 * 1024 * 1024) {
      error.value = '头像文件大小不能超过5MB'
      return
    }
    avatarFile.value = file
    error.value = '' // 清除之前的错误
    const reader = new FileReader()
    reader.onload = (e) => {
      avatarPreview.value = e.target?.result as string
    }
    reader.readAsDataURL(file)
  }
}

function removeAvatar() {
  avatarFile.value = null
  avatarPreview.value = null
  if (avatarInput.value) {
    avatarInput.value.value = ''
  }
}

async function onRegister() {
  error.value = ''
  success.value = ''
  
  // 前端基础验证
  if (!username.value || !password.value || !email.value || !registerCode.value) {
    error.value = '请填写所有必填项'
    return
  }
  
  // 验证用户名格式
  const usernamePattern = /^[a-zA-Z0-9_]{1,50}$/
  if (!usernamePattern.test(username.value)) {
    error.value = '用户名只能包含字母、数字和下划线，长度不超过50个字符'
    return
  }
  
  // 验证密码长度
  if (password.value.length < 6 || password.value.length > 20) {
    error.value = '密码长度必须在6-20个字符之间'
    return
  }
  
  // 验证邮箱格式
  if (!emailPattern.test(email.value)) {
    error.value = '邮箱格式不正确，请输入有效的邮箱地址'
    return
  }

  if (phone.value && !phonePattern.test(phone.value)) {
    error.value = '手机号格式不正确，请输入11位中国大陆手机号'
    return
  }
  if (!/^\d{6}$/.test(registerCode.value)) {
    error.value = '邮箱验证码应为6位数字'
    return
  }
  
  loading.value = true
  try {
    const formData = new FormData()
    formData.append('username', username.value)
    formData.append('password', password.value)
    formData.append('email', email.value)
    formData.append('code', registerCode.value)
    if (phone.value) {
      formData.append('phone', phone.value)
    }
    if (avatarFile.value) {
      formData.append('avatar', avatarFile.value)
    }
    
    await http.post('/api/auth/register', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    success.value = '注册成功！正在自动登录...'
    
    // 注册成功后自动登录
    const res = await http.post('/api/auth/login', { username: username.value, password: password.value })
    const token = res.data.token as string
    const serverStartTime = res.data?.serverStartTime as number
    auth.setAuth(token, username.value)
    // 保存服务器启动时间，用于检测后端是否重启
    if (serverStartTime) {
      auth.setServerStartTime(serverStartTime)
    }
    
    // 等待一下确保 role 已从 token 中解析并设置完成
    await new Promise(resolve => setTimeout(resolve, 50))
    
    // 登录成功后，根据用户角色决定跳转路径
    const redirect = router.currentRoute.value.query.redirect as string
    setTimeout(() => {
      if (redirect) {
        // 如果有重定向路径，检查是否是管理员访问普通用户页面
        // 如果是管理员且重定向到首页，应该跳转到数据看板
        if (auth.isAdmin && redirect === '/') {
          router.replace('/dashboard')
        } else {
          router.replace(redirect)
        }
      } else if (auth.isAdmin) {
        // 管理员跳转到数据看板
        router.replace('/dashboard')
      } else {
        // 普通用户跳转到首页
        router.replace('/')
      }
    }, 1000)
  } catch (e: any) {
    // 显示后端返回的详细错误信息
    const errorMessage = e?.response?.data?.message
    if (errorMessage) {
      error.value = errorMessage
    } else if (e?.response?.status === 400) {
      error.value = '请求参数错误，请检查输入信息'
    } else if (e?.response?.status === 500) {
      error.value = '服务器错误，请稍后重试'
    } else {
      error.value = '注册失败：' + (e?.message || '未知错误')
    }
  } finally {
    loading.value = false
  }
}

let cooldownTimer: any = null
async function sendRegisterCode() {
  error.value = ''
  success.value = ''
  if (!email.value || !emailPattern.test(email.value)) {
    error.value = '请先输入有效邮箱'
    return
  }
  sendingCode.value = true
  try {
    await http.post('/api/auth/register-code', { email: email.value })
    success.value = '验证码已发送，请查收邮件'
    codeCooldown.value = 60
    if (cooldownTimer) clearInterval(cooldownTimer)
    cooldownTimer = setInterval(() => {
      codeCooldown.value--
      if (codeCooldown.value <= 0) {
        clearInterval(cooldownTimer)
        cooldownTimer = null
      }
    }, 1000)
  } catch (e: any) {
    error.value = e?.message || e?.response?.data?.message || '发送验证码失败'
  } finally {
    sendingCode.value = false
  }
}
</script>

<style scoped src="../styles/views/Login.css"></style>


