<template>
  <section class="profile-page">
    <div class="profile-container">
      <!-- 个人信息卡片 -->
      <div class="profile-card">
        <div class="header">
          <h2>个人信息</h2>
          <p class="subtitle" v-if="loading">加载中...</p>
          <p class="subtitle" v-else>当前账号：{{ displayName }}</p>
        </div>

        <!-- 头像上传 -->
        <div class="card card-avatar">
          <h3>头像设置</h3>
          <div class="avatar-section">
            <div class="avatar-display">
              <img 
                v-if="currentAvatarUrl"
                :src="currentAvatarUrl" 
                :alt="profile?.username || '用户'"
                class="avatar-image"
                @error="handleAvatarError"
                @load="handleAvatarLoad"
              />
              <div v-if="!currentAvatarUrl || avatarLoadError" class="avatar-placeholder">
                <span class="avatar-icon">👤</span>
                <span class="avatar-text">{{ currentAvatarUrl && avatarLoadError ? '头像加载失败' : '暂无头像' }}</span>
              </div>
            </div>
            <div class="avatar-upload-controls">
              <input 
                type="file" 
                ref="avatarInput" 
                @change="handleAvatarChange" 
                accept="image/*" 
                style="display: none"
              />
              <button 
                type="button" 
                @click="$refs.avatarInput?.click()" 
                class="btn-upload-avatar"
                :disabled="uploading"
              >
                {{ currentAvatarUrl ? '更换头像' : '上传头像' }}
              </button>
              <button 
                v-if="currentAvatarUrl" 
                type="button" 
                @click="removeAvatar" 
                class="btn-remove-avatar"
                :disabled="uploading"
              >
                移除头像
              </button>
              <button 
                v-if="currentAvatarUrl && avatarLoadError" 
                type="button" 
                @click="reloadAvatar" 
                class="btn-reload-avatar"
                :disabled="uploading"
              >
                重新加载
              </button>
            </div>
            <div v-if="avatarPreview" class="avatar-preview-section">
              <p class="preview-label">预览：</p>
              <div class="avatar-preview">
                <img :src="avatarPreview" alt="头像预览" />
              </div>
              <div class="avatar-preview-actions">
                <button @click="confirmUpload" :disabled="uploading" class="btn-confirm">
                  {{ uploading ? '上传中...' : '确认上传' }}
                </button>
                <button @click="cancelUpload" :disabled="uploading" class="btn-cancel">取消</button>
              </div>
            </div>
            <p v-if="avatarMsg" class="avatar-msg">{{ avatarMsg }}</p>
            <p v-if="avatarErr" class="avatar-err">{{ avatarErr }}</p>
            <!-- 调试信息（开发时可见，生产环境可隐藏） -->
            <p v-if="currentAvatarUrl && avatarLoadError" class="avatar-debug" style="font-size: 12px; color: #f59e0b; margin-top: 8px;">
              ⚠️ 头像加载失败，请检查URL是否正确: {{ currentAvatarUrl }}
            </p>
          </div>
        </div>

        <!-- 个人信息详情（管理员与普通用户均可自助修改，便于更换负责人后更新联系方式） -->
        <div class="card card-account">
          <div class="card-section-head">
            <h3>账号信息</h3>
            <p class="card-section-desc">维护登录名与联系方式，便于通知与账号交接。</p>
          </div>
          <form class="account-form" @submit.prevent="saveAccountInfo">
            <div class="account-form-inner">
              <label class="form-field">
                <span class="field-label">用户名</span>
                <input
                  v-model.trim="accountForm.username"
                  class="form-input"
                  type="text"
                  autocomplete="username"
                  placeholder="字母、数字、下划线"
                  :disabled="isPresetAdmin || savingAccount"
                  :class="{ 'input-disabled': isPresetAdmin }"
                />
                <small v-if="isPresetAdmin" class="field-hint">预置管理员账号用户名不可修改；其他管理员可修改用户名，保存后将自动刷新登录凭证。</small>
              </label>
              <label class="form-field">
                <span class="field-label">邮箱 <span class="required">*</span></span>
                <input
                  v-model.trim="accountForm.email"
                  class="form-input"
                  type="email"
                  required
                  autocomplete="email"
                  placeholder="用于找回密码与通知"
                  :disabled="savingAccount"
                />
              </label>
              <label class="form-field">
                <span class="field-label">手机号</span>
                <input
                  v-model.trim="accountForm.phone"
                  class="form-input"
                  type="tel"
                  maxlength="11"
                  inputmode="numeric"
                  autocomplete="tel"
                  placeholder="11 位中国大陆手机号，可留空"
                  :disabled="savingAccount"
                />
                <small class="field-hint">若填写，须为 11 位有效号码且全站唯一。</small>
              </label>
              <div class="account-meta">
                <span class="account-meta-item">
                  <span class="account-meta-key">角色</span>
                  <span class="account-meta-val">{{ profile?.role === 'ADMIN' ? '管理员' : '普通用户' }}</span>
                </span>
                <span class="account-meta-item">
                  <span class="account-meta-key">注册时间</span>
                  <span class="account-meta-val">{{ formatDateTime(profile?.createdAt) }}</span>
                </span>
              </div>
              <div class="account-form-actions">
                <button type="submit" class="btn-save-account" :disabled="savingAccount">
                  {{ savingAccount ? '保存中…' : '保存账号信息' }}
                </button>
              </div>
            </div>
            <p v-if="accountMsg" class="msg account-form-feedback">{{ accountMsg }}</p>
            <p v-if="accountErr" class="err account-form-feedback">{{ accountErr }}</p>
          </form>
        </div>

        <!-- 修改密码 -->
        <div class="card card-password">
          <div class="card-section-head">
            <h3>修改密码</h3>
            <p class="card-section-desc">修改成功后需使用新密码重新登录。</p>
          </div>
          <form class="password-form" @submit.prevent="changePassword">
            <div class="account-form-inner">
              <label class="form-field">
                <span class="field-label">原密码</span>
                <input v-model="oldPassword" class="form-input" type="password" required autocomplete="current-password" />
              </label>
              <label class="form-field">
                <span class="field-label">新密码</span>
                <input v-model="newPassword" class="form-input" type="password" required minlength="6" autocomplete="new-password" />
              </label>
              <label class="form-field">
                <span class="field-label">确认新密码</span>
                <input v-model="confirmPassword" class="form-input" type="password" required minlength="6" autocomplete="new-password" />
                <span v-if="confirmPassword && newPassword !== confirmPassword" class="password-mismatch">
                  两次输入的密码不一致
                </span>
              </label>
              <div class="account-form-actions account-form-actions--password">
                <button type="submit" class="btn-save-account" :disabled="changing || !isPasswordMatch">
                  {{ changing ? '更新中…' : '更新密码' }}
                </button>
              </div>
            </div>
          </form>
          <p v-if="changeMsg" class="msg">{{ changeMsg }}</p>
          <p v-if="changeErr" class="err">{{ changeErr }}</p>
        </div>
      </div>

      <!-- 管理员侧栏：快捷入口与安全说明（无个人违规、无自助注销，与读者个人信息页区分） -->
      <div v-if="auth.isAdmin" class="profile-card profile-card-admin-aside">
        <div class="header">
          <h2>管理工作台</h2>
          <p class="subtitle">快捷入口与账号说明</p>
        </div>

        <div class="card admin-aside-card">
          <h3>系统概览</h3>
          <div class="admin-kpi-grid">
            <div class="admin-kpi">
              <div class="admin-kpi-key">用户数</div>
              <div class="admin-kpi-val">{{ adminOverview.usersTotal ?? '—' }}</div>
            </div>
            <div class="admin-kpi">
              <div class="admin-kpi-key">座位数</div>
              <div class="admin-kpi-val">{{ adminOverview.seatsTotal ?? '—' }}</div>
            </div>
            <div class="admin-kpi">
              <div class="admin-kpi-key">待审核预约</div>
              <div class="admin-kpi-val admin-kpi-warn">{{ adminOverview.pendingReservations ?? '—' }}</div>
            </div>
            <div class="admin-kpi">
              <div class="admin-kpi-key">未处理违规</div>
              <div class="admin-kpi-val admin-kpi-warn">{{ adminOverview.unhandledViolations ?? '—' }}</div>
            </div>
          </div>
          <p v-if="adminOverviewErr" class="admin-kpi-hint err">{{ adminOverviewErr }}</p>
          <p v-else class="admin-kpi-hint">概览数据用于快速判断待处理事项，详细内容请进入对应模块。</p>
        </div>

        <div class="card admin-aside-card">
          <h3>常用管理入口</h3>
          <nav class="admin-quick-nav" aria-label="管理功能快捷链接">
            <router-link class="admin-quick-link" :to="{ name: 'dashboard' }">数据看板</router-link>
            <router-link class="admin-quick-link" :to="{ name: 'adminUsers' }">用户管理</router-link>
            <router-link class="admin-quick-link" :to="{ name: 'adminSeats' }">座位管理</router-link>
            <router-link class="admin-quick-link" :to="{ name: 'adminReservations' }">预约管理</router-link>
            <router-link class="admin-quick-link" :to="{ name: 'adminViolations' }">违规管理</router-link>
            <router-link class="admin-quick-link" :to="{ name: 'adminBooks' }">图书管理</router-link>
            <router-link class="admin-quick-link" :to="{ name: 'adminNotices' }">公告管理</router-link>
            <router-link class="admin-quick-link" :to="{ name: 'adminBorrows' }">借阅管理</router-link>
            <router-link class="admin-quick-link" :to="{ name: 'adminFeedbacks' }">反馈管理</router-link>
          </nav>
        </div>

        <div class="card admin-aside-card">
          <h3>今日管理清单</h3>
          <ul class="admin-tip-list admin-checklist">
            <li>先查看「待审核预约」「未处理违规」，及时处理堆积事项。</li>
            <li>更换负责人时，务必更新管理员邮箱/手机号，并修改密码。</li>
            <li>建议定期到「座位管理」检查异常状态（故障/维修/闲置）。</li>
          </ul>
        </div>

        <div class="card admin-aside-card">
          <h3>为什么这里没有「违规记录 / 注销」？</h3>
          <ul class="admin-tip-list">
            <li>
              违规记录面向<strong>读者预约签到</strong>场景；管理员账号不参与读者侧违规统计，侧栏不展示「我的违规」。
            </li>
            <li>
              自助注销仅开放给<strong>普通用户</strong>；停用或删除管理员需在「用户管理」中由其他管理员处理，避免误操作锁死后台。
            </li>
            <li v-if="isPresetAdmin">
              预置账号 <span class="admin-mono">admin</span> 的用户名不可改；更换负责人时请在本页更新<strong>邮箱、手机号</strong>并定期<strong>修改密码</strong>。
            </li>
            <li v-else>修改登录名保存后，系统会自动刷新登录凭证，请勿在公共设备保存密码。</li>
          </ul>
        </div>

        <div
          v-if="profile && profile.serverStartTime != null && profile.serverStartTime !== ''"
          class="card admin-aside-card admin-aside-meta"
        >
          <h3>服务状态</h3>
          <p class="admin-meta-line">
            后端进程启动时间：<strong>{{ formatServerStart(profile.serverStartTime) }}</strong>
          </p>
          <p class="admin-meta-hint">
            若时间异常，可能曾被重启；登录态失效时可尝试重新登录。
          </p>
        </div>
      </div>

      <!-- 违规记录卡片（仅普通用户显示） -->
      <div v-if="showViolations" class="profile-card">
        <div class="header">
          <h2>我的违规记录</h2>
        </div>

        <div class="card">
          <!-- 违规统计 -->
          <div v-if="violationStats" class="violation-stats">
            <div class="stat-item">
              <span class="stat-label">总违规数：</span>
              <span class="stat-value">{{ violationStats.total || 0 }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">待处理：</span>
              <span class="stat-value stat-unhandled">{{ violationStats.unhandled || 0 }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">已处理：</span>
              <span class="stat-value stat-handled">{{ violationStats.handled || 0 }}</span>
            </div>
          </div>

          <!-- 违规记录列表 -->
          <div v-if="loadingViolations" class="loading">加载中...</div>
          <div v-else-if="violations.length === 0" class="empty-state">
            暂无违规记录
          </div>
          <div v-else class="violations-list-wrap">
            <div class="violations-list">
              <div v-for="v in violations" :key="v.id" class="violation-item">
                <div class="violation-header">
                  <span class="violation-type">{{ getViolationTypeText(v.type) }}</span>
                  <span :class="['violation-status', v.handled ? 'handled' : 'unhandled']">
                    {{ v.handled ? '已处理' : '待处理' }}
                  </span>
                </div>
                <div class="violation-content">
                  <p v-if="v.description" class="violation-desc">{{ v.description }}</p>
                  <p class="violation-time">发生时间：{{ formatDateTime(v.occurredAt) }}</p>
                </div>
              </div>
            </div>
            <div v-if="violationTotal > 0 && violationPages > 1" class="violation-pagination">
              <button
                type="button"
                class="page-btn"
                :disabled="violationPage <= 1 || loadingViolations"
                @click="goViolationPage(violationPage - 1)"
              >
                上一页
              </button>
              <span class="page-info">
                第 {{ violationPage }} / {{ violationPages }} 页 · 每页 {{ violationPageSize }} 条 · 共 {{ violationTotal }} 条
              </span>
              <button
                type="button"
                class="page-btn"
                :disabled="violationPage >= violationPages || loadingViolations"
                @click="goViolationPage(violationPage + 1)"
              >
                下一页
              </button>
            </div>
          </div>
        </div>

        <!-- 注销账户（与管理端删除用户规则一致：进行中预约/未还书/任小组组长时不可注销） -->
        <div class="card danger-zone">
          <h3>注销账户</h3>
          <p class="danger-hint">
            注销后将永久删除您的账号及可清理的历史数据，且不可恢复。若存在进行中的预约、未归还的借阅，或您是学习小组组长，需先处理完毕。
          </p>
          <label>
            <span>当前密码（用于确认）</span>
            <input
              v-model="deleteAccountPassword"
              type="password"
              autocomplete="current-password"
              placeholder="输入登录密码"
            />
          </label>
          <button
            type="button"
            class="btn-danger"
            :disabled="deletingAccount || !deleteAccountPassword.trim()"
            @click="confirmDeleteAccount"
          >
            {{ deletingAccount ? '处理中...' : '注销账户' }}
          </button>
          <p v-if="deleteAccountErr" class="err">{{ deleteAccountErr }}</p>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '../lib/http'
import { useAuthStore } from '../stores/auth'

const profile = ref<any>(null)
const loading = ref(true)
const auth = useAuthStore()
const router = useRouter()
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const changing = ref(false)
const changeMsg = ref('')
const changeErr = ref('')

const accountForm = ref({ username: '', email: '', phone: '' })
const savingAccount = ref(false)
const accountMsg = ref('')
const accountErr = ref('')

const USERNAME_PATTERN = /^[a-zA-Z0-9_]{1,50}$/
const EMAIL_PATTERN = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/
const PHONE_PATTERN = /^1[3-9]\d{9}$/

const isPresetAdmin = computed(() => profile.value?.username === 'admin')

const adminOverview = ref<{
  usersTotal: number | null
  seatsTotal: number | null
  pendingReservations: number | null
  unhandledViolations: number | null
}>({
  usersTotal: null,
  seatsTotal: null,
  pendingReservations: null,
  unhandledViolations: null
})
const adminOverviewErr = ref('')

// 头像相关
const currentAvatarUrl = ref<string | null>(null)
const avatarFile = ref<File | null>(null)
const avatarPreview = ref<string | null>(null)
const avatarInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const avatarMsg = ref('')
const avatarErr = ref('')
const avatarLoadError = ref(false)

// 违规记录相关（列表分页，统计仍用全量接口）
const violations = ref<any[]>([])
const violationStats = ref<any>(null)
const loadingViolations = ref(false)
const violationPage = ref(1)
const violationPageSize = ref(10)
const violationTotal = ref(0)
const violationPages = ref(1)

const deleteAccountPassword = ref('')
const deletingAccount = ref(false)
const deleteAccountErr = ref('')

const showViolations = computed(() => !auth.isAdmin)

const displayName = computed(() => {
  return profile.value?.username || auth.username || '-'
})

const isPasswordMatch = computed(() => {
  return newPassword.value && confirmPassword.value && newPassword.value === confirmPassword.value
})

function syncAccountForm() {
  if (!profile.value) return
  accountForm.value = {
    username: profile.value.username || '',
    email: profile.value.email || '',
    phone: profile.value.phone || ''
  }
}

async function saveAccountInfo() {
  accountMsg.value = ''
  accountErr.value = ''

  const uname = isPresetAdmin.value ? 'admin' : accountForm.value.username.trim()
  if (!isPresetAdmin.value) {
    if (!uname) {
      accountErr.value = '用户名不能为空'
      return
    }
    if (!USERNAME_PATTERN.test(uname)) {
      accountErr.value = '用户名只能包含字母、数字和下划线，长度不超过50个字符'
      return
    }
  }

  const email = accountForm.value.email.trim()
  if (!email) {
    accountErr.value = '邮箱不能为空'
    return
  }
  if (!EMAIL_PATTERN.test(email)) {
    accountErr.value = '邮箱格式不正确，请输入有效的邮箱地址'
    return
  }

  const phoneRaw = accountForm.value.phone.trim()
  if (phoneRaw && !PHONE_PATTERN.test(phoneRaw)) {
    accountErr.value = '手机号格式不正确，请输入11位中国大陆手机号'
    return
  }

  savingAccount.value = true
  try {
    const payload: Record<string, string> = {
      username: uname,
      email,
      phone: phoneRaw
    }
    const res = await http.put('/api/auth/profile', payload)
    const d = res.data || {}
    if (typeof d.token === 'string' && d.token) {
      auth.setAuth(d.token, String(d.username ?? uname))
    }
    profile.value = {
      ...profile.value,
      id: d.id ?? profile.value?.id,
      username: d.username ?? profile.value?.username,
      email: d.email,
      phone: d.phone,
      role: d.role ?? profile.value?.role,
      avatarUrl: d.avatarUrl ?? profile.value?.avatarUrl,
      createdAt: d.createdAt ?? profile.value?.createdAt
    }
    syncAccountForm()
    accountMsg.value = typeof d.message === 'string' ? d.message : '账号信息已更新'
    setTimeout(() => {
      accountMsg.value = ''
    }, 4000)
  } catch (e: any) {
    accountErr.value = e?.response?.data?.message || e?.message || '保存失败'
  } finally {
    savingAccount.value = false
  }
}

// 格式化日期时间
function formatDateTime(dateTime: string | null | undefined): string {
  if (!dateTime) return '-'
  try {
    const date = new Date(dateTime)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
  } catch {
    return dateTime
  }
}

/** profile 接口中的 serverStartTime 为毫秒时间戳 */
function formatServerStart(v: number | string | null | undefined): string {
  if (v == null || v === '') return '-'
  const n = typeof v === 'number' ? v : Number(v)
  if (!Number.isFinite(n)) return '-'
  try {
    return new Date(n).toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    })
  } catch {
    return '-'
  }
}

