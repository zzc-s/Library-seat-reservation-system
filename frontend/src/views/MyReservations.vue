<template>
  <div class="reservations-page">
    <div class="reservations-container">
      <div class="page-header">
        <h2 class="page-title">我的预约记录</h2>
        <button @click="goSeats" class="btn btn-secondary">
          <span class="icon">←</span> 返回座位列表
        </button>
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

      <!-- 预约列表 -->
      <div class="reservations-section">
        <div class="section-header">
          <h3 class="section-title">预约记录（{{ filteredList.length }}条）</h3>
          <div class="header-actions">
            <!-- 状态筛选 -->
            <select v-model="statusFilter" class="status-filter">
              <option value="">全部</option>
              <option value="ACTIVE">进行中</option>
              <option value="FINISHED">已完成</option>
              <option value="CANCELLED">已取消</option>
            </select>
          </div>
        </div>

        <div v-if="filteredList.length === 0" class="empty-state">
          <p>{{ statusFilter ? '该状态下暂无预约记录' : '暂无预约记录' }}</p>
          <button v-if="!isAdmin" @click="goSeats" class="btn btn-primary">去预约座位</button>
        </div>

        <div v-else class="reservations-list">
          <div 
            v-for="r in filteredList" 
            :key="r.id"
            :class="['reservation-card', getStatusClass(r.status)]"
          >
            <div class="card-header">
              <div class="reservation-id">
                <span class="label">预约ID：</span>
                <span class="value">#{{ r.id }}</span>
              </div>
              <span :class="['status-badge', getStatusClass(r.status)]">
                {{ getStatusText(r.status) }}
              </span>
            </div>
            
            <div class="card-body">
              <div class="info-grid">
                <div v-if="isAdmin" class="info-item">
                  <span class="label">用户：</span>
                  <span class="value">{{ r.username || `ID: ${r.userId}` }}</span>
                </div>
                <div class="info-item">
                  <span class="label">座位ID：</span>
                  <span class="value">{{ r.seatId }}</span>
                </div>
                <div class="info-item">
                  <span class="label">开始时间：</span>
                  <span class="value">{{ formatDateTime(r.startTime) }}</span>
                </div>
                <div class="info-item">
                  <span class="label">结束时间：</span>
                  <span class="value">{{ formatDateTime(r.endTime) }}</span>
                </div>
                <div class="info-item">
                  <span class="label">创建时间：</span>
                  <span class="value">{{ r.createdAt ? formatDateTime(r.createdAt) : '-' }}</span>
                </div>
                <div class="info-item full-width">
                  <span class="label">预约时长：</span>
                  <span class="value">{{ getDuration(r.startTime, r.endTime) }}</span>
                </div>
                <!-- 签到状态显示 -->
                <div v-if="attendanceStatus[r.id]" class="info-item full-width attendance-status-item">
                  <span class="label">签到状态：</span>
                  <span class="value" :class="getAttendanceStatusClass(attendanceStatus[r.id])">
                    {{ getAttendanceStatusText(attendanceStatus[r.id]) }}
                  </span>
                  <span v-if="attendanceStatus[r.id].checkInTime" class="checkin-time">
                    （{{ formatDateTime(attendanceStatus[r.id].checkInTime) }}）
                  </span>
                </div>
              </div>
            </div>

            <div class="card-footer">
              <!-- 签到按钮：仅普通用户 + 在可签到时间窗口内 + 还未取消/完成 -->
              <button 
                v-if="!isAdmin && canCheckIn(r)"
                @click="showCheckInQrCode(r)"
                class="btn btn-primary btn-small"
              >
                📱 签到
              </button>
              <!-- 不能签到的提示信息 -->
              <div 
                v-else-if="!isAdmin && ['ACTIVE', 'CONFIRMED'].includes(r.status) && !isCheckedIn(r)"
                class="checkin-hint"
              >
                <span class="hint-text">{{ getCheckInHint(r) }}</span>
              </div>
              <!-- 可以取消 ACTIVE、CONFIRMED、PENDING 状态的预约 -->
              <button 
                v-if="canCancel(r)"
                @click="cancel(r.id)" 
                class="btn btn-danger btn-small"
              >
                取消预约
              </button>
              <!-- 状态显示 -->
              <span class="status-text" :class="getStatusClass(r.status)">{{ getActualStatusText(r) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 签到二维码模态框 -->
    <div v-if="showQrModal" class="modal-overlay" @click="closeQrModal">
      <div class="qr-modal" @click.stop>
        <div class="qr-modal-header">
          <h3>扫码签到</h3>
          <button @click="closeQrModal" class="close-btn">×</button>
        </div>
        <div class="qr-modal-content">
          <div v-if="loadingQr" class="qr-loading">
            <p>正在生成二维码...</p>
          </div>
          <div v-else-if="qrCodeError" class="qr-error">
            <p>{{ qrCodeError }}</p>
          </div>
          <div v-else-if="qrCodeData" class="qr-code-container">
            <p class="qr-tip">请使用手机扫描下方二维码进行签到</p>
            <div class="qr-code-wrapper">
              <img :src="qrCodeData.qrCode" alt="签到二维码" class="qr-code-image" />
            </div>
            <div class="qr-info">
              <p><strong>预约ID：</strong>#{{ qrCodeData.reservationId }}</p>
              <p><strong>座位ID：</strong>{{ qrCodeData.seatId }}</p>
              <p v-if="currentReservation" class="qr-time-info">
                <strong>开始时间：</strong>{{ formatDateTime(currentReservation.startTime) }}<br>
                <strong>结束时间：</strong>{{ formatDateTime(currentReservation.endTime) }}
              </p>
              <div class="qr-url-info" v-if="qrCodeData.qrUrl || qrCodeData.qrContent">
                <p class="qr-url-label"><strong>二维码URL：</strong></p>
                <p class="qr-url-text">{{ qrCodeData.qrUrl || qrCodeData.qrContent }}</p>
                <button @click="copyQrUrl" class="btn-copy-url">复制URL</button>
              </div>
              <div class="qr-network-tip">
                <p class="qr-tip-title">📱 网络检查提示：</p>
                <ul class="qr-tip-list">
                  <li>确保手机和电脑连接<strong>同一个WiFi网络</strong></li>
                  <li>如果二维码URL是 <code>localhost</code>，手机无法访问</li>
                  <li>二维码URL应该是类似 <code>http://192.168.x.x:5173</code> 的格式</li>
                  <li>如果URL不对，请用手机浏览器访问上面的URL测试是否能打开</li>
                </ul>
              </div>
              <p class="qr-note">{{ qrCodeData.note || '请扫描座位上的固定二维码进行签到' }}</p>
              <p class="qr-hint" v-if="qrCodeData.tip">{{ qrCodeData.tip }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, computed, watch } from 'vue'
import http from '../lib/http'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const isAdmin = computed(() => auth.isAdmin)
const list = ref<any[]>([])
const statusFilter = ref<string>('') // 状态筛选：''=全部, 'ACTIVE'=进行中, 'FINISHED'=已完成, 'CANCELLED'=已取消
const error = ref('')
const success = ref('')
const route = useRoute()
const router = useRouter()

// 二维码相关
const showQrModal = ref(false)
const loadingQr = ref(false)
const qrCodeData = ref<any>(null)
const qrCodeError = ref('')
const currentReservation = ref<any>(null)
const attendanceStatus = ref<Record<number, { status: string; checkInTime?: string }>>({})

function pad(n: number) {
  return n < 10 ? '0' + n : '' + n
}


// 格式化日期时间显示
function formatDateTime(dateTime: string): string {
  if (!dateTime) return '-'
  const date = new Date(dateTime)
  const year = date.getFullYear()
  const month = pad(date.getMonth() + 1)
  const day = pad(date.getDate())
  const hours = pad(date.getHours())
  const minutes = pad(date.getMinutes())
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

// 计算预约时长
function getDuration(startTime: string, endTime: string): string {
  if (!startTime || !endTime) return '-'
  const start = new Date(startTime)
  const end = new Date(endTime)
  const diffMs = end.getTime() - start.getTime()
  const hours = Math.floor(diffMs / (1000 * 60 * 60))
  const minutes = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60))
  if (hours > 0) {
    return minutes > 0 ? `${hours}小时${minutes}分钟` : `${hours}小时`
  }
  return `${minutes}分钟`
}

// 判断预约是否即将开始（用于显示提示）
function isUpcoming(startTime: string): boolean {
  if (!startTime) return false
  const start = new Date(startTime)
  const now = new Date()
  // PC端采用自动签到，此函数保留但不使用
  const before5Min = new Date(start.getTime() - 5 * 60 * 1000)
  const after5Min = new Date(end.getTime() + 5 * 60 * 1000)
  return now >= before5Min && now <= after5Min
}

async function load() {
  try {
    // 管理员查看所有预约，普通用户查看自己的预约
    const url = isAdmin.value ? '/api/admin/reservations' : '/api/reservations'
    const res = await http.get(url)
    list.value = res.data || []
    
    // 为每个预约加载签到状态
    for (const reservation of list.value) {
      await loadAttendanceStatus(reservation.id)
    }
  } catch (e: any) {
    error.value = e?.response?.data?.message || '加载预约列表失败'
  }
}

function goSeats() {
  router.push({ name: 'seatsMap' })
}


async function cancel(id: number) {
  if (!confirm('确定要取消这个预约吗？')) {
    return
  }
  error.value = ''
  success.value = ''
  try {
    console.log('准备取消预约:', id)
    const res = await http.post(`/api/reservations/${id}/cancel`)
    console.log('取消预约成功:', res.data)
    const item = list.value.find(x => x.id === id)
    if (item) {
      item.status = 'CANCELLED'
      success.value = res.data?.message || '预约已取消'
      setTimeout(() => { success.value = '' }, 2000)
    }
    await load() // 刷新列表
  } catch (e: any) {
    console.error('取消预约失败:', e)
    let errorMsg = '取消预约失败'
    if (e?.response) {
      if (e.response.status === 403) {
        errorMsg = e.response.data?.message || '无权取消此预约'
      } else if (e.response.status === 404) {
        errorMsg = e.response.data?.message || '预约不存在'
      } else if (e.response.status === 400) {
        errorMsg = e.response.data?.message || '无法取消此预约'
      } else if (e.response.status === 500) {
        errorMsg = e.response.data?.message || '服务器错误，请稍后重试'
      } else {
        errorMsg = e.response.data?.message || `取消预约失败 (${e.response.status})`
      }
    } else if (e?.message) {
      errorMsg = e.message
    }
    error.value = errorMsg
  }
}

function getStatusClass(status: string): string {
  const statusMap: Record<string, string> = {
    'ACTIVE': 'status-active',
    'PENDING': 'status-pending',
    'CONFIRMED': 'status-confirmed',
    'CANCELLED': 'status-cancelled',
    'FINISHED': 'status-finished'
  }
  return statusMap[status] || 'status-unknown'
}

function getStatusText(status: string): string {
  const statusMap: Record<string, string> = {
    'ACTIVE': '进行中',
    'CANCELLED': '已取消',
    'FINISHED': '已完成'
  }
  return statusMap[status] || status
}

// 判断预约是否可以取消（允许取消 ACTIVE、CONFIRMED、PENDING 状态的预约）
function canCancel(reservation: any): boolean {
  // 允许取消 ACTIVE、CONFIRMED、PENDING 状态的预约
  return ['ACTIVE', 'CONFIRMED', 'PENDING'].includes(reservation.status)
}

// 判断预约是否可在当前时间签到
// 状态必须是 ACTIVE / CONFIRMED，且在 [开始前5分钟, 开始后15分钟] 的窗口内，且未签到
function canCheckIn(reservation: any): boolean {
  // 已取消或已完成不能签到
  if (['CANCELLED', 'FINISHED'].includes(reservation.status)) return false
  
  // 必须是 ACTIVE 或 CONFIRMED 状态
  if (!['ACTIVE', 'CONFIRMED'].includes(reservation.status)) return false
  
  // 如果已经签到，不再显示签到按钮
  const status = attendanceStatus.value[reservation.id]
  if (status && (status.status === 'CHECKED_IN' || status.status === 'CHECKED_OUT')) {
    return false
  }
  
  if (!reservation.startTime || !reservation.endTime) return false
  
  const now = new Date()
  const start = new Date(reservation.startTime)
  const end = new Date(reservation.endTime)
  
  // 时间窗口：开始前5分钟到开始后15分钟
  const windowStart = new Date(start.getTime() - 5 * 60 * 1000) // 开始前5分钟
  const windowEnd = new Date(start.getTime() + 15 * 60 * 1000)   // 开始后15分钟
  
  return now >= windowStart && now <= windowEnd
}

// 判断是否已签到
function isCheckedIn(reservation: any): boolean {
  const status = attendanceStatus.value[reservation.id]
  return status && (status.status === 'CHECKED_IN' || status.status === 'CHECKED_OUT')
}

// 获取不能签到的提示信息
function getCheckInHint(reservation: any): string {
  if (!reservation.startTime || !reservation.endTime) {
    return '预约时间信息不完整'
  }
  
  const now = new Date()
  const start = new Date(reservation.startTime)
  const windowStart = new Date(start.getTime() - 5 * 60 * 1000) // 开始前5分钟
  const windowEnd = new Date(start.getTime() + 15 * 60 * 1000)   // 开始后15分钟
  
  if (now < windowStart) {
    const minutes = Math.ceil((windowStart.getTime() - now.getTime()) / (1000 * 60))
    return `⏰ 距离可签到时间还有 ${minutes} 分钟（可提前5分钟签到）`
  }
  
  if (now > windowEnd) {
    return '⏰ 签到时间已过期（开始后15分钟内可签到，超时未签到将自动释放座位）'
  }
  
  return '⏰ 当前不在可签到时间范围内'
}

// 显示签到二维码
async function showCheckInQrCode(reservation: any) {
  if (!canCheckIn(reservation)) {
    error.value = '当前不在可签到时间范围内（可提前5分钟签到，开始后15分钟内可签到）'
    setTimeout(() => { error.value = '' }, 3000)
    return
  }
  
  currentReservation.value = reservation
  showQrModal.value = true
  loadingQr.value = true
  qrCodeData.value = null
  qrCodeError.value = ''
  
  try {
    const res = await http.get(`/api/attendance/reservation/${reservation.id}/qrcode`)
    qrCodeData.value = res.data
    
    // 开始轮询检查签到状态（每3秒检查一次）
    startPollingCheckInStatus(reservation.id)
  } catch (e: any) {
    console.error('获取签到二维码失败:', e)
    qrCodeError.value = e?.response?.data?.message || e?.message || '获取二维码失败'
  } finally {
    loadingQr.value = false
  }
}

// 加载预约的签到状态
async function loadAttendanceStatus(reservationId: number) {
  try {
    const res = await http.get('/api/attendance/logs', { params: { reservationId } })
    const logs = res.data || []
    if (logs.length > 0) {
      const checkInLog = logs.find((log: any) => log.action === 'CHECK_IN')
      const checkOutLog = logs.find((log: any) => log.action === 'CHECK_OUT')
      
      if (checkOutLog) {
        attendanceStatus.value[reservationId] = {
          status: 'CHECKED_OUT',
          checkInTime: checkInLog?.occurredAt
        }
      } else if (checkInLog) {
        attendanceStatus.value[reservationId] = {
          status: 'CHECKED_IN',
          checkInTime: checkInLog.occurredAt
        }
      } else {
        attendanceStatus.value[reservationId] = { status: 'NONE' }
      }
    } else {
      attendanceStatus.value[reservationId] = { status: 'NONE' }
    }
  } catch (e) {
    console.error('加载签到状态失败:', e)
    attendanceStatus.value[reservationId] = { status: 'NONE' }
  }
}

// 获取签到状态文本
function getAttendanceStatusText(status: { status: string }): string {
  switch (status.status) {
    case 'CHECKED_IN': return '✅ 已签到'
    case 'CHECKED_OUT': return '✅ 已签退'
    case 'NONE': return '⏳ 未签到'
    default: return '⏳ 未签到'
  }
}

// 获取签到状态样式类
function getAttendanceStatusClass(status: { status: string }): string {
  switch (status.status) {
    case 'CHECKED_IN': return 'status-checked-in'
    case 'CHECKED_OUT': return 'status-checked-out'
    case 'NONE': return 'status-not-checked'
    default: return 'status-not-checked'
  }
}

// 轮询检查签到状态
let pollingInterval: number | null = null

function startPollingCheckInStatus(reservationId: number) {
  // 清除之前的轮询
  if (pollingInterval) {
    clearInterval(pollingInterval)
  }
  
  // 每3秒检查一次签到状态
  pollingInterval = window.setInterval(async () => {
    try {
      // 重新加载预约列表和签到状态
      await load()
      await loadAttendanceStatus(reservationId)
      
      // 如果已签到，自动关闭弹窗并停止轮询
      if (attendanceStatus.value[reservationId]?.status === 'CHECKED_IN' || 
          attendanceStatus.value[reservationId]?.status === 'CHECKED_OUT') {
        // 显示成功提示
        success.value = '签到成功！'
        setTimeout(() => {
          success.value = ''
        }, 3000)
        
        // 自动关闭二维码弹窗
        closeQrModal()
        
        // 停止轮询
        if (pollingInterval) {
          clearInterval(pollingInterval)
          pollingInterval = null
        }
      }
    } catch (e) {
      console.error('检查签到状态失败:', e)
    }
  }, 3000)
  
  // 30秒后自动停止轮询（避免无限轮询）
  setTimeout(() => {
    if (pollingInterval) {
      clearInterval(pollingInterval)
      pollingInterval = null
    }
  }, 30000)
}

// 复制二维码URL
async function copyQrUrl() {
  const url = qrCodeData.value?.qrUrl || qrCodeData.value?.qrContent
  if (!url) return
  
  try {
    await navigator.clipboard.writeText(url)
    alert('URL已复制到剪贴板！\n\n如果手机无法访问，请：\n1. 确保手机和电脑在同一WiFi\n2. 在手机浏览器中直接访问这个URL测试')
  } catch (e) {
    // 降级方案：使用传统方法
    const textarea = document.createElement('textarea')
    textarea.value = url
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    try {
      document.execCommand('copy')
      alert('URL已复制到剪贴板！')
    } catch (err) {
      alert('复制失败，请手动复制：' + url)
    }
    document.body.removeChild(textarea)
  }
}

// 关闭二维码模态框
function closeQrModal() {
  // 停止轮询
  if (pollingInterval) {
    clearInterval(pollingInterval)
    pollingInterval = null
  }
  
  showQrModal.value = false
  qrCodeData.value = null
  qrCodeError.value = ''
  currentReservation.value = null
  
  // 刷新预约列表
  load()
}

// 根据状态筛选过滤预约列表
const filteredList = computed(() => {
  // 如果没有选择筛选条件，返回全部列表
  if (!statusFilter.value || statusFilter.value === '') {
    return list.value
  }
  
  const filterValue = String(statusFilter.value).trim().toUpperCase()
  
  // 筛选逻辑
  return list.value.filter(r => {
    if (!r || !r.status) {
      return false
    }
    
    // 确保状态值也是大写，去除空格
    const recordStatus = String(r.status).trim().toUpperCase()
    
    // 进行中：ACTIVE 或 CONFIRMED 状态，且预约未结束（包括未开始但已创建的预约）
    if (filterValue === 'ACTIVE') {
      if (!['ACTIVE', 'CONFIRMED'].includes(recordStatus)) {
        return false
      }
      const now = new Date()
      const end = new Date(r.endTime)
      // 只要预约未结束，就认为是"进行中"（包括未开始、进行中、已开始但未结束）
      return now <= end
    }
    
    // 已完成：直接匹配 FINISHED 状态
    if (filterValue === 'FINISHED') {
      return recordStatus === 'FINISHED'
    }
    
    // 已取消：直接匹配 CANCELLED 状态
    if (filterValue === 'CANCELLED') {
      return recordStatus === 'CANCELLED'
    }
    
    return false
  })
})

// 获取预约的实际状态文字（区分未开始、进行中、已结束）
function getActualStatusText(reservation: any): string {
  if (reservation.status === 'CANCELLED') return '已取消'
  if (reservation.status === 'FINISHED') return '已完成'
  if (reservation.status === 'PENDING') return '待审核'
  if (reservation.status === 'CONFIRMED') {
    const now = new Date()
    const startTime = new Date(reservation.startTime)
    const endTime = new Date(reservation.endTime)
    if (now < startTime) return '已批准（待开始）'
    if (now > endTime) return '已过期'
    return '已批准（进行中）'
  }
  if (reservation.status === 'ACTIVE') {
    const now = new Date()
    const startTime = new Date(reservation.startTime)
    const endTime = new Date(reservation.endTime)
    if (now < startTime) return '待开始'
    if (now > endTime) return '已过期'
    return '进行中'
  }
  return reservation.status
}

onMounted(() => {
  load()
  
  // 监听预约状态变化，如果已签到则关闭二维码模态框
  // 可以通过轮询或WebSocket实现，这里使用简单的定时刷新
  // 用户扫码签到后，后端会更新预约状态，前端刷新列表时会自动更新
})
</script>

<style scoped src="../styles/views/MyReservations.css"></style>
