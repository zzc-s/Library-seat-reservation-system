<template>
  <div class="reservation-books-page">
    <div class="reservation-books-container">
      <div class="page-header">
        <h2 class="page-title">预约关联书籍</h2>
        <button @click="goSeats" class="btn btn-secondary">
          <span class="icon">←</span> 返回座位列表
        </button>
      </div>

      <!-- 创建预约表单 -->
      <div class="create-section">
        <h3 class="section-title">创建新预约</h3>
        <div class="form-grid">
          <div class="form-item">
            <label>座位ID</label>
            <button @click="showSeatSelection = true" class="btn btn-secondary" type="button">
              {{ selectedSeatInfo ? selectedSeatInfo : '选择座位' }}
            </button>
          </div>
          <div class="form-item">
            <label>开始时间</label>
            <input 
              v-model="start" 
              type="datetime-local" 
              :min="minDateTime"
              class="form-input"
            />
          </div>
          <div class="form-item">
            <label>结束时间</label>
            <input 
              v-model="end" 
              type="datetime-local" 
              :min="start || minDateTime"
              class="form-input"
            />
          </div>
          <div class="form-item">
            <label>关联图书（可选）</label>
            <button @click="showBookSelectionForCreate = true" class="btn btn-secondary">
              选择图书 (已选{{ selectedBookIdsForCreate.length }}本)
            </button>
          </div>
          <div v-if="selectedBookIdsForCreate.length > 0" class="selected-books">
            <div v-for="bookId in selectedBookIdsForCreate" :key="bookId" class="book-tag">
              {{ getBookName(bookId) }}
              <button @click="removeBookFromCreate(bookId)" class="remove-btn">×</button>
            </div>
          </div>
          <div class="form-item form-actions">
            <button @click="createReservation" class="btn btn-primary" :disabled="!canCreate" :title="!canCreate ? getDisabledReason() : ''">
              创建预约
            </button>
            <button @click="quickSetTime" class="btn btn-secondary btn-small">
              快速设置
            </button>
          </div>
          <div v-if="!canCreate" class="form-hint">
            <small>{{ getDisabledReason() }}</small>
          </div>
        </div>
        <div v-if="start && end" class="time-info">
          <span>预约时长：{{ durationText }}</span>
          <span v-if="!isValidTimeRange" class="warning-text">
            （单次预约不超过4小时）
          </span>
        </div>
      </div>

      <!-- 座位选择对话框 -->
      <div v-if="showSeatSelection" class="dialog-overlay" @click="showSeatSelection = false">
        <div class="book-selection-dialog" @click.stop>
          <div class="dialog-header">
            <h3>选择座位</h3>
            <button @click="showSeatSelection = false" class="close-btn">×</button>
          </div>
          <div class="book-selection-content">
            <div class="filter-section">
              <input 
                v-model="seatSearch" 
                @input="searchSeats" 
                placeholder="搜索座位（ID、位置、楼栋、楼层）..." 
                class="search-input" 
              />
            </div>
            <div class="books-list">
              <div 
                v-for="seat in availableSeats" 
                :key="seat.id"
                :class="['book-card', { 'selected': seatId === seat.id }]"
                @click="selectSeat(seat)"
              >
                <div class="book-title">座位ID: {{ seat.id }} - {{ seat.label || seat.seatCode || '未命名' }}</div>
                <div class="book-info">
                  <span v-if="seat.building">楼栋：{{ seat.building }}</span>
                  <span v-if="seat.floor">楼层：{{ seat.floor }}层</span>
                  <span v-if="seat.area">区域：{{ seat.area }}</span>
                  <span v-if="seat.zone">类型：{{ seat.zone }}</span>
                </div>
                <div class="book-info">
                  <span v-if="seat.hasPower">🔌 有电源</span>
                  <span v-if="seat.isWindow">🪟 靠窗</span>
                  <span v-if="seat.status">状态：{{ getSeatStatusText(seat.status) }}</span>
                </div>
              </div>
            </div>
            <div v-if="availableSeats.length === 0" class="empty-state">
              {{ seatSearch ? '未找到相关座位' : '暂无座位' }}
            </div>
            <div class="dialog-actions">
              <button @click="showSeatSelection = false" class="btn btn-secondary">取消</button>
              <button @click="confirmSeatSelection" class="btn btn-primary">确认</button>
            </div>
          </div>
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

      <!-- 创建预约时选择图书对话框 -->
      <div v-if="showBookSelectionForCreate" class="dialog-overlay" @click="showBookSelectionForCreate = false">
        <div class="book-selection-dialog" @click.stop>
          <div class="dialog-header">
            <h3>选择图书</h3>
            <button @click="showBookSelectionForCreate = false" class="close-btn">×</button>
          </div>
          <div class="book-selection-content">
            <div class="filter-section">
              <input 
                v-model="bookSearch" 
                @input="searchBooksForCreate" 
                placeholder="搜索图书（书名、作者、ISBN）..." 
                class="search-input" 
              />
            </div>
            <div class="books-list">
              <div 
                v-for="book in availableBooks" 
                :key="book.id"
                :class="['book-card', { 'selected': selectedBookIdsForCreate.includes(book.id) }]"
                @click="toggleBookSelectionForCreate(book.id)"
              >
                <div class="book-title">{{ book.title }}</div>
                <div class="book-info">
                  <span v-if="book.author">作者：{{ book.author }}</span>
                  <span v-if="book.isbn">ISBN：{{ book.isbn }}</span>
                </div>
              </div>
            </div>
            <div v-if="availableBooks.length === 0" class="empty-state">
              {{ bookSearch ? '未找到相关图书' : '暂无图书，请先添加图书' }}
            </div>
            <div class="dialog-actions">
              <button @click="showBookSelectionForCreate = false" class="btn btn-secondary">取消</button>
              <button @click="showBookSelectionForCreate = false" class="btn btn-primary">确认</button>
            </div>
          </div>
        </div>
      </div>

    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, computed, watch } from 'vue'