// 获取违规类型文本
function getViolationTypeText(type: string): string {
  const typeMap: Record<string, string> = {
    'LATE_CHECKIN': '迟到签到',
    'NO_SHOW': '未到',
    'NO_CHECKOUT': '未签退',
    'OVERTIME': '超时',
    'OTHER': '其他'
  }
  return typeMap[type] || type
}

// 加载违规记录（分页列表 + 全量统计）
async function loadViolations(page = violationPage.value) {
  loadingViolations.value = true
  try {
    const [violationsRes, statsRes] = await Promise.all([
      http.get('/api/violations/my', {
        params: { page, size: violationPageSize.value }
      }),
      http.get('/api/violations/my/stats')
    ])
    const data = violationsRes.data || {}
    violations.value = Array.isArray(data.records) ? data.records : []
    violationTotal.value = typeof data.total === 'number' ? data.total : 0
    violationPage.value = typeof data.page === 'number' ? data.page : page
    if (typeof data.size === 'number' && data.size > 0) {
      violationPageSize.value = data.size
    }
    const rawPages = typeof data.pages === 'number' ? data.pages : 0
    violationPages.value =
      violationTotal.value === 0 ? 1 : Math.max(1, rawPages)
    violationStats.value = statsRes.data || {}
  } catch (e: any) {
    console.error('加载违规记录失败', e)
    console.error('错误详情:', e.response?.data || e.message)
    violations.value = []
    violationStats.value = null
    violationTotal.value = 0
    violationPages.value = 1
    const errorMsg = e.response?.data?.message || e.message || '加载违规记录失败'
    alert(`加载违规记录失败：${errorMsg}\n\n请检查网络连接或联系管理员。`)
  } finally {
    loadingViolations.value = false
  }
}

