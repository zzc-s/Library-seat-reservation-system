<template>
  <section class="users-admin">
    <h2>用户管理</h2>
    <div class="actions">
      <button type="button" @click="openCreateUserDialog" class="btn-create">+ 新增用户</button>
    </div>
    <div class="search-bar-container">
      <div class="search-bar">
        <input
          v-model="searchKeyword"
          type="text"
          placeholder="用户ID或用户名查询"
          class="search-input"
        />
        <span class="search-info" v-if="searchKeyword">
          已筛选：{{ filteredBySearch.length }} / {{ allUsers.length }} 条
        </span>
      </div>
      <!-- 分页控制栏 -->
      <div class="pagination-controls">
        <div class="page-size-selector">
          <label>每页显示：</label>
          <select v-model.number="pageSize" @change="handlePageSizeChange">
            <option :value="10">10</option>
            <option :value="20">20</option>
            <option :value="50">50</option>
            <option :value="100">100</option>
          </select>
          <span class="total-info">
            共 {{ total }} 条记录
          </span>
        </div>
      </div>
    </div>
    <div class="table-container">
      <table border="1" cellspacing="0" cellpadding="6">
      <thead>
        <tr>
          <th class="text-center">ID</th>
          <th class="text-center">用户名</th>
          <th class="text-center">邮箱</th>
          <th class="text-center">角色</th>
          <th class="text-center">冻结</th>
          <th class="text-center">黑名单</th>
          <th class="text-center">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="u in filteredList" :key="u.id">
          <td class="text-center">{{ u.id }}</td>
          <td class="text-center">{{ u.username }}</td>
          <td class="text-center">{{ u.email || '-' }}</td>
          <td class="text-center">{{ u.role === 'ADMIN' ? '管理员' : '普通用户' }}</td>
          <td class="text-center">{{ u.isFrozen ? '是' : '否' }}</td>
          <td class="text-center">
            <span v-if="u.isBlacklisted" class="blacklist-badge">🚫 是</span>
            <span v-else>否</span>
          </td>
          <td class="action-cell text-center">
            <div class="action-buttons-container">
              <button 
                @click="viewUserDetail(u.id)" 
                class="btn-action btn-view"
              >
                查看详情
              </button>
              <button 
                v-if="u.username !== 'admin'" 
                @click="editUser(u.id)" 
                class="btn-action btn-edit"
              >
                编辑信息
              </button>
              <!-- 不能冻结自己，也不能对预置 admin 显示冻结/解冻按钮 -->
              <button 
                v-if="u.username !== auth.username && u.username !== 'admin'" 
                @click="freeze(u.id)" 
                :disabled="u.isFrozen"
                class="btn-action btn-freeze"
              >
                冻结
              </button>
              <button 
                v-if="u.username !== auth.username && u.username !== 'admin'" 
                @click="unfreeze(u.id)" 
                :disabled="!u.isFrozen"
                class="btn-action btn-unfreeze"
              >
                解冻
              </button>
              <!-- 只有普通用户才显示"提升为管理员"按钮 -->
              <template v-if="u.role !== 'ADMIN'">
                <button 
                  @click="promote(u.id)" 
                  class="btn-action btn-promote"
                >
                  提升为管理员
                </button>
                <!-- 删除按钮紧跟在提升为管理员后面 -->
                <button 
                  v-if="u.username !== 'admin'" 
                  @click="deleteUser(u.id)" 
                  class="btn-action btn-delete"
                >
                  删除
                </button>
              </template>
              <!-- 管理员：只有预置 admin 可以降级其他管理员（但不能降级自己或预置 admin） -->
              <button 
                v-else-if="u.role === 'ADMIN' && isSuperAdmin && u.username !== 'admin' && u.username !== auth.username"
                @click="demote(u.id)" 
                class="btn-action btn-demote"
              >
                降级为普通用户
              </button>
              <span 
                v-else-if="u.role === 'ADMIN' && (!isSuperAdmin || u.username === 'admin' || u.username === auth.username)"
                class="role-label"
              >
                {{ u.username === 'admin' ? '预置管理员' : '管理员' }}
              </span>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
    </div>
    
    <!-- 分页导航 -->
    <div class="pagination" v-if="computedTotalPages > 1">
      <button 
        class="pagination-btn" 
        :disabled="currentPage === 1" 
        @click="goToPage(1)">
        首页
      </button>
      <button 
        class="pagination-btn" 
        :disabled="currentPage === 1" 
        @click="goToPage(currentPage - 1)">
        上一页
      </button>
      
      <span class="page-info">
        第 
        <input 
          type="number" 
          v-model.number="inputPage" 
          @keyup.enter="goToInputPage"
          @blur="goToInputPage"
          class="page-input"
          :min="1" 
          :max="computedTotalPages" 
        />
        页 / 共 {{ computedTotalPages }} 页
      </span>
      
      <button 
        class="pagination-btn" 
        :disabled="currentPage === computedTotalPages" 
        @click="goToPage(currentPage + 1)">
        下一页
      </button>
      <button 
        class="pagination-btn" 
        :disabled="currentPage === computedTotalPages" 
        @click="goToPage(computedTotalPages)">
        末页
      </button>
    </div>
    
    <!-- 用户详情对话框 -->
    <div v-if="showDetailDialog" class="dialog-overlay" @click="showDetailDialog = false">
      <div class="user-detail-dialog" @click.stop>
        <div class="dialog-header">
          <h3>用户详情</h3>
          <button @click="showDetailDialog = false" class="close-btn">×</button>
        </div>
        <div v-if="userDetail" class="user-detail-content">
          <div class="detail-section">
            <h4>基本信息</h4>
            <div class="detail-grid">
              <div class="detail-item">
                <label>用户ID</label>
                <span>{{ userDetail.id }}</span>
              </div>
              <div class="detail-item">
                <label>用户名</label>
                <span>{{ userDetail.username }}</span>
              </div>
              <div class="detail-item">
                <label>邮箱</label>
                <span>{{ userDetail.email || '-' }}</span>
              </div>
              <div class="detail-item">
                <label>电话</label>
                <span>{{ userDetail.phone || '-' }}</span>
              </div>
              <div class="detail-item">
                <label>角色</label>
                <span>{{ userDetail.role === 'ADMIN' ? '管理员' : '普通用户' }}</span>
              </div>
              <div class="detail-item">
                <label>冻结状态</label>
                <span :class="userDetail.isFrozen ? 'status-frozen' : 'status-normal'">
                  {{ userDetail.isFrozen ? '已冻结' : '正常' }}
                </span>
              </div>
              <div class="detail-item">
                <label>黑名单状态</label>
                <span :class="userDetail.isBlacklisted ? 'status-blacklisted' : 'status-normal'">
                  {{ userDetail.isBlacklisted ? '🚫 已加入黑名单' : '正常' }}
                </span>
              </div>
              <div class="detail-item">
                <label>创建时间</label>
                <span>{{ formatDateTime(userDetail.createdAt) }}</span>
              </div>
              <div class="detail-item">
                <label>更新时间</label>
                <span>{{ formatDateTime(userDetail.updatedAt) }}</span>
              </div>
            </div>
          </div>
          <div class="detail-section" v-if="userDetail.statistics">
            <h4>统计信息</h4>
            <div class="detail-grid">
              <div class="detail-item">
                <label>预约次数</label>
                <span>{{ userDetail.statistics.reservationCount || 0 }}</span>
              </div>
              <div class="detail-item">
                <label>违规次数</label>
                <span>{{ userDetail.statistics.violationCount || 0 }}</span>
              </div>
              <div class="detail-item">
                <label>借阅次数</label>
                <span>{{ userDetail.statistics.borrowCount || 0 }}</span>
              </div>
              <div class="detail-item">
                <label>反馈次数</label>
                <span>{{ userDetail.statistics.feedbackCount || 0 }}</span>
              </div>
            </div>
          </div>
          <div class="dialog-actions">
            <button @click="showDetailDialog = false" class="btn-cancel">关闭</button>
            <button v-if="userDetail.username !== 'admin'" @click="editUserFromDetail" class="btn-submit">编辑信息</button>
          </div>
        </div>
        <div v-else class="loading">加载中...</div>
      </div>
    </div>
    
    <!-- 编辑用户对话框 -->
    <div v-if="showEditDialog" class="dialog-overlay" @click="showEditDialog = false">
      <div class="create-user-dialog" @click.stop>
        <div class="dialog-header">
          <h3>编辑用户信息</h3>
          <button @click="showEditDialog = false" class="close-btn">×</button>
        </div>
        <form @submit.prevent="updateUser" class="create-user-form">
          <div class="form-item">
            <label>用户名 <span class="required">*</span></label>
            <input 
              v-model.trim="editingUser.username" 
              type="text" 
              required 
              placeholder="支持字母、数字、下划线"
              autocomplete="off"
            />
            <small class="field-hint">用户名：支持字母、数字、下划线</small>
          </div>
          <div class="form-item">
            <label>邮箱 <span class="required">*</span></label>
            <input 
              v-model.trim="editingUser.email" 
              type="email" 
              required 
              placeholder="例如：user@example.com"
              autocomplete="email"
            />
            <small class="field-hint">请输入有效的邮箱地址</small>
          </div>
          <div class="form-item">
            <label>手机号</label>
            <input 
              v-model.trim="editingUser.phone" 
              type="tel" 
              maxlength="11"
              inputmode="numeric"
              placeholder="11位中国大陆手机号"
              autocomplete="tel"
            />
            <small class="field-hint">若填写，须为 11 位中国大陆有效号码，且不可与他人重复</small>
          </div>
          <div class="form-item">
            <label>角色</label>
            <select v-model="editingUser.role">
              <option value="USER">普通用户</option>
              <option value="ADMIN">管理员</option>
            </select>
          </div>
          <div class="form-item">
            <label>重置密码</label>
            <input 
              v-model="editingUser.password" 
              type="password" 
              placeholder="留空则不修改密码"
              autocomplete="new-password"
            />
            <small class="field-hint">如需修改密码，请输入新密码（6-20个字符）</small>
          </div>
          <div v-if="editError" class="error-message">{{ editError }}</div>
          <div class="dialog-actions">
            <button type="button" @click="showEditDialog = false" class="btn-cancel">取消</button>
            <button type="submit" :disabled="updating" class="btn-submit">
              {{ updating ? '更新中...' : '更新' }}
            </button>
          </div>
        </form>
      </div>
    </div>
    
    <!-- 新增用户对话框 -->
    <div v-if="showCreateDialog" class="dialog-overlay" @click="showCreateDialog = false">
      <div class="create-user-dialog" @click.stop>
        <div class="dialog-header">
          <h3>新增用户</h3>
          <button @click="showCreateDialog = false" class="close-btn">×</button>
        </div>
        <form @submit.prevent="createUser" class="create-user-form">
          <div class="form-item">
            <label>用户名 <span class="required">*</span></label>
            <input 
              v-model.trim="newUser.username" 
              type="text" 
              required 
              placeholder="支持字母、数字、下划线"
              autocomplete="off"
            />
            <small class="field-hint">用户名：支持字母、数字、下划线</small>
          </div>
          <div class="form-item">
            <label>密码 <span class="required">*</span></label>
            <input 
              v-model="newUser.password" 
              type="password" 
              required 
              placeholder="6-20个字符"
              autocomplete="new-password"
            />
            <small class="field-hint">密码：6-20个字符</small>
          </div>
          <div class="form-item">
            <label>邮箱 <span class="required">*</span></label>
            <input 
              v-model.trim="newUser.email" 
              type="email" 
              required 
              placeholder="例如：user@example.com"
              autocomplete="email"
            />
            <small class="field-hint">须为真实可用邮箱：填写后发送验证码，用户收信后由管理员代为录入验证码完成创建</small>
          </div>
          <div class="form-item form-item-code">
            <label>邮箱验证码 <span class="required">*</span></label>
            <div class="code-inline">
              <input
                v-model.trim="createEmailCode"
                class="code-field-input"
                type="text"
                required
                placeholder="6位数字"
                maxlength="6"
                inputmode="numeric"
                autocomplete="one-time-code"
              />
              <button
                type="button"
                class="btn-send-code"
                :disabled="sendingCreateCode || createCodeCooldown > 0 || !newUser.email"
                @click="sendCreateUserEmailCode"
              >
                {{ createCodeCooldown > 0 ? `重新发送(${createCodeCooldown}s)` : (sendingCreateCode ? '发送中...' : '发送验证码') }}
              </button>
            </div>
            <small class="field-hint">与自助注册相同：先向该邮箱发送验证码，再填写</small>
          </div>
          <div class="form-item">
            <label>手机号</label>
            <input 
              v-model.trim="newUser.phone" 
              type="tel" 
              maxlength="11"
              inputmode="numeric"
              placeholder="11位中国大陆手机号"
              autocomplete="tel"
            />
            <small class="field-hint">若填写，须为 11 位中国大陆有效号码，且不可与他人重复</small>
          </div>
          <div class="form-item">
            <label>角色</label>
            <select v-model="newUser.role">
              <option value="USER">普通用户</option>
              <option value="ADMIN">管理员</option>
            </select>
          </div>
          <div v-if="createError" class="error-message">{{ createError }}</div>
          <div class="dialog-actions">
            <button type="button" @click="showCreateDialog = false" class="btn-cancel">取消</button>
            <button type="submit" :disabled="creating" class="btn-submit">
              {{ creating ? '创建中...' : '创建' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import http from '../../lib/http'
import { useAuthStore } from '../../stores/auth'

const list = ref<any[]>([])
const allUsers = ref<any[]>([]) // 保存所有用户数据用于前端分页
const searchKeyword = ref('')
const auth = useAuthStore()
const showCreateDialog = ref(false)
const showEditDialog = ref(false)
const showDetailDialog = ref(false)
const creating = ref(false)
const updating = ref(false)
const createError = ref('')
const editError = ref('')
const userDetail = ref<any>(null)
const editingUserId = ref<number | null>(null)
const createEmailCode = ref('')
const sendingCreateCode = ref(false)
const createCodeCooldown = ref(0)
let createCooldownTimer: ReturnType<typeof setInterval> | null = null
const newUser = ref({
  username: '',
  password: '',
  email: '',
  phone: '',
  role: 'USER'
})
const editingUser = ref({
  username: '',
  email: '',
  phone: '',
  role: 'USER',
  password: ''
})

// 分页相关
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const totalPages = ref(0)
const inputPage = ref(1)

// 判断当前登录用户是否是预置的 admin
const isSuperAdmin = computed(() => auth.username === 'admin')

/** 与注册页一致：11 位中国大陆手机号 */
const phonePattern = /^1[3-9]\d{9}$/
const emailPattern = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/

function resetCreateUserFormState() {
  createError.value = ''
  createEmailCode.value = ''
  newUser.value = {
    username: '',
    password: '',
    email: '',
    phone: '',
    role: 'USER'
  }
  if (createCooldownTimer) {
    clearInterval(createCooldownTimer)
    createCooldownTimer = null
  }
  createCodeCooldown.value = 0
}

function openCreateUserDialog() {
  resetCreateUserFormState()
  showCreateDialog.value = true
}

async function sendCreateUserEmailCode() {
  createError.value = ''
  if (!newUser.value.email || !emailPattern.test(newUser.value.email)) {
    createError.value = '请先输入有效邮箱'
    return
  }
  sendingCreateCode.value = true
  try {
    await http.post('/api/admin/users/create-account-code', { email: newUser.value.email })
    createCodeCooldown.value = 60
    if (createCooldownTimer) clearInterval(createCooldownTimer)
    createCooldownTimer = setInterval(() => {
      createCodeCooldown.value--
      if (createCodeCooldown.value <= 0 && createCooldownTimer) {
        clearInterval(createCooldownTimer)
        createCooldownTimer = null
      }
    }, 1000)
  } catch (e: any) {
    createError.value = e?.message || e?.response?.data?.message || '发送验证码失败'
  } finally {
    sendingCreateCode.value = false
  }
}

// 先过滤搜索关键词（纯数字 = 只按用户主键 ID 精确匹配；否则匹配用户名子串，不匹配邮箱以免与「ID/用户名」提示不符）
const filteredBySearch = computed(() => {
  const raw = searchKeyword.value.trim()
  if (!raw) return allUsers.value
  const kw = raw.toLowerCase()
  const digitsOnly = /^\d+$/.test(raw)
  return allUsers.value.filter(u => {
    if (digitsOnly) {
      return String(u.id ?? '') === raw
    }
    const nameMatch = (u.username || '').toLowerCase().includes(kw)
    const idSubMatch = String(u.id || '').includes(kw)
    return nameMatch || idSubMatch
  })
})

// 再分页
const filteredList = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredBySearch.value.slice(start, end)
})

