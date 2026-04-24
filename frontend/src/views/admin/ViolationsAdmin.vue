<template>
  <div class="violations-admin-page">
    <div class="violations-container">
      <!-- 页面标题 -->
      <div class="page-header">
        <h2 class="page-title">
          <span class="icon">⚠️</span>
          违规管理
        </h2>
      </div>

      <!-- 统计信息卡片 -->
      <div class="stats-section">
        <div class="stat-card stat-total" @click="filterByAll" title="点击查看全部违规">
          <div class="stat-icon">📊</div>
          <div class="stat-content">
            <div class="stat-label">总违规数</div>
            <div class="stat-value">{{ stats.total || 0 }}</div>
          </div>
        </div>
        <div class="stat-card stat-unhandled" @click="filterByUnhandled" title="点击查看待处理违规">
          <div class="stat-icon">⏳</div>
          <div class="stat-content">
            <div class="stat-label">待处理</div>
            <div class="stat-value">{{ stats.unhandled || 0 }}</div>
          </div>
        </div>
        <div class="stat-card stat-handled" @click="filterByHandled" title="点击查看已处理违规">
          <div class="stat-icon">✅</div>
          <div class="stat-content">
            <div class="stat-label">已处理</div>
            <div class="stat-value">{{ stats.handled || 0 }}</div>
          </div>
        </div>
      </div>

      <!-- 筛选区域 -->
      <div class="filter-section">
        <h3 class="section-title">筛选条件</h3>
        <div class="filter-grid">
          <div class="filter-item">
            <label>用户</label>
            <input 
              v-model.trim="filters.userSearch" 
              type="text" 
              placeholder="用户ID 或 用户名"
              class="filter-input"
              autocomplete="off"
            />
          </div>
          <div class="filter-item">
            <label>违规类型</label>
            <select v-model="filters.type" class="filter-input">
              <option value="">全部类型</option>
              <option value="LATE_CHECKIN">迟到签到</option>
              <option value="NO_SHOW">未到</option>
              <option value="OVERTIME">超时</option>
              <option value="OTHER">其他</option>
            </select>
          </div>
          <div class="filter-item">
            <label>处理状态</label>
            <select v-model="filters.handled" class="filter-input">
              <option :value="null">全部</option>
              <option :value="false">待处理</option>
              <option :value="true">已处理</option>
            </select>
          </div>
          <div class="filter-item">
            <label>开始日期</label>
            <input 
              v-model="filters.from" 
              type="date" 
              class="filter-input"
            />
          </div>
          <div class="filter-item">
            <label>结束日期</label>
            <input 
              v-model="filters.to" 
              type="date" 
              class="filter-input"
            />
          </div>
          <div class="filter-actions">
            <button @click="resetFilters" class="btn btn-secondary">重置</button>
            <button @click="exportExcel" class="btn btn-info">导出Excel</button>
          </div>
        </div>
      </div>

      <!-- 批量操作栏 -->
      <div v-if="selectedIds.length > 0" class="batch-actions">
        <span class="batch-info">已选择 <strong>{{ selectedIds.length }}</strong> 条记录</span>
        <div class="batch-buttons">
          <button @click="batchHandle" class="btn btn-success btn-small">批量标记已处理</button>
          <button @click="batchAddToBlacklist" class="btn btn-warning btn-small">批量加入黑名单</button>
          <button @click="batchDelete" class="btn btn-danger btn-small">批量删除</button>
          <button @click="clearSelection" class="btn btn-secondary btn-small">取消选择</button>
        </div>
      </div>

      <!-- 消息提示 -->
      <div v-if="success" class="message success-message">
        <span>{{ success }}</span>
        <button @click="success = ''" class="close-btn">×</button>
      </div>
      <div v-if="error" class="message error-message">
        <span>{{ error }}</span>
        <button @click="error = ''" class="close-btn">×</button>
      </div>

      <!-- 违规列表 -->
      <div class="violations-section">
        <div class="section-header">
          <h3 class="section-title">违规记录（共 {{ total }} 条）</h3>
        </div>

        <div v-if="total > 0" class="pagination-bar">
          <div class="pagination-left">
            <label>每页显示</label>
            <select v-model.number="pageSize" class="page-size-select" @change="onPageSizeChange">
              <option :value="10">10</option>
              <option :value="20">20</option>
              <option :value="50">50</option>
            </select>
            <span class="total-info">本页 {{ list.length }} 条</span>
          </div>
          <div class="pagination-nav">
            <button type="button" class="btn btn-secondary btn-small" :disabled="currentPage <= 1" @click="goPage(1)">
              首页
            </button>
            <button type="button" class="btn btn-secondary btn-small" :disabled="currentPage <= 1" @click="goPage(currentPage - 1)">
              上一页
            </button>
            <span class="page-indicator">第 {{ currentPage }} / {{ totalPages }} 页</span>
            <button
              type="button"
              class="btn btn-secondary btn-small"
              :disabled="currentPage >= totalPages"
              @click="goPage(currentPage + 1)"
            >
              下一页
            </button>
            <button
              type="button"
              class="btn btn-secondary btn-small"
              :disabled="currentPage >= totalPages"
              @click="goPage(totalPages)"
            >
              末页
            </button>
          </div>
        </div>

        <div v-if="list.length === 0" class="empty-state">
          <p>暂无违规记录</p>
        </div>

        <div v-else class="table-container">
          <table class="violations-table">
            <thead>
              <tr>
                <th width="50">
                  <input 
                    type="checkbox" 
                    :checked="allSelected"
                    @change="toggleAll"
                  />
                </th>
                <th>ID</th>
                <th>用户</th>
                <th>预约ID</th>
                <th>违规类型</th>
                <th>描述</th>
                <th>发生时间</th>
                <th>处理状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr 
                v-for="v in list" 
                :key="v.id"
                :class="{ 'selected': selectedIds.includes(v.id), 'handled': v.handled }"
              >
                <td>
                  <input 
                    type="checkbox" 
                    :value="v.id"
                    v-model="selectedIds"
                  />
                </td>
                <td>{{ v.id }}</td>
                <td>
                  <div class="user-info">
                    <span class="user-id">#{{ v.userId }}</span>
                    <span class="username">
                      {{ v.username || '未知用户' }}
                      <span v-if="v.isBlacklisted" class="blacklist-badge">🚫 黑名单</span>
                    </span>
                  </div>
                </td>
                <td>{{ v.reservationId || '-' }}</td>
                <td>
                  <select v-model="v.type" class="inline-select" @change="update(v)">
                    <option value="LATE_CHECKIN">迟到签到</option>
                    <option value="NO_SHOW">未到</option>
                    <option value="OVERTIME">超时</option>
                    <option value="OTHER">其他</option>
                  </select>
                </td>
                <td>
                  <input 
                    v-model="v.description" 
                    class="inline-input"
                    @blur="update(v)"
                    placeholder="无描述"
                  />
                </td>
                <td>{{ formatDateTime(v.occurredAt) }}</td>
                <td>
                  <span :class="['status-badge', v.handled ? 'status-handled' : 'status-unhandled']">
                    {{ v.handled ? '已处理' : '待处理' }}
                  </span>
                </td>
                <td>
                  <div class="action-buttons">
                    <button 
                      @click="toggleHandle(v)" 
                      class="btn btn-small"
                      :class="v.handled ? 'btn-secondary' : 'btn-success'"
                    >
                      {{ v.handled ? '取消处理' : '标记已处理' }}
                    </button>
                    <button 
                      v-if="!v.isBlacklisted"
                      @click="addToBlacklist(v.userId)" 
                      class="btn btn-warning btn-small"
                      title="将该用户加入黑名单，加入后无法预约座位"
                    >
                      🚫 加入黑名单
                    </button>
                    <button 
                      v-else
                      @click="removeFromBlacklist(v.userId)" 
                      class="btn btn-info btn-small"
                      title="将该用户从黑名单中移除，移除后可以重新预约"
                    >
                      ✅ 移除黑名单
                    </button>
                    <button @click="remove(v.id)" class="btn btn-danger btn-small">删除</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import http from '../../lib/http'

