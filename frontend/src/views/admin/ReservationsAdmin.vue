<template>
  <div class="reservations-admin">
    <h2>预约审核</h2>
    <div class="filter-section">
      <div class="filter-row">
        <div class="filter-item">
          <label>状态筛选：</label>
          <select v-model="statusFilter" @change="handleFilterChange">
            <option value="">全部状态</option>
            <option value="ACTIVE">进行中</option>
            <option value="CANCELLED">已取消</option>
            <option value="FINISHED">已完成</option>
          </select>
        </div>
        <div class="filter-item">
          <label>座位ID：</label>
          <input 
            type="number" 
            v-model.number="seatIdFilter" 
            @input="handleSeatIdFilter"
            placeholder="输入座位ID查询"
            class="seat-id-input"
          />
          <button v-if="seatIdFilter" @click="clearSeatIdFilter" class="btn-clear">清除</button>
        </div>
        <div class="filter-item">
          <label>用户：</label>
          <input
            type="text"
            v-model.trim="userSearch"
            @input="handleUserSearch"
            placeholder="用户ID 或 用户名"
            class="seat-id-input"
            autocomplete="off"
          />
          <button v-if="userSearch" @click="clearUserSearch" class="btn-clear">清除</button>
        </div>
      </div>
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
        <span class="total-info">共 {{ total }} 条记录</span>
      </div>
    </div>
    
    <div class="table-container">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>用户名</th>
            <th>用户ID</th>
            <th>座位ID</th>
            <th>开始时间</th>
            <th>结束时间</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in reservations" :key="r.id">
            <td>{{ r.id }}</td>
            <td>{{ r.username || '-' }}</td>
            <td>{{ r.userId }}</td>
            <td>{{ r.seatId }}</td>
            <td>{{ formatDateTime(r.startTime) }}</td>
            <td>{{ formatDateTime(r.endTime) }}</td>
            <td>
              <span :class="['status-badge', `status-${r.status?.toLowerCase()}`]">
                {{ getStatusText(r.status) }}
              </span>
            </td>
            <td>
              <!-- 对于进行中或已确认的预约，管理员可以手动释放座位 -->
              <button 
                v-if="['ACTIVE', 'CONFIRMED'].includes(r.status)"
                @click="releaseSeat(r.id)"
                class="btn btn-danger btn-small"
                title="释放座位（提前结束预约）"
              >
                释放座位
              </button>
              <span v-else class="no-action">-</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    
    <!-- 分页导航 -->
    <div class="pagination">
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
          :max="totalPages" 
        />
        页 / 共 {{ totalPages }} 页
      </span>
      
      <button 
        class="pagination-btn" 
        :disabled="currentPage === totalPages" 
        @click="goToPage(currentPage + 1)">
        下一页
      </button>
      <button 
        class="pagination-btn" 
        :disabled="currentPage === totalPages" 
        @click="goToPage(totalPages)">
        末页
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import http from '../../lib/http'

const reservations = ref<any[]>([])
const statusFilter = ref('')
const seatIdFilter = ref<number | null>(null)
/** 用户ID（纯数字）或用户名，交给后端 userQuery */
const userSearch = ref('')

// 分页相关
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const totalPages = ref(0)
const inputPage = ref(1)

// 防抖定时器
let seatIdSearchTimer: ReturnType<typeof setTimeout> | null = null
let userSearchTimer: ReturnType<typeof setTimeout> | null = null

function formatDateTime(dateTime: string): string {
  if (!dateTime) return '-'
  const date = new Date(dateTime)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function getStatusText(status: string): string {
  const map: Record<string, string> = {
    'PENDING': '待审核',
    'CONFIRMED': '已批准',
    'ACTIVE': '进行中',
    'CANCELLED': '已取消',
    'FINISHED': '已完成'
  }
  return map[status] || status
}

async function loadReservations() {
  try {
    const params: any = {
      current: currentPage.value,
      size: pageSize.value
    }
    if (statusFilter.value) params.status = statusFilter.value
    if (seatIdFilter.value) params.seatId = seatIdFilter.value
    const uq = userSearch.value.trim()
    if (uq) params.userQuery = uq
    const res = await http.get('/api/admin/reservations', { params })
    reservations.value = res.data.records || []
    total.value = res.data.total || 0
    totalPages.value = res.data.pages || 1
    currentPage.value = res.data.current || 1
    inputPage.value = currentPage.value
  } catch (e) {
    console.error('加载预约失败', e)
    alert('加载预约失败')
  }
}

function handleFilterChange() {
  currentPage.value = 1
  loadReservations()
}

function handleSeatIdFilter() {
  // 清除之前的定时器
  if (seatIdSearchTimer) {
    clearTimeout(seatIdSearchTimer)
  }
  
  // 如果输入框为空，立即清除筛选
  if (!seatIdFilter.value || seatIdFilter.value === 0) {
    handleFilterChange()
    return
  }
  
  // 防抖：500ms 后执行搜索
  seatIdSearchTimer = setTimeout(() => {
    currentPage.value = 1
    loadReservations()
  }, 500)
}

function clearSeatIdFilter() {
  seatIdFilter.value = null
  handleFilterChange()
}

function handleUserSearch() {
  if (userSearchTimer) {
    clearTimeout(userSearchTimer)
  }
  if (!userSearch.value.trim()) {
    handleFilterChange()
    return
  }
  userSearchTimer = setTimeout(() => {
    currentPage.value = 1
    loadReservations()
  }, 500)
}

function clearUserSearch() {
  userSearch.value = ''
  handleFilterChange()
}

async function releaseSeat(id: number) {
  if (!confirm('确定要释放该座位吗？这将提前结束预约并释放座位资源。')) {
    return
  }
  try {
    const res = await http.post(`/api/admin/reservations/${id}/release`)
    alert(res.data?.message || '座位已释放')
    loadReservations()
  } catch (e: any) {
    console.error('释放座位失败', e)
    alert(e?.response?.data?.message || '释放座位失败')
  }
}

function handlePageSizeChange() {
  currentPage.value = 1
  loadReservations()
}

function goToPage(page: number) {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
  loadReservations()
}

function goToInputPage() {
  const page = parseInt(String(inputPage.value))
  if (isNaN(page) || page < 1) {
    inputPage.value = currentPage.value
    return
  }
  if (page > totalPages.value) {
    inputPage.value = totalPages.value
    goToPage(totalPages.value)
    return
  }
  goToPage(page)
}

async function approve(id: number) {
  try {
    await http.post(`/api/admin/reservations/${id}/approve`)
    loadReservations()
  } catch (e) {
    alert('批准失败')
  }
}

async function reject(id: number) {
  try {
    await http.post(`/api/admin/reservations/${id}/reject`)
    loadReservations()
  } catch (e) {
    alert('拒绝失败')
  }
}

onMounted(() => {
  loadReservations()
})
</script>

<style scoped src="../../styles/views/admin/ReservationsAdmin.css"></style>