import http from '../lib/http'
import { useRouter, useRoute, onBeforeRouteLeave } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const isAdmin = computed(() => auth.isAdmin)
const router = useRouter()
const route = useRoute()  // 添加 route 变量定义
const error = ref('')
const success = ref('')

// 创建预约相关
const seatId = ref<number | null>(null)
const start = ref('')
const end = ref('')
// 确保初始状态为false，防止遮罩层意外显示
const showSeatSelection = ref(false)
const availableSeats = ref<any[]>([])
const seatSearch = ref('')
const selectedBookIdsForCreate = ref<number[]>([])
const showBookSelectionForCreate = ref(false)

// 图书选择相关（仅用于创建预约时选择图书）
const availableBooks = ref<any[]>([])
const bookSearch = ref('')

// 计算最小日期时间（当前时间）
const minDateTime = computed(() => {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  const hours = String(now.getHours()).padStart(2, '0')
  const minutes = String(now.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hours}:${minutes}`
})

// 计算预约时长
const durationText = computed(() => {
  if (!start.value || !end.value) return ''
  const startDate = new Date(start.value)
  const endDate = new Date(end.value)
  const diffMs = endDate.getTime() - startDate.getTime()
  const hours = Math.floor(diffMs / (1000 * 60 * 60))
  const minutes = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60))
  if (hours > 0) {
    return minutes > 0 ? `${hours}小时${minutes}分钟` : `${hours}小时`
  }
  return `${minutes}分钟`
})

// 验证时间范围（不超过4小时）
const isValidTimeRange = computed(() => {
  if (!start.value || !end.value) return false
  const startDate = new Date(start.value)
  const endDate = new Date(end.value)
  const diffMs = endDate.getTime() - startDate.getTime()
  const hours = diffMs / (1000 * 60 * 60)
  return hours > 0 && hours <= 4
})

// 是否可以创建预约
const canCreate = computed(() => {
  return seatId.value && start.value && end.value && isValidTimeRange.value
})

// 获取按钮禁用原因（用于提示用户）
function getDisabledReason(): string {
  if (!seatId.value) return '请先选择座位'
  if (!start.value) return '请选择开始时间'
  if (!end.value) return '请选择结束时间'
  if (!isValidTimeRange.value) return '预约时长不能超过4小时'
  return ''
}

const selectedSeatInfo = computed(() => {
  if (!seatId.value) return ''
  const seat = availableSeats.value.find(s => s.id === seatId.value)
  if (seat) {
    return `${seat.building || ''}-${seat.floor || ''}层-${seat.label || seat.seatCode || seat.id}`
  }
  return `座位ID: ${seatId.value}`
})

function pad(n: number) {
  return n < 10 ? '0' + n : '' + n
}

function formatDateTimeLocal(d: Date): string {
  const yyyy = d.getFullYear()
  const mm = pad(d.getMonth() + 1)
  const dd = pad(d.getDate())
  const hh = pad(d.getHours())
  const mi = pad(d.getMinutes())
  return `${yyyy}-${mm}-${dd}T${hh}:${mi}`
}

function roundUpToNext15Minutes(d: Date) {
  const copy = new Date(d.getTime())
  copy.setSeconds(0, 0)
  const minutes = copy.getMinutes()
  const rounded = Math.ceil((minutes + 1) / 15) * 15
  copy.setMinutes(rounded % 60)
  if (rounded >= 60) {
    copy.setHours(copy.getHours() + 1)
  }
  return copy
}

// 快速设置时间（默认：下一个15分钟整点开始，持续2小时）
function quickSetTime() {
  const now = new Date()
  const s = roundUpToNext15Minutes(now)
  const e = new Date(s.getTime() + 2 * 60 * 60 * 1000)
  start.value = formatDateTimeLocal(s)
  end.value = formatDateTimeLocal(e)
}

// 将本地时间转换为 ISO 8601 格式（用于 API 请求）
function formatLocalDateTime(dateStr: string): string {
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}`
}



// 创建预约
async function createReservation() {
  if (!canCreate.value) {
    error.value = '请填写完整的预约信息，且预约时长不超过4小时'
    setTimeout(() => { error.value = '' }, 3000)
    return
  }
  
  // 客户端验证：确保开始时间和结束时间都是未来时间
  const now = new Date()
  const startDate = new Date(start.value)
  const endDate = new Date(end.value)
  
  if (startDate <= now) {
    error.value = '开始时间必须是未来时间，不能预约过去的时间段'
    setTimeout(() => { error.value = '' }, 3000)
    return
  }
  
  if (endDate <= now) {
    error.value = '结束时间必须是未来时间'
    setTimeout(() => { error.value = '' }, 3000)
    return
  }
  
  error.value = ''
  success.value = ''
  try {
    const payload = {
      seatId: seatId.value,
      startTime: formatLocalDateTime(start.value),
      endTime: formatLocalDateTime(end.value)
    }
    console.log('创建预约请求:', payload)
    const res = await http.post('/api/reservations', payload)
    const reservationId = res.data.id
    
    // 如果选择了图书，关联图书到预约
    if (selectedBookIdsForCreate.value.length > 0) {
      for (const bookId of selectedBookIdsForCreate.value) {
        try {
          await http.post('/api/books/link', {
            reservationId: reservationId,
            bookId: bookId
          })
        } catch (e: any) {
          console.error(`关联图书 ${bookId} 失败`, e)
        }
      }
    }
    
    success.value = '预约创建成功' + (selectedBookIdsForCreate.value.length > 0 ? '，已关联图书' : '')
    // 清除表单
    if (route.query.seatId) {
      await router.replace({ path: route.path, query: {} })
    }
    seatId.value = null
    selectedBookIdsForCreate.value = []
    quickSetTime() // 重置为默认时间
    setTimeout(() => { success.value = '' }, 2000)
  } catch (e: any) {
    console.error('创建预约失败:', e)
    let errorMsg = '创建预约失败'
    if (e?.response) {
      if (e.response.status === 409) {
        errorMsg = e.response.data?.message || '该时段座位已被预约'
      } else if (e.response.status === 400) {
        errorMsg = e.response.data?.message || '请求参数错误'
      } else if (e.response.status === 500) {
        errorMsg = e.response.data?.message || '服务器错误，请稍后重试'
      } else {
        errorMsg = e.response.data?.message || `创建预约失败 (${e.response.status})`
      }
    } else if (e?.message) {
      errorMsg = e.message
    }
    error.value = errorMsg
    // 错误消息显示5秒
    setTimeout(() => { error.value = '' }, 5000)
  }
}

// 搜索座位
async function searchSeats() {
  try {
    const keyword = seatSearch.value.trim()
    const params: any = {}
    
    // 如果搜索关键词是数字，可能是座位ID
    if (keyword && !isNaN(Number(keyword))) {
      params.id = Number(keyword)
    } else if (keyword) {
      // 否则搜索楼栋、楼层、标签等
      const res = await http.get('/api/seats')
      let seats = res.data || []
      
      // 前端过滤
      const lowerKeyword = keyword.toLowerCase()
      seats = seats.filter((seat: any) => {
        const label = (seat.label || seat.seatCode || '').toLowerCase()
        const building = (seat.building || '').toLowerCase()
        const area = (seat.area || '').toLowerCase()
        const zone = (seat.zone || '').toLowerCase()
        return label.includes(lowerKeyword) || 
               building.includes(lowerKeyword) || 
               area.includes(lowerKeyword) ||
               zone.includes(lowerKeyword) ||
               String(seat.id).includes(keyword)
      })
      availableSeats.value = seats
      return
    }
    
    const res = await http.get('/api/seats', { params })
    availableSeats.value = res.data || []
  } catch (e) {
    console.error('搜索座位失败', e)
    availableSeats.value = []
  }
}

// 选择座位
function selectSeat(seat: any) {
  seatId.value = seat.id
}

// 确认座位选择
function confirmSeatSelection() {
  showSeatSelection.value = false
}

// 获取座位状态文本
function getSeatStatusText(status: string): string {
  const statusMap: Record<string, string> = {
    'FREE': '空闲',
    'IDLE': '空闲',
    'RESERVED': '已预约',
    'OCCUPIED': '使用中',
    'BROKEN': '故障',
    'FAULT': '维修'
  }
  return statusMap[status] || status
}

// 监听座位选择对话框的显示，自动加载座位列表
watch(showSeatSelection, (newVal) => {
  if (newVal) {
    searchSeats()
  } else {
    // 关闭对话框时清空搜索
    seatSearch.value = ''
  }
})

// 创建预约时选择图书
async function searchBooksForCreate() {
  try {
    const keyword = bookSearch.value.trim()
    const res = await http.get('/api/books', { 
      params: keyword ? { keyword } : {} 
    })
    availableBooks.value = res.data
  } catch (e) {
    console.error('搜索图书失败', e)
    availableBooks.value = []
  }
}

function toggleBookSelectionForCreate(bookId: number) {
  const index = selectedBookIdsForCreate.value.indexOf(bookId)
  if (index > -1) {
    selectedBookIdsForCreate.value.splice(index, 1)
  } else {
    selectedBookIdsForCreate.value.push(bookId)
  }
}

function removeBookFromCreate(bookId: number) {
  const index = selectedBookIdsForCreate.value.indexOf(bookId)
  if (index > -1) {
    selectedBookIdsForCreate.value.splice(index, 1)
  }
}

function getBookName(bookId: number): string {
  const book = availableBooks.value.find(b => b.id === bookId)
  return book ? book.title : `图书${bookId}`
}

// 监听创建预约时的图书选择对话框
watch(showBookSelectionForCreate, (newVal) => {
  if (newVal) {
    searchBooksForCreate()
  } else {
    // 关闭对话框时清空搜索
    bookSearch.value = ''
  }
})


function goSeats() {
  router.push({ name: 'seatsMap' })
}


onMounted(() => {
  // 确保所有对话框在组件挂载时都是关闭状态
  showSeatSelection.value = false
  showBookSelectionForCreate.value = false
  
  if (route.query.seatId) {
    seatId.value = Number(route.query.seatId)
  }
  if (route.query.startTime) {
    start.value = route.query.startTime as string
  }
  if (route.query.endTime) {
    end.value = route.query.endTime as string
  }
  // 若未选择时间，则默认预填
  if (!start.value || !end.value) {
    quickSetTime()
  }
})

// 路由离开前关闭所有对话框，防止遮罩层残留
onBeforeRouteLeave(() => {
  showSeatSelection.value = false
  showBookSelectionForCreate.value = false
})

// 监控对话框状态，确保不会同时打开多个
watch([showSeatSelection, showBookSelectionForCreate], ([seat, bookCreate]) => {
  // 如果同时有多个对话框打开，关闭其他的
  if (seat && bookCreate) {
    showBookSelectionForCreate.value = false
  }
})

// 组件卸载时也关闭所有对话框
onBeforeUnmount(() => {
  showSeatSelection.value = false
  showBookSelectionForCreate.value = false
})
</script>

<style scoped src="../styles/views/ReservationBooks.css"></style>