interface Violation {
  id: number
  userId: number
  username?: string
  reservationId?: number
  type: string
  description?: string
  occurredAt: string
  handled: boolean
  isBlacklisted?: boolean
}

const list = ref<Violation[]>([])
const stats = ref<any>({})
const error = ref('')
const success = ref('')
const selectedIds = ref<number[]>([])

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const totalPages = ref(1)

const filters = ref<{
  userSearch: string
  from: string
  to: string
  type: string
  handled: boolean | null
}>({ 
  userSearch: '', 
  from: '', 
  to: '', 
  type: '',
  handled: null
})

const allSelected = computed(() => {
  return list.value.length > 0 && selectedIds.value.length === list.value.length
})

function formatDateTime(dateStr: string): string {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

async function load() {
  try {
    error.value = ''
    const params: any = {
      current: currentPage.value,
      size: pageSize.value
    }
    const uq = filters.value.userSearch.trim()
    if (uq) params.userQuery = uq
    if (filters.value.from) params.from = filters.value.from
    if (filters.value.to) params.to = filters.value.to
    if (filters.value.type) params.type = filters.value.type
    // 确保handled是布尔值，不是字符串
    if (filters.value.handled !== null && filters.value.handled !== undefined) {
      params.handled = filters.value.handled === true || filters.value.handled === 'true'
    }
    
    console.log('正在加载违规列表，参数:', params)
    const res = await http.get('/api/admin/violations', { params })
    console.log('违规列表加载成功:', res.data)
    const data = res.data
    if (data && Array.isArray(data.records)) {
      list.value = data.records as Violation[]
      total.value = Number(data.total) || 0
      totalPages.value = Math.max(1, Number(data.pages) || 1)
      if (currentPage.value > totalPages.value) {
        currentPage.value = totalPages.value
        await load()
        return
      }
    } else if (Array.isArray(data)) {
      list.value = data as Violation[]
      total.value = data.length
      totalPages.value = 1
    } else {
      list.value = []
      total.value = 0
      totalPages.value = 1
    }
    selectedIds.value = []
  } catch (e: any) {
    console.error('加载违规列表失败:', e)
    console.error('错误详情:', {
      message: e?.message,
      response: e?.response?.data,
      status: e?.response?.status,
      url: e?.config?.url
    })
    const errorMsg = e?.response?.data?.message || e?.message || '加载违规列表失败'
    const status = e?.response?.status
    
    // 检查是否是数据库字段缺失错误（检查错误消息和响应数据）
    const responseData = e?.response?.data
    const responseText = responseData ? JSON.stringify(responseData) : ''
    const fullErrorText = errorMsg + responseText + (e?.stack || '')
    const isDbError = fullErrorText.includes('Unknown column') || 
                      fullErrorText.includes("'description'") ||
                      fullErrorText.includes('description') ||
                      fullErrorText.includes('BadSqlGrammarException') ||
                      fullErrorText.includes('SQLSyntaxErrorException')
    
    if (status === 403) {
      // 如果是403但包含数据库错误信息，说明是后端SQL错误导致的
      if (isDbError) {
        error.value = `数据库错误: violation表缺少description字段。\n\n请执行以下SQL修复（在MySQL客户端中执行）：\n\nUSE library_seat;\nALTER TABLE violation ADD COLUMN description VARCHAR(500) COMMENT '违规描述' AFTER type;\nALTER TABLE violation ADD COLUMN occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '违规发生时间' AFTER description;\nALTER TABLE violation ADD COLUMN handled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已处理' AFTER occurred_at;\n\n执行完成后重启后端服务。`
      } else {
        error.value = '权限不足，只有管理员可以访问'
      }
    } else if (status === 401) {
      error.value = '登录已过期，请重新登录'
    } else if (status === 500 || isDbError) {
      if (isDbError) {
        error.value = `数据库错误: violation表缺少description字段。\n\n请执行以下SQL修复（在MySQL客户端中执行）：\n\nUSE library_seat;\nALTER TABLE violation ADD COLUMN description VARCHAR(500) COMMENT '违规描述' AFTER type;\nALTER TABLE violation ADD COLUMN occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '违规发生时间' AFTER description;\nALTER TABLE violation ADD COLUMN handled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已处理' AFTER occurred_at;\n\n执行完成后重启后端服务。`
      } else {
        error.value = `服务器错误: ${errorMsg}。请检查后端日志获取详细信息。`
      }
    } else if (status === 404) {
      error.value = 'API接口不存在。请确认后端已更新并重启。'
    } else {
      // 即使没有明确的状态码，也检查是否是数据库错误
      if (isDbError) {
        error.value = `数据库错误: violation表缺少description字段。\n\n请执行以下SQL修复（在MySQL客户端中执行）：\n\nUSE library_seat;\nALTER TABLE violation ADD COLUMN description VARCHAR(500) COMMENT '违规描述' AFTER type;\nALTER TABLE violation ADD COLUMN occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '违规发生时间' AFTER description;\nALTER TABLE violation ADD COLUMN handled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已处理' AFTER occurred_at;\n\n执行完成后重启后端服务。`
      } else {
        error.value = `加载失败: ${errorMsg}${status ? ` (状态码: ${status})` : ''}。请检查后端服务是否正常运行。`
      }
    }
  }
}

async function loadStats() {
  try {
    const res = await http.get('/api/admin/violations/stats')
    stats.value = res.data || {}
  } catch (e: any) {
    console.error('加载统计信息失败:', e)
    // 统计信息加载失败不影响主列表，只记录错误
    // 如果是数据库错误，在主错误信息中已显示
  }
}

function filterByAll() {
  resetFilters()
  load()
}

function filterByUnhandled() {
  filters.value = { userSearch: '', from: '', to: '', type: '', handled: false }
  currentPage.value = 1
  load()
}

function filterByHandled() {
  filters.value = { userSearch: '', from: '', to: '', type: '', handled: true }
  currentPage.value = 1
  load()
}

function onPageSizeChange() {
  currentPage.value = 1
  load()
}

function goPage(page: number) {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
  load()
}

async function update(v: Violation) {
  try {
    await http.put(`/api/admin/violations/${v.id}`, { 
      type: v.type, 
      description: v.description, 
      handled: v.handled 
    })
    await loadStats()
  } catch (e: any) {
    error.value = e?.response?.data?.message || '更新失败'
    await load()
  }
}

async function toggleHandle(v: Violation) {
  v.handled = !v.handled
  await update(v)
}

async function remove(id: number) {
  if (!confirm('确定要删除这条违规记录吗？')) {
    return
  }
  error.value = ''
  success.value = ''
  try {
    await http.delete(`/api/admin/violations/${id}`)
    success.value = '删除成功'
    await load()
    await loadStats()
    setTimeout(() => { success.value = '' }, 2000)
  } catch (e: any) {
    error.value = e?.response?.data?.message || '删除失败'
  }
}

function resetFilters() {
  filters.value = { userSearch: '', from: '', to: '', type: '', handled: null }
  currentPage.value = 1
  load()
}

function toggleAll(e: Event) {
  const checked = (e.target as HTMLInputElement).checked
  selectedIds.value = checked ? list.value.map(v => v.id) : []
}

function clearSelection() {
  selectedIds.value = []
}

async function batchHandle() {
  if (selectedIds.value.length === 0) {
    error.value = '请选择要处理的记录'
    setTimeout(() => { error.value = '' }, 2000)
    return
  }
  if (!confirm(`确定要标记 ${selectedIds.value.length} 条记录为已处理吗？`)) {
    return
  }
  error.value = ''
  success.value = ''
  try {
    const res = await http.post('/api/admin/violations/batch-handle', { ids: selectedIds.value })
    success.value = res.data?.message || '批量处理成功'
    selectedIds.value = []
    await load()
    await loadStats()
    setTimeout(() => { success.value = '' }, 2000)
  } catch (e: any) {
    error.value = e?.response?.data?.message || '批量处理失败'
  }
}

async function batchDelete() {
  if (selectedIds.value.length === 0) {
    error.value = '请选择要删除的记录'
    setTimeout(() => { error.value = '' }, 2000)
    return
  }
  if (!confirm(`确定要删除 ${selectedIds.value.length} 条记录吗？此操作不可恢复！`)) {
    return
  }
  error.value = ''
  success.value = ''
  try {
    const res = await http.post('/api/admin/violations/batch-delete', { ids: selectedIds.value })
    success.value = res.data?.message || '批量删除成功'
    selectedIds.value = []
    await load()
    await loadStats()
    setTimeout(() => { success.value = '' }, 2000)
  } catch (e: any) {
    error.value = e?.response?.data?.message || '批量删除失败'
  }
}

// 批量加入黑名单
async function batchAddToBlacklist() {
  if (selectedIds.value.length === 0) {
    error.value = '请选择要操作的记录'
    setTimeout(() => { error.value = '' }, 2000)
    return
  }
  
  const selectedViolations = list.value.filter(v => selectedIds.value.includes(v.id))
  const userIds = [...new Set(selectedViolations.map(v => v.userId))]
  if (userIds.length === 0) {
    error.value = '未找到有效的用户'
    setTimeout(() => { error.value = '' }, 2000)
    return
  }

  if (!confirm(`确定要将 ${userIds.length} 个用户加入黑名单吗？加入黑名单后，这些用户将无法预约座位。`)) {
    return
  }
  
  error.value = ''
  success.value = ''
  
  let successCount = 0
  let failCount = 0
  for (const userId of userIds) {
    try {
      await http.post(`/api/admin/violations/blacklist/${userId}`)
      successCount++
      // 更新列表中该用户的所有违规记录的黑名单状态
      list.value.forEach(v => {
        if (v.userId === userId) {
          v.isBlacklisted = true
        }
      })
    } catch (e: any) {
      failCount++
      console.error(`将用户 ${userId} 加入黑名单失败:`, e)
    }
  }

  if (successCount > 0) {
    success.value = `成功将 ${successCount} 个用户加入黑名单${failCount > 0 ? `，${failCount} 个失败` : ''}`
  } else {
    error.value = '批量加入黑名单失败'
  }

  
  selectedIds.value = []
  // 重新加载列表以获取最新状态
  await load()
  setTimeout(() => { 
    success.value = ''
    error.value = ''
  }, 3000)
}

async function exportExcel() {
  try {
    const params: any = {}
    if (filters.value.from) params.from = filters.value.from
    if (filters.value.to) params.to = filters.value.to

    const res = await http.get('/api/admin/violations/export', {
      params,
      responseType: 'blob'
    })

    const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const link = document.createElement('a')
    const url = URL.createObjectURL(blob)
    const today = new Date().toISOString().split('T')[0]
    const filename = `违规记录-${filters.value.from || today}_${filters.value.to || today}.xlsx`
    link.setAttribute('href', url)
    link.setAttribute('download', filename)
    link.style.visibility = 'hidden'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    success.value = '导出成功'
    setTimeout(() => { success.value = '' }, 2000)
  } catch (e: any) {
    console.error('导出Excel失败:', e)
    error.value = e?.response?.data?.message || '导出失败，请稍后重试'
    setTimeout(() => { error.value = '' }, 3000)
  }
}

// 加载用户的黑名单状态（用于刷新单个用户的状态）
async function loadBlacklistStatus(userId: number): Promise<boolean> {
  try {
    const res = await http.get(`/api/admin/violations/blacklist/${userId}`)
    return res.data?.isBlacklisted || false
  } catch (e: any) {
    console.error('加载黑名单状态失败:', e)
    return false
  }
}

// 将用户加入黑名单
async function addToBlacklist(userId: number) {
  if (!confirm('确定要将该用户加入黑名单吗？加入黑名单后，该用户将无法预约座位。')) {
    return
  }
  
  error.value = ''
  success.value = ''
  
  try {
    const res = await http.post(`/api/admin/violations/blacklist/${userId}`)
    success.value = res.data?.message || '用户已加入黑名单'
    
    // 更新列表中该用户的所有违规记录的黑名单状态
    list.value.forEach(v => {
      if (v.userId === userId) {
        v.isBlacklisted = true
      }
    })
    
    // 重新加载列表以获取最新状态
    await load()
    
    setTimeout(() => { success.value = '' }, 3000)
  } catch (e: any) {
    error.value = e?.response?.data?.message || '加入黑名单失败'
    setTimeout(() => { error.value = '' }, 3000)
  }
}

// 将用户从黑名单中移除
async function removeFromBlacklist(userId: number) {
  if (!confirm('确定要将该用户从黑名单中移除吗？移除后，该用户可以重新预约座位。')) {
    return
  }
  
  error.value = ''
  success.value = ''
  
  try {
    const res = await http.delete(`/api/admin/violations/blacklist/${userId}`)
    success.value = res.data?.message || '用户已从黑名单中移除'
    
    // 更新列表中该用户的所有违规记录的黑名单状态
    list.value.forEach(v => {
      if (v.userId === userId) {
        v.isBlacklisted = false
      }
    })
    
    setTimeout(() => { success.value = '' }, 3000)
  } catch (e: any) {
    error.value = e?.response?.data?.message || '移除黑名单失败'
    setTimeout(() => { error.value = '' }, 3000)
  }
}

// 防抖定时器
let debounceTimer: number | null = null

// 监听筛选条件变化，自动查询（防抖处理）
watch(
  () => [filters.value.userSearch, filters.value.type, filters.value.handled, filters.value.from, filters.value.to],
  () => {
    // 清除之前的定时器
    if (debounceTimer !== null) {
      clearTimeout(debounceTimer)
    }
    // 设置新的定时器，500ms后执行查询
    debounceTimer = window.setTimeout(() => {
      currentPage.value = 1
      load()
    }, 500)
  },
  { deep: true }
)

onMounted(() => {
  load()
  loadStats()
})
</script>

<style scoped src="../../styles/views/admin/ViolationsAdmin.css"></style>