function goViolationPage(p: number) {
  if (p < 1 || p > violationPages.value || p === violationPage.value) return
  loadViolations(p)
}

function handleAvatarError(event: Event) {
  console.error('头像加载失败:', currentAvatarUrl.value)
  avatarLoadError.value = true
  // 不设置默认头像，让占位符显示
}

function handleAvatarLoad() {
  avatarLoadError.value = false
  console.log('头像加载成功:', currentAvatarUrl.value)
}

function handleAvatarChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) {
    // 验证文件类型
    if (!file.type.startsWith('image/')) {
      avatarErr.value = '请选择图片文件'
      return
    }
    // 验证文件大小
    if (file.size > 5 * 1024 * 1024) {
      avatarErr.value = '头像文件大小不能超过5MB'
      return
    }
    avatarFile.value = file
    avatarErr.value = ''
    const reader = new FileReader()
    reader.onload = (e) => {
      avatarPreview.value = e.target?.result as string
    }
    reader.readAsDataURL(file)
  }
}

async function confirmUpload() {
  if (!avatarFile.value) return
  
  uploading.value = true
  avatarMsg.value = ''
  avatarErr.value = ''
  
  try {
    const formData = new FormData()
    formData.append('file', avatarFile.value)
    
    const res = await http.post('/api/auth/upload-avatar', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    
    const avatarUrl = res.data?.avatarUrl
    if (avatarUrl) {
      // 处理相对路径和绝对路径
      let fullAvatarUrl: string
      if (avatarUrl.startsWith('http://') || avatarUrl.startsWith('https://')) {
        fullAvatarUrl = avatarUrl
      } else if (avatarUrl.startsWith('/')) {
        // 使用相对路径，通过Vite代理访问
        fullAvatarUrl = avatarUrl
      } else {
        fullAvatarUrl = `/${avatarUrl}`
      }
      
      currentAvatarUrl.value = fullAvatarUrl
      avatarLoadError.value = false
      avatarMsg.value = '头像上传成功！'
      avatarPreview.value = null
      avatarFile.value = null
      if (avatarInput.value) {
        avatarInput.value.value = ''
      }
      // 重新加载用户信息
      const profileRes = await http.get('/api/auth/profile')
      profile.value = profileRes.data
      // 更新全局头像（触发App.vue中的头像更新）
      window.dispatchEvent(new CustomEvent('avatar-updated', { detail: { avatarUrl: fullAvatarUrl } }))
      console.log('头像上传成功，URL:', fullAvatarUrl)
    }
  } catch (e: any) {
    avatarErr.value = e?.response?.data?.message || '头像上传失败'
  } finally {
    uploading.value = false
  }
}

function cancelUpload() {
  avatarPreview.value = null
  avatarFile.value = null
  avatarErr.value = ''
  if (avatarInput.value) {
    avatarInput.value.value = ''
  }
}

async function removeAvatar() {
  if (!confirm('确认移除头像？移除后可以重新上传新头像。')) return
  
  uploading.value = true
  avatarMsg.value = ''
  avatarErr.value = ''
  
  try {
    const res = await http.post('/api/auth/remove-avatar')
    
    // 移除成功，清空头像URL
    currentAvatarUrl.value = null
    avatarLoadError.value = false
    avatarMsg.value = '头像已移除，您可以重新上传新头像'
    
    // 重新加载用户信息
    const profileRes = await http.get('/api/auth/profile')
    profile.value = profileRes.data
    syncAccountForm()
    
    // 更新全局头像（触发App.vue中的头像更新）
    window.dispatchEvent(new CustomEvent('avatar-updated', { detail: { avatarUrl: null } }))
    
    setTimeout(() => {
      avatarMsg.value = ''
    }, 3000)
  } catch (e: any) {
    console.error('移除头像失败:', e)
    // 如果是401或403错误，提示用户重新登录
    if (e?.response?.status === 401 || e?.response?.status === 403) {
      avatarErr.value = '登录已过期，请重新登录后再试'
      // 延迟跳转到登录页
      setTimeout(() => {
        auth.clear()
        router.replace({ name: 'login' })
      }, 2000)
    } else {
      avatarErr.value = e?.response?.data?.message || e?.message || '移除头像失败，请稍后重试'
    }
  } finally {
    uploading.value = false
  }
}

function reloadAvatar() {
  avatarLoadError.value = false
  // 强制重新加载图片
  if (currentAvatarUrl.value) {
    const url = currentAvatarUrl.value
    currentAvatarUrl.value = null
    // 使用时间戳强制刷新
    setTimeout(() => {
      currentAvatarUrl.value = url + (url.includes('?') ? '&' : '?') + '_t=' + Date.now()
    }, 100)
  }
}

async function loadAdminOverview() {
  adminOverviewErr.value = ''
  try {
    const [usersRes, seatsRes, pendingRes, vioStatsRes] = await Promise.all([
      http.get('/api/users'),
      http.get('/api/admin/seats', { params: { current: 1, size: 1 } }),
      http.get('/api/admin/reservations', { params: { current: 1, size: 1, status: 'PENDING' } }),
      http.get('/api/admin/violations/stats')
    ])

    const users = Array.isArray(usersRes.data) ? usersRes.data.length : null
    const seatsData = seatsRes.data || {}
    const seatsTotal =
      typeof seatsData.totalAll === 'number'
        ? seatsData.totalAll
        : typeof seatsData.total === 'number'
          ? seatsData.total
          : null

    const pendingData = pendingRes.data || {}
    const pendingTotal = typeof pendingData.total === 'number' ? pendingData.total : null

    const vioStats = vioStatsRes.data || {}
    const unhandled =
      typeof vioStats.unhandled === 'number'
        ? vioStats.unhandled
        : typeof vioStats.unhandledCount === 'number'
          ? vioStats.unhandledCount
          : null

    adminOverview.value = {
      usersTotal: users,
      seatsTotal,
      pendingReservations: pendingTotal,
      unhandledViolations: unhandled
    }
  } catch (e: any) {
    console.error('加载管理员概览失败', e)
    adminOverviewErr.value = '概览数据加载失败（不影响其他功能）'
  }
}

onMounted(async () => {
  try {
    const res = await http.get('/api/auth/profile')
    profile.value = res.data
    syncAccountForm()
    console.log('用户信息:', profile.value)
    console.log('头像URL:', profile.value?.avatarUrl)
    
    // 设置当前头像URL
    if (profile.value?.avatarUrl) {
      const avatarUrl = profile.value.avatarUrl
      // 处理相对路径和绝对路径
      if (avatarUrl.startsWith('http://') || avatarUrl.startsWith('https://')) {
        currentAvatarUrl.value = avatarUrl
      } else if (avatarUrl.startsWith('/')) {
        // 使用相对路径，通过Vite代理访问
        currentAvatarUrl.value = avatarUrl
      } else {
        currentAvatarUrl.value = `/${avatarUrl}`
      }
      avatarLoadError.value = false
      console.log('设置头像URL:', currentAvatarUrl.value, '原始URL:', avatarUrl)
    } else {
      console.log('用户没有头像')
      currentAvatarUrl.value = null
      avatarLoadError.value = false
    }
  } catch (e: any) {
    console.error('加载用户信息失败:', e)
  } finally {
    loading.value = false
  }
  // 仅普通用户加载违规记录
  if (showViolations.value) {
    loadViolations()
  }
  // 管理员加载右侧概览（用于填充与对齐）
  if (auth.isAdmin) {
    loadAdminOverview()
  }
})

async function confirmDeleteAccount() {
  deleteAccountErr.value = ''
  if (!deleteAccountPassword.value.trim()) {
    deleteAccountErr.value = '请输入当前密码'
    return
  }
  const ok = window.confirm(
    '确定要注销账户吗？此操作不可恢复。系统将校验您是否存在未完成预约、未归还图书或学习小组组长身份。'
  )
  if (!ok) return

  deletingAccount.value = true
  try {
    await http.delete('/api/auth/account', {
      data: { password: deleteAccountPassword.value }
    })
    auth.clear()
    router.replace({ name: 'login' })
  } catch (e: any) {
    deleteAccountErr.value =
      e?.response?.data?.message || e?.message || '注销失败，请稍后重试'
  } finally {
    deletingAccount.value = false
  }
}

async function changePassword() {
  changeMsg.value = ''
  changeErr.value = ''
  
  // 验证两次密码是否一致
  if (newPassword.value !== confirmPassword.value) {
    changeErr.value = '两次输入的密码不一致，请重新输入'
    return
  }
  
  // 验证密码长度
  if (newPassword.value.length < 6) {
    changeErr.value = '新密码长度至少为6位'
    return
  }
  
  changing.value = true
  try {
    await http.post('/api/auth/change-password', {
      oldPassword: oldPassword.value,
      newPassword: newPassword.value
    })
    changeMsg.value = '密码已更新，请重新登录'
    // 清理并跳转登录
    auth.clear()
    setTimeout(() => router.replace('/login'), 800)
  } catch (e: any) {
    changeErr.value = e?.message || e?.response?.data?.message || '修改失败'
  } finally {
    changing.value = false
  }
}
</script>


<style scoped src="../styles/views/Profile.css"></style>