// 计算总页数
const computedTotalPages = computed(() => {
  return Math.max(1, Math.ceil(filteredBySearch.value.length / pageSize.value))
})

async function load() {
  try {
    console.log('正在加载用户列表...')
    console.log('当前登录状态:', auth.isAuthenticated, '角色:', auth.role, '用户名:', auth.username)
    const res = await http.get('/api/users')
    allUsers.value = res.data
    total.value = res.data.length
    // totalPages 由 computedTotalPages 自动计算
    inputPage.value = currentPage.value
    console.log('用户列表加载成功，共', res.data.length, '个用户')
  } catch (e: any) {
    console.error('加载用户列表失败:', e)
    if (e?.response?.status === 401) {
      alert('登录已过期，请重新登录')
    } else if (e?.response?.status === 403) {
      alert('权限不足，只有管理员可以查看用户列表')
    } else {
      alert(e?.message || '加载失败')
    }
  }
}

function handlePageSizeChange() {
  currentPage.value = 1
  inputPage.value = 1
}

function goToPage(page: number) {
  if (page < 1 || page > computedTotalPages.value) return
  currentPage.value = page
  inputPage.value = page
  // 滚动到表格顶部
  const tableContainer = document.querySelector('.table-container')
  if (tableContainer) {
    tableContainer.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

function goToInputPage() {
  const page = parseInt(String(inputPage.value))
  if (isNaN(page) || page < 1) {
    inputPage.value = currentPage.value
    return
  }
  if (page > computedTotalPages.value) {
    inputPage.value = computedTotalPages.value
    goToPage(computedTotalPages.value)
    return
  }
  goToPage(page)
}

async function freeze(id: number) {
  await http.post(`/api/admin/users/${id}/freeze`)
  const u = allUsers.value.find(x => x.id === id)
  if (u) u.isFrozen = true
}

async function unfreeze(id: number) {
  await http.post(`/api/admin/users/${id}/unfreeze`)
  const u = allUsers.value.find(x => x.id === id)
  if (u) u.isFrozen = false
}

async function promote(id: number) {
  if (!confirm('确定要将此用户提升为管理员吗？管理员拥有系统所有权限。')) {
    return
  }
  try {
    await http.post(`/api/admin/users/${id}/promote`)
    const u = allUsers.value.find(x => x.id === id)
    if (u) u.role = 'ADMIN'
    alert('已成功提升为管理员')
  } catch (e: any) {
    alert(e?.response?.data?.message || '操作失败')
  }
}

async function demote(id: number) {
  if (!confirm('确定要将此管理员降级为普通用户吗？')) {
    return
  }
  try {
    await http.post(`/api/admin/users/${id}/demote`)
    const u = allUsers.value.find(x => x.id === id)
    if (u) u.role = 'USER'
    alert('已成功降级为普通用户')
  } catch (e: any) {
    alert(e?.response?.data?.message || '操作失败')
  }
}

async function viewUserDetail(id: number) {
  try {
    const res = await http.get(`/api/admin/users/${id}`)
    userDetail.value = res.data
    showDetailDialog.value = true
  } catch (e: any) {
    alert(e?.response?.data?.message || '获取用户详情失败')
    console.error('获取用户详情失败:', e)
  }
}

function editUserFromDetail() {
  if (userDetail.value) {
    editUser(userDetail.value.id)
  }
}

async function editUser(id: number) {
  try {
    // 获取用户详情
    const res = await http.get(`/api/admin/users/${id}`)
    const user = res.data
    
    editingUserId.value = id
    editingUser.value = {
      username: user.username || '',
      email: user.email || '',
      phone: user.phone || '',
      role: user.role || 'USER',
      password: '' // 密码留空，不修改
    }
    showEditDialog.value = true
    showDetailDialog.value = false // 关闭详情对话框
  } catch (e: any) {
    alert(e?.response?.data?.message || '获取用户信息失败')
    console.error('获取用户信息失败:', e)
  }
}

async function updateUser() {
  editError.value = ''
  
  if (!editingUser.value.username || !editingUser.value.email) {
    editError.value = '用户名和邮箱不能为空'
    return
  }
  
  // 验证用户名格式
  const usernamePattern = /^[a-zA-Z0-9_]{1,50}$/
  if (!usernamePattern.test(editingUser.value.username)) {
    editError.value = '用户名只能包含字母、数字和下划线，长度不超过50个字符'
    return
  }
  
  // 验证邮箱格式
  const emailPattern = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/
  if (!emailPattern.test(editingUser.value.email)) {
    editError.value = '邮箱格式不正确，请输入有效的邮箱地址'
    return
  }

  const editPhone = (editingUser.value.phone || '').trim()
  if (editPhone && !phonePattern.test(editPhone)) {
    editError.value = '手机号格式不正确，请输入11位中国大陆手机号'
    return
  }
  
  // 如果提供了密码，验证密码长度
  if (editingUser.value.password && 
      (editingUser.value.password.length < 6 || editingUser.value.password.length > 20)) {
    editError.value = '密码长度必须在6-20个字符之间'
    return
  }
  
  updating.value = true
  try {
    const updateData: any = {
      username: editingUser.value.username,
      email: editingUser.value.email,
      role: editingUser.value.role
    }
    
    if (editingUser.value.phone !== undefined) {
      updateData.phone = editPhone || null
    }
    
    // 只有提供了密码才更新
    if (editingUser.value.password && editingUser.value.password.trim()) {
      updateData.password = editingUser.value.password
    }
    
    await http.put(`/api/admin/users/${editingUserId.value}`, updateData)
    
    // 重置表单
    editingUser.value = {
      username: '',
      email: '',
      phone: '',
      role: 'USER',
      password: ''
    }
    editingUserId.value = null
    showEditDialog.value = false
    
    // 刷新用户列表
    await load()
    alert('用户信息更新成功')
  } catch (e: any) {
    const errorMessage = e?.response?.data?.message
    if (errorMessage) {
      editError.value = errorMessage
    } else if (e?.response?.status === 400) {
      editError.value = '请求参数错误，请检查输入信息'
    } else if (e?.response?.status === 500) {
      editError.value = '服务器错误，请稍后重试'
    } else {
      editError.value = '更新失败：' + (e?.message || '未知错误')
    }
  } finally {
    updating.value = false
  }
}

function formatDateTime(dateTime: string | null | undefined): string {
  if (!dateTime) return '-'
  try {
    const date = new Date(dateTime)
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    const hours = String(date.getHours()).padStart(2, '0')
    const minutes = String(date.getMinutes()).padStart(2, '0')
    return `${year}-${month}-${day} ${hours}:${minutes}`
  } catch (e) {
    return dateTime
  }
}

async function deleteUser(id: number) {
  if (!confirm('确定要删除此用户吗？删除后该用户需要重新注册才能使用系统。此操作不可恢复！')) {
    return
  }
  try {
    await http.delete(`/api/admin/users/${id}`)
    // 从列表中移除已删除的用户
    allUsers.value = allUsers.value.filter(u => u.id !== id)
    total.value = allUsers.value.length
    // 如果当前页没有数据了，跳转到上一页
    if (filteredList.value.length === 0 && currentPage.value > 1) {
      currentPage.value = currentPage.value - 1
      inputPage.value = currentPage.value
    }
    alert('用户已删除')
  } catch (e: any) {
    // http.ts 拦截器已经提取了 message，直接使用 e.message
    // 如果拦截器没有处理，则尝试从 response 中获取
    const errorMessage = e?.message || e?.response?.data?.message || '删除失败'
    alert(errorMessage)
    console.error('删除用户失败:', e)
  }
}

async function createUser() {
  createError.value = ''
  
  if (!newUser.value.username || !newUser.value.password || !newUser.value.email) {
    createError.value = '请填写所有必填项'
    return
  }
  if (!createEmailCode.value) {
    createError.value = '请填写邮箱验证码'
    return
  }
  
  const usernamePattern = /^[a-zA-Z0-9_]{1,50}$/
  if (!usernamePattern.test(newUser.value.username)) {
    createError.value = '用户名只能包含字母、数字和下划线，长度不超过50个字符'
    return
  }
  
  if (newUser.value.password.length < 6 || newUser.value.password.length > 20) {
    createError.value = '密码长度必须在6-20个字符之间'
    return
  }
  
  if (!emailPattern.test(newUser.value.email)) {
    createError.value = '邮箱格式不正确，请输入有效的邮箱地址'
    return
  }

  if (!/^\d{6}$/.test(createEmailCode.value)) {
    createError.value = '邮箱验证码应为6位数字'
    return
  }

  const newPhone = (newUser.value.phone || '').trim()
  if (newPhone && !phonePattern.test(newPhone)) {
    createError.value = '手机号格式不正确，请输入11位中国大陆手机号'
    return
  }
  
  creating.value = true
  try {
    await http.post('/api/admin/users', {
      username: newUser.value.username,
      password: newUser.value.password,
      email: newUser.value.email,
      code: createEmailCode.value,
      phone: newPhone || null,
      role: newUser.value.role
    })
    resetCreateUserFormState()
    showCreateDialog.value = false
    await load()
    alert('用户创建成功')
  } catch (e: any) {
    // 显示后端返回的详细错误信息
    const errorMessage = e?.response?.data?.message
    if (errorMessage) {
      createError.value = errorMessage
    } else if (e?.response?.status === 400) {
      createError.value = '请求参数错误，请检查输入信息'
    } else if (e?.response?.status === 500) {
      createError.value = '服务器错误，请稍后重试'
    } else {
      createError.value = '创建失败：' + (e?.message || '未知错误')
    }
  } finally {
    creating.value = false
  }
}

load()
</script>

<style scoped src="../../styles/views/admin/UsersAdmin.css"></style>


