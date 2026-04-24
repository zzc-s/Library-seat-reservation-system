<template>
  <div class="visual-seat-page">
    <div class="visual-seat-container">
      <h2 class="page-title">可视化选座</h2>

      <!-- 查询条件区域 -->
      <div class="query-section">
        <div class="query-form">
          <div class="form-group">
            <label>预约日期</label>
            <input 
              type="date" 
              v-model="reserveDate" 
              :min="minDate"
              :max="maxDate"
              class="form-input"
            />
          </div>
          <div class="form-group">
            <label>开始时间</label>
            <input 
              type="time" 
              v-model="startTime" 
              class="form-input"
              :min="getMinStartTime()"
              :max="'23:00'"
              step="1800"
            />
          </div>
          <div class="form-group">
            <label>结束时间</label>
            <input 
              type="time" 
              v-model="endTime"
              class="form-input"
              :min="getMinEndTime()"
              :max="'23:59'"
              step="1800"
            />
          </div>
          <div class="form-group">
            <label>楼栋</label>
            <select v-model="selectedBuilding" class="form-input">
              <option value="A楼">A楼</option>
            </select>
          </div>
          <div class="form-group">
            <label>区域</label>
            <select v-model="selectedArea" class="form-input">
              <option value="">全部区域</option>
              <option v-for="area in areas" :key="area" :value="area">
                {{ area }}
              </option>
            </select>
          </div>
          <!-- WebSocket 连接状态指示器 -->
          <div class="ws-status" :class="{ connected: wsConnected }" :title="wsConnected ? '实时更新已连接' : '实时更新未连接'">
            <span class="ws-dot"></span>
            <span class="ws-text">{{ wsConnected ? '实时' : '离线' }}</span>
          </div>
        </div>
      </div>

      <!-- 筛选条件 -->
      <div class="filter-section">
        <div class="filter-options">
          <label class="filter-checkbox">
            <input type="checkbox" v-model="filterHasPower" />
            <span>🔌 有电源</span>
          </label>
          <label class="filter-checkbox">
            <input type="checkbox" v-model="filterIsWindow" />
            <span>🪟 靠窗</span>
          </label>
          <label class="filter-checkbox">
            <input type="checkbox" v-model="filterIsQuiet" />
            <span>🔇 静音区</span>
          </label>
          <!-- 图例说明 -->
          <div class="legend-inline">
            <span class="legend-label">图例说明：</span>
            <div class="legend-items-inline">
              <span class="legend-item-inline">
                <span class="legend-color available"></span>
                <span>空闲</span>
              </span>
              <span class="legend-item-inline">
                <span class="legend-color idle"></span>
                <span>闲置</span>
              </span>
              <span class="legend-item-inline">
                <span class="legend-color reserved"></span>
                <span>已预约</span>
              </span>
              <span class="legend-item-inline">
                <span class="legend-color occupied"></span>
                <span>使用中</span>
              </span>
              <span class="legend-item-inline">
                <span class="legend-color broken"></span>
                <span>故障</span>
              </span>
              <span class="legend-item-inline">
                <span class="legend-color fault"></span>
                <span>维修</span>
              </span>
              <span class="legend-item-inline">
                <span class="legend-color selected"></span>
                <span>已选中</span>
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 座位图区域（支持滚动查看） -->
      <div class="seats-visualization-wrapper">
        <!-- 选中座位信息（浮动在右侧） -->
        <div v-if="selectedSeat" class="selected-seat-floating">
          <div class="info-card">
            <div class="info-card-header">
              <h3>已选座位</h3>
              <button @click="selectedSeat = null" class="close-info-btn" title="关闭">×</button>
            </div>
            <div class="info-content">
              <div class="info-item">
                <span class="label">座位编号：</span>
                <span class="value">{{ selectedSeat.seatCode }}</span>
              </div>
              <div class="info-item">
                <span class="label">区域：</span>
                <span class="value">{{ selectedSeat.area || selectedSeat.zone || '未分类' }}</span>
              </div>
              <div class="info-item">
                <span class="label">状态：</span>
                <span :class="['status-badge', getStatusClass(selectedSeat.statusText || selectedSeat.status)]">
                  {{ getStatusText(selectedSeat.statusText || selectedSeat.status) }}
                </span>
              </div>
              <div class="info-item">
                <span class="label">属性：</span>
                <span class="features">
                  <span v-if="selectedSeat.hasPower" class="feature-tag">🔌 有电源</span>
                  <span v-if="selectedSeat.isWindow" class="feature-tag">🪟 靠窗</span>
                  <span v-if="selectedSeat.zone === '安静区'" class="feature-tag">🔇 静音区</span>
                </span>
              </div>
              <div class="info-item">
                <span class="label">预约时间：</span>
                <span class="value">{{ reserveDate }} {{ startTime }} - {{ endTime }}</span>
              </div>
              <div class="action-buttons">
                <button 
                  @click="confirmReserve" 
                  :disabled="!canReserve"
                  class="btn-reserve"
                >
                  {{ canReserve ? '确认预约' : '该座位不可预约' }}
                </button>
                <button @click="selectedSeat = null" class="btn-cancel">取消选择</button>
              </div>
            </div>
          </div>
        </div>
        
        <!-- 主座位图容器（支持滚动查看） -->
        <div 
          class="seats-visualization"
          ref="visualizationRef"
        >
          <div 
            class="seats-container"
          >
            <div v-if="loading" class="loading">加载中...</div>
            <div v-else-if="seatsByArea.size === 0 && !loading" class="empty-state">
              <p v-if="!reserveDate || !startTime || !endTime">
                请选择预约日期和时段
              </p>
              <p v-else-if="allSeats.length === 0">
                未查询到符合条件的座位，请尝试调整筛选条件或选择其他时间段
              </p>
              <p v-else>
                请选择日期和时段
              </p>
            </div>
            <div v-else class="areas-container">
              <!-- 按区域分组显示座位（只显示选中区域的座位） -->
              <div 
                v-for="[areaName, areaSeats] in seatsByArea" 
                :key="areaName"
                v-show="!selectedArea.value || areaName === selectedArea.value"
                class="area-section"
              >
                <h3 class="area-title">{{ areaName || '未分类区域' }}</h3>
                <div class="seat-grid" :style="getGridStyle(areaSeats)">
                  <div
                    v-for="seat in areaSeats"
                    :key="seat.id"
                    class="seat-item"
                    :class="getSeatClass(seat)"
                    :style="getSeatStyle(seat)"
                    @click="selectSeat(seat)"
                    :title="getSeatTooltip(seat)"
                  >
                    <div class="seat-label">{{ seat.label || seat.seatCode }}</div>
                    <div class="seat-icons">
                      <span v-if="seat.hasPower" class="icon">🔌</span>
                      <span v-if="seat.isWindow" class="icon">🪟</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 消息提示 -->
      <div v-if="message" :class="['message', messageType]">
        <span>{{ message }}</span>
        <button @click.stop="closeMessage" class="close-btn" title="关闭">×</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import http from '../lib/http'
import { useAuthStore } from '../stores/auth'
import { useRouter } from 'vue-router'
import { useRealtimeStore } from '../stores/realtime'

const authStore = useAuthStore()
const router = useRouter()

// 查询条件
const reserveDate = ref('')
const startTime = ref('')
const endTime = ref('')

// 根据当前时间计算合理的默认时间段
function getDefaultTimeSlot() {
  const now = new Date()
  const currentHour = now.getHours()
  const currentMinute = now.getMinutes()
  
  // 如果当前时间在凌晨0点到早上6点之间，不能预约，建议从早上6点开始
  if (currentHour >= 0 && currentHour < 6) {
    return { start: '06:00', end: '10:00' }
  }
  
  // 如果当前时间已经超过晚上23点，建议从明天早上6点开始
  if (currentHour >= 23) {
    return { start: '06:00', end: '10:00' }
  }
  
  // 如果当前时间在6点之前，从6点开始
  if (currentHour < 6) {
    return { start: '06:00', end: '10:00' }
  }
  
  // 建议从下一个整点开始（例如：如果现在是14:30，建议从15:00开始）
  // 如果当前分钟数大于0，需要向上取整到下一个整点
  let suggestedStartHour = currentHour + 1
  
  // 如果下一个整点已经超过22点（因为预约至少需要1小时，23点结束），建议明天
  if (suggestedStartHour > 22) {
    // 如果已经22点或更晚，建议明天
    return { start: '06:00', end: '10:00' }
  }
  
  // 计算结束时间（开始时间 + 2小时，但不超过23:00）
  let endHour = suggestedStartHour + 2
  if (endHour > 23) {
    endHour = 23
    // 如果结束时间被限制为23点，确保开始时间至少提前1小时
    // 但开始时间不能小于当前时间的下一个整点
    if (suggestedStartHour >= 23) {
      suggestedStartHour = 22
    }
  }
  
  const startTimeStr = `${String(suggestedStartHour).padStart(2, '0')}:00`
  const endTimeStr = `${String(endHour).padStart(2, '0')}:00`
  
  console.log('计算默认时间段:', {
    当前时间: `${currentHour}:${String(currentMinute).padStart(2, '0')}`,
    建议开始时间: startTimeStr,
    建议结束时间: endTimeStr
  })
  
  return { start: startTimeStr, end: endTimeStr }
}
const selectedBuilding = ref('A楼')
const selectedArea = ref('')

// 筛选条件
const filterHasPower = ref(false)
const filterIsWindow = ref(false)
const filterIsQuiet = ref(false)

// 数据
const allSeats = ref<any[]>([])
const seatsByArea = ref<Map<string, any[]>>(new Map())
const buildings = ref<string[]>([])
const floors = ref<number[]>([])
const areas = ref<string[]>([])
const selectedSeat = ref<any>(null)
const loading = ref(false)
const message = ref('')
const messageType = ref<'success' | 'error'>('success')
let messageTimer: ReturnType<typeof setTimeout> | null = null

// WebSocket 相关（全局连接）
const realtime = useRealtimeStore()
let offSeatMsg: null | (() => void) = null
const wsConnected = ref(false)

// 可视化容器引用
const visualizationRef = ref<HTMLElement | null>(null)

// miniMap 相关（已移除缩略图功能）

// 计算最小日期（今天）和最大日期（次日）
const minDate = computed(() => {
  const today = new Date()
  return today.toISOString().split('T')[0]
})

const maxDate = computed(() => {
  const tomorrow = new Date()
  tomorrow.setDate(tomorrow.getDate() + 1)
  return tomorrow.toISOString().split('T')[0]
})

// 计算网格样式（基于所有座位计算，确保网格大小固定）
// 注意：隐藏的座位使用display: none隐藏，不占据位置
// 但网格大小必须基于所有座位（包括隐藏的）来计算，这样座位的grid-row和grid-column才能正确对应
const getGridStyle = (seats: any[]) => {
  if (seats.length === 0) return {}
  
  // 基于所有座位（包括隐藏的）计算网格大小
  // 这样座位的grid-row和grid-column才能正确对应固定的网格大小
  const allSeatsForGrid = allSeats.value.length > 0 ? allSeats.value : seats
  
  // 计算最大行数和列数（基于rowNum和colNum）
  const rows = allSeatsForGrid.map(s => s.rowNum || s.row || 0).filter(r => r > 0)
  const cols = allSeatsForGrid.map(s => s.colNum || s.col || 0).filter(c => c > 0)
  
  const maxRow = rows.length > 0 ? Math.max(...rows) : 0
  const maxCol = cols.length > 0 ? Math.max(...cols) : 0
  
  // 如果无法从坐标计算，根据座位数量合理估算
  if (maxCol <= 0) {
    const estimatedCols = Math.ceil(Math.sqrt(allSeatsForGrid.length)) * 2
    return {
      gridTemplateColumns: `repeat(${Math.min(Math.max(estimatedCols, 6), 8)}, 1fr)`,
      gap: '6px'
    }
  }
  
  // 使用所有座位的最大列数和行数（固定网格大小）
  // 使用固定宽度列（而非1fr），避免空列时座位被拉伸
  const colWidth = '70px'
  return {
    gridTemplateColumns: `repeat(${maxCol}, ${colWidth})`,
    gridTemplateRows: maxRow > 0 ? `repeat(${maxRow}, auto)` : undefined,
    gap: '6px',
    justifyContent: 'start' // 左对齐，避免空列时内容居中
  }
}

// 获取座位样式（根据row_num和col_num固定定位，保持原位置）
const getSeatStyle = (seat: any) => {
  const styles: any = {}
  
  // 使用 row_num 和 col_num 固定定位，保持座位在原始位置
  // 这样筛选时，不符合条件的座位隐藏后，其他座位不会挤在一起
  const rowNum = seat.rowNum || seat.row || 0
  const colNum = seat.colNum || seat.col || 0
  
  if (rowNum > 0 && colNum > 0) {
    styles.gridRow = rowNum
    styles.gridColumn = colNum
  }
  
  return styles
}

// 获取座位类名
const getSeatClass = (seat: any) => {
  const classes: string[] = []
  
  // 如果座位不符合筛选条件，添加hidden类（隐藏但保持位置）
  if (seat.shouldShow === false) {
    classes.push('seat-hidden')
  }
  
  // 状态类：支持数字状态（0-空闲、1-已预约、2-使用中、3-故障、4-维修）和字符串状态
  // 优先使用statusText（字符串状态），如果没有则使用status（数字状态）
  const status = seat.statusText || seat.status
  
  if (typeof status === 'number') {
    // 数字状态
    if (status === 0) {
      classes.push('available')
    } else if (status === 1) {
      classes.push('reserved')
    } else if (status === 2) {
      classes.push('occupied')
    } else if (status === 3) {
      classes.push('broken')
    } else if (status === 4) {
      classes.push('fault')
    }
  } else {
    // 字符串状态
    if (status === 'FREE') {
      classes.push('available')
    } else if (status === 'IDLE') {
      classes.push('idle')
    } else if (status === 'RESERVED') {
      classes.push('reserved')
    } else if (status === 'OCCUPIED') {
      classes.push('occupied')
    } else if (status === 'BROKEN') {
      classes.push('broken')
    } else if (status === 'FAULT') {
      classes.push('fault')
    }
  }
  
  // 选中状态
  if (selectedSeat.value && selectedSeat.value.id === seat.id) {
    classes.push('selected')
  }
  
  // 不可点击状态（只有FREE和IDLE可以点击）
  // 优先使用statusText，如果没有则使用status
  const actualStatus = seat.statusText || seat.status
  if (typeof actualStatus === 'number') {
    if (actualStatus !== 0) {
      classes.push('disabled')
    }
  } else {
    if (actualStatus !== 'FREE' && actualStatus !== 'IDLE') {
      classes.push('disabled')
    }
  }
  
  return classes.join(' ')
}

// 获取座位提示信息
const getSeatTooltip = (seat: any) => {
  // 优先使用statusText，如果没有则使用status
  const status = seat.statusText || seat.status
  let tooltip = `${seat.seatCode || seat.label}\n状态：${getStatusText(status)}`
  if (seat.hasPower) tooltip += '\n有电源'
  if (seat.isWindow) tooltip += '\n靠窗'
  return tooltip
}

// 获取状态文本（支持数字和字符串状态）
const getStatusText = (status: any) => {
  // 如果是数字状态
  if (typeof status === 'number') {
    const numStatusMap: Record<number, string> = {
      0: '空闲',
      0.5: '闲置',
      1: '已预约',
      2: '使用中',
      3: '故障',
      4: '维修'
    }
    return numStatusMap[status] || '未知'
  }
  
  // 如果是字符串状态
  const statusMap: Record<string, string> = {
    'FREE': '空闲',
    'IDLE': '闲置',
    'RESERVED': '已预约',
    'OCCUPIED': '使用中',
    'BROKEN': '故障',
    'FAULT': '维修'
  }
  return statusMap[status] || status || '未知'
}

// 获取状态类名（支持数字和字符串状态）
const getStatusClass = (status: any) => {
  // 如果是数字状态
  if (typeof status === 'number') {
    if (status === 0) return 'status-available'
    if (status === 0.5) return 'status-idle'
    if (status === 1) return 'status-reserved'
    if (status === 2) return 'status-occupied'
    if (status === 3) return 'status-broken'
    if (status === 4) return 'status-fault'
    return 'status-available'
  }
  
  // 如果是字符串状态
  if (status === 'FREE') return 'status-available'
  if (status === 'IDLE') return 'status-idle'
  if (status === 'RESERVED') return 'status-reserved'
  if (status === 'OCCUPIED') return 'status-occupied'
  if (status === 'BROKEN') return 'status-broken'
  if (status === 'FAULT') return 'status-fault'
  
  return 'status-available'
}

// 是否可以预约
const canReserve = computed(() => {
  if (!selectedSeat.value) return false
  if (!reserveDate.value || !startTime.value || !endTime.value) return false
  
  const status = selectedSeat.value.status
  const statusText = selectedSeat.value.statusText
  
  // 优先使用statusText（字符串状态），如果没有则使用status（数字状态）
  const actualStatus = statusText || status
  
  // 只有FREE和IDLE状态的座位可以预约，RESERVED、OCCUPIED、BROKEN、FAULT都不能预约
  // 无论管理员还是普通用户，都不能预约已预约、使用中、故障或维修中的座位
  if (typeof actualStatus === 'number') {
    return actualStatus === 0
  }
  // 只有空闲和闲置状态的座位可以预约
  return actualStatus === 'FREE' || actualStatus === 'IDLE'
})

// 选择座位
function selectSeat(seat: any) {
  // 如果座位被隐藏（不符合筛选条件），不允许选择
  if (seat.shouldShow === false) {
    return
  }
  
  const status = seat.status
  const statusText = seat.statusText
  // 优先使用statusText（字符串状态），如果没有则使用status（数字状态）
  const actualStatus = statusText || status
  
  // 只有FREE和IDLE状态的座位可以选择
  // 如果是数字状态，只有0（空闲）可以选择
  if (typeof actualStatus === 'number') {
    if (actualStatus !== 0) {
      let statusText = '不可用'
      if (actualStatus === 1) statusText = '已预约'
      else if (actualStatus === 2) statusText = '使用中'
      else if (actualStatus === 3) statusText = '故障或维修中'
      showMessage(`该座位${statusText}，不可选择`, 'error', true)
      return
    }
  } else {
    // 字符串状态
    if (actualStatus !== 'FREE' && actualStatus !== 'IDLE') {
      let statusText = '不可用'
      if (actualStatus === 'RESERVED') statusText = '已预约'
      else if (actualStatus === 'OCCUPIED') statusText = '使用中'
      else if (actualStatus === 'BROKEN') statusText = '故障'
      else if (actualStatus === 'FAULT') statusText = '维修'
      showMessage(`该座位${statusText}，不可选择`, 'error', true)
      return
    }
  }
  
  selectedSeat.value = seat
  message.value = ''
}

// 显示消息（自动关闭）
function showMessage(msg: string, type: 'success' | 'error' = 'success', autoClose: boolean = true) {
  // 清除之前的定时器
  if (messageTimer) {
    clearTimeout(messageTimer)
    messageTimer = null
  }
  
  message.value = msg
  messageType.value = type
  
  // 如果启用自动关闭，根据消息类型设置不同的关闭时间
  if (autoClose) {
    // 成功消息3秒后关闭，错误消息4秒后关闭（让用户有更多时间阅读）
    const delay = type === 'success' ? 3000 : 4000
    messageTimer = setTimeout(() => {
      message.value = ''
      messageTimer = null
    }, delay)
  }
}

// 关闭消息提示
function closeMessage() {
  // 清除定时器
  if (messageTimer) {
    clearTimeout(messageTimer)
    messageTimer = null
  }
  message.value = ''
}

// 查询座位
async function querySeats() {
  if (!reserveDate.value || !startTime.value || !endTime.value) {
    showMessage('请选择日期和时段', 'error')
    return
  }
  
  // 验证时间：禁止凌晨0点到早上6点之间的预约
  const [startHour, startMinute] = startTime.value.split(':').map(Number)
  const [endHour, endMinute] = endTime.value.split(':').map(Number)
  
  // 检查开始时间是否在禁止时段（0:00-6:00）
  if (startHour >= 0 && startHour < 6) {
    showMessage('凌晨0点到早上6点之间不能预约座位', 'error')
    return
  }
  
  // 检查结束时间是否在禁止时段（0:00-6:00）
  // 如果结束时间跨越了0点，需要检查
  if (endHour >= 0 && endHour < 6) {
    showMessage('预约结束时间不能超过晚上12点（24:00）', 'error')
    return
  }
  
  // 验证时间
  if (startTime.value >= endTime.value) {
    showMessage('结束时间必须晚于开始时间', 'error')
    return
  }
  
  // 验证预约时长不超过4小时
  const start = new Date(`${reserveDate.value}T${startTime.value}`)
  const end = new Date(`${reserveDate.value}T${endTime.value}`)
  const durationHours = (end.getTime() - start.getTime()) / (1000 * 60 * 60)
  if (durationHours > 4) {
    showMessage('单次预约时长不能超过4小时', 'error')
    return
  }
  
  // 验证结束时间不超过23:59
  if (endHour > 23 || (endHour === 23 && endMinute > 59)) {
    showMessage('预约结束时间不能超过晚上12点（24:00）', 'error')
    return
  }
  
  loading.value = true
  message.value = ''
  selectedSeat.value = null
  
  try {
    // 组合时间段
    const timeSlot = `${startTime.value}-${endTime.value}`
    const params: any = {
      reserveDate: reserveDate.value,
      timeSlot: timeSlot,
      building: 'A楼' // 固定为A楼
    }
    
    // 从区域名称中提取楼层信息（"一楼" -> 1, "二楼" -> 2）
    let floor: number | null = null
    if (selectedArea.value) {
      if (selectedArea.value.includes('一楼')) {
        floor = 1
      } else if (selectedArea.value.includes('二楼')) {
        floor = 2
      }
    }
    
    // 如果选择了区域，根据区域名称设置楼层
    if (floor != null) {
      params.floor = floor
    }
    // 如果没有选择区域，不传递floor参数，后端会返回所有楼层的座位
    
    // 区域筛选（如果选择了区域）
    if (selectedArea.value) {
      params.area = selectedArea.value
    }
    
    const res = await http.get('/api/seat/query', { params })
    const data = res.data || {}
    
    // 处理返回的数据
    const allSeatsList = data.seats || []
    
    if (allSeatsList.length === 0) {
      showMessage('未查询到座位数据，请检查数据库是否有座位数据', 'error', true)
      seatsByArea.value = new Map()
      allSeats.value = []
      return
    }
    
    // 首先过滤掉B楼座位（只保留A楼）
    const aBuildingSeats = allSeatsList.filter((s: any) => s.building === 'A楼')
    
    // 不删除座位，而是标记哪些座位应该显示（保持原位置）
    // 为每个座位添加一个shouldShow标记
    const seatsWithVisibility = aBuildingSeats.map((seat: any) => {
      let shouldShow = true
      
      // 筛选：有电源（必须严格匹配）
      if (filterHasPower.value) {
        const hasPower = seat.hasPower === true || seat.hasPower === 1 || seat.hasPower === '1'
        if (!hasPower) shouldShow = false
      }
      
      // 筛选：靠窗（必须严格匹配）
      if (filterIsWindow.value) {
        const isWindow = seat.isWindow === true || seat.isWindow === 1 || seat.isWindow === '1'
        if (!isWindow) shouldShow = false
      }
      
      // 筛选：静音区（必须严格匹配zone字段）
      if (filterIsQuiet.value) {
        if (seat.zone !== '安静区') shouldShow = false
      }
      
      return { ...seat, shouldShow }
    })
    
    // 用于分组的座位列表（包含所有座位，用于保持位置）
    let filteredSeats = seatsWithVisibility
    
    // 如果选择了区域，更新shouldShow标记（只显示选中区域的座位）
    if (selectedArea.value) {
      seatsWithVisibility.forEach((seat: any) => {
        // 检查 area 字段是否严格匹配选中的区域
        if (seat.area === selectedArea.value) {
          // 区域匹配，保持shouldShow的当前值（由其他筛选条件决定）
          // 不改变shouldShow，因为可能已经被其他筛选条件设置为false
        } else {
          // 区域不匹配，隐藏
          seat.shouldShow = false
        }
      })
    }
    
    // 按区域分组（只使用area字段，确保不会出现重复分组）
    const grouped = new Map<string, any[]>()
    seatsWithVisibility.forEach((seat: any) => {
      // 如果选择了区域，只分组匹配的区域
      if (selectedArea.value && seat.area !== selectedArea.value) {
        return // 跳过不匹配的区域
      }
      
      // 只使用 area 字段进行分组（数据库中的座位都有area字段，如"一楼安静区"、"一楼自习区"等）
      let area = seat.area
      
      // 如果 area 为空（理论上不应该发生），使用"未分类区域"
      if (!area || area.trim() === '') {
        area = '未分类区域'
      }
      
      // 标准化区域名称：去除前后空格
      const normalizedArea = area.trim()
      
      if (!grouped.has(normalizedArea)) {
        grouped.set(normalizedArea, [])
      }
      grouped.get(normalizedArea)!.push(seat)
    })
    
    // 调试日志（仅在开发环境打印）
    if (import.meta.env.DEV) {
      const visibleCount = seatsWithVisibility.filter((s: any) => s.shouldShow !== false).length
      console.log('座位查询结果:', {
        总座位数: seatsWithVisibility.length,
        可见座位数: visibleCount,
        区域数: grouped.size,
        可用: data.available || 0,
        选择的区域: selectedArea.value || '全部',
        筛选条件: {
          有电源: filterHasPower.value,
          靠窗: filterIsWindow.value,
          静音区: filterIsQuiet.value
        },
        区域分组详情: Array.from(grouped.entries()).map(([area, seats]) => ({
          区域: area,
          座位数: seats.length,
          座位列表: seats.map(s => s.label)
        }))
      })
    }
    
    // 对每个区域的座位进行排序（按rowNum和colNum）
    grouped.forEach((seats, area) => {
      seats.sort((a, b) => {
        const rowA = a.rowNum || a.row || 0
        const rowB = b.rowNum || b.row || 0
        const colA = a.colNum || a.col || 0
        const colB = b.colNum || b.col || 0
        if (rowA !== rowB) return rowA - rowB
        return colA - colB
      })
    })
    
    seatsByArea.value = grouped
    // allSeats保存所有座位（包括隐藏的），用于保持位置
    allSeats.value = seatsWithVisibility
    
    // 显示查询结果消息，3秒后自动关闭
    showMessage(`查询到 ${filteredSeats.length} 个座位，可用 ${data.available || 0} 个`, 'success', true)
  } catch (e: any) {
    console.error('查询座位失败', e)
    showMessage('查询失败：' + (e?.response?.data?.message || e?.message || '未知错误'), 'error', true)
    seatsByArea.value = new Map()
  } finally {
    loading.value = false
  }
}

// 确认预约
async function confirmReserve() {
  // 检查是否已登录
  if (!authStore.isAuthenticated) {
    showMessage('请先登录后再预约', 'error', true)
    router.push({ name: 'login', query: { redirect: '/seats-map' } })
    return
  }
  
  if (!selectedSeat.value || !reserveDate.value || !startTime.value || !endTime.value) {
    showMessage('请选择座位和预约时间', 'error', true)
    return
  }
  
  if (!canReserve.value) {
    showMessage('该座位不可预约', 'error', true)
    return
  }
  
  // 验证预约时长不超过4小时
  const start = new Date(`${reserveDate.value}T${startTime.value}`)
  const end = new Date(`${reserveDate.value}T${endTime.value}`)
  const durationHours = (end.getTime() - start.getTime()) / (1000 * 60 * 60)
  if (durationHours > 4) {
    showMessage('单次预约时长不能超过4小时', 'error', true)
    return
  }
  
  // 验证时间：禁止凌晨0点到早上6点之间的预约
  const [startHour] = startTime.value.split(':').map(Number)
  const [endHour, endMinute] = endTime.value.split(':').map(Number)
  
  // 检查开始时间是否在禁止时段（0:00-6:00）
  if (startHour >= 0 && startHour < 6) {
    showMessage('凌晨0点到早上6点之间不能预约座位', 'error', true)
    return
  }
  
  // 验证结束时间不超过23:59
  if (endHour > 23 || (endHour === 23 && endMinute > 59)) {
    showMessage('预约结束时间不能超过晚上12点（24:00）', 'error', true)
    return
  }
  
  try {
    // 组合时间（ISO格式：YYYY-MM-DDTHH:mm:ss）
    const startTimeStr = `${reserveDate.value}T${startTime.value}:00`
    const endTimeStr = `${reserveDate.value}T${endTime.value}:00`
    
    const res = await http.post('/api/seat/reserve', {
      seatId: selectedSeat.value.id,
      reserveDate: reserveDate.value,
      startTime: startTimeStr,
      endTime: endTimeStr
    })
    
    showMessage('预约成功！', 'success', true)
    selectedSeat.value = null
    
    // 重新查询座位状态
    await querySeats()
    
    // 延迟跳转到我的预约页面
    setTimeout(() => {
      router.push('/my-reservations')
    }, 1500)
  } catch (e: any) {
    console.error('预约失败', e)
    showMessage('预约失败：' + (e?.response?.data?.message || e?.message || '未知错误'), 'error', true)
  }
}

// 加载区域列表（固定为4个区域选项）
async function loadOptions() {
  try {
    // 固定区域列表：只显示4个区域
    areas.value = ['一楼安静区', '一楼自习区', '二楼安静区', '二楼自习区']
  } catch (e: any) {
    console.error('加载选项失败', e)
  }
}

// 全局 WebSocket：连接状态（用于页面指示器）
watch(
  () => realtime.seatConnected,
  () => {
    wsConnected.value = !!realtime.seatConnected
  },
  { immediate: true, deep: false }
)

// 处理 WebSocket 消息
function handleWebSocketMessage(data: any) {
  if (data.type === 'seatStatusUpdate') {
    // 更新对应座位的状态
    const seatId = data.seatId
    const newStatus = data.status
    const statusText = data.statusText
    
    // 更新所有区域中的座位状态
    seatsByArea.value.forEach((seats, areaName) => {
      const seat = seats.find(s => s.id === seatId)
      if (seat) {
        seat.status = newStatus
        seat.statusText = statusText
        console.log(`座位 ${seat.seatCode} 状态更新为: ${statusText}`)
      }
    })
    
    // 同时更新 allSeats 中的状态
    const seatInAll = allSeats.value.find(s => s.id === seatId)
    if (seatInAll) {
      seatInAll.status = newStatus
      seatInAll.statusText = statusText
    }
    
    // 如果当前选中的座位状态更新了，也更新选中座位信息
    if (selectedSeat.value && selectedSeat.value.id === seatId) {
      selectedSeat.value.status = newStatus
      selectedSeat.value.statusText = statusText
    }
  } else if (data.type === 'seatStatusRefresh') {
    // 如果收到刷新消息，重新查询座位
    if (reserveDate.value && startTime.value && endTime.value) {
      querySeats()
    }
  }
}

// 关闭 WebSocket 连接：由 App 统一管理，这里只注销订阅
function disconnectWebSocket() {
  if (offSeatMsg) {
    offSeatMsg()
    offSeatMsg = null
  }
}


// 获取最小开始时间（如果选择今天，不能早于当前时间）
const minStartTime = computed(() => {
  if (!reserveDate.value || reserveDate.value !== minDate.value) {
    return '06:00' // 如果不是今天，最早从6点开始（禁止0-6点）
  }
  
  // 如果选择的是今天，开始时间至少是当前时间的下一个整点
  const now = new Date()
  const currentHour = now.getHours()
  const currentMinute = now.getMinutes()
  
  // 如果当前时间在凌晨0点到早上6点之间，最早从6点开始
  if (currentHour >= 0 && currentHour < 6) {
    return '06:00'
  }
  
  // 如果当前时间已经超过23点，返回23点（但实际应该建议明天）
  if (currentHour >= 23) {
    return '23:00'
  }
  
  // 如果当前时间在6点之前，最早从6点开始
  if (currentHour < 6) {
    return '06:00'
  }
  
  // 从下一个整点开始（至少1小时后）
  let minHour = currentHour + 1
  
  // 确保不超过23点（因为预约至少需要1小时，24点结束）
  minHour = Math.min(minHour, 23)
  
  return `${String(minHour).padStart(2, '0')}:00`
})

// 获取最小结束时间（至少比开始时间晚1小时）
const minEndTime = computed(() => {
  if (!startTime.value) {
    return '07:00'
  }
  
  const [startHour] = startTime.value.split(':').map(Number)
  let endHour = startHour + 1 // 至少1小时后
  endHour = Math.min(endHour, 23) // 不超过23点（实际是23:59）
  
  return `${String(endHour).padStart(2, '0')}:00`
})

// 兼容函数（用于模板中的方法调用）
function getMinStartTime(): string {
  return minStartTime.value
}

function getMinEndTime(): string {
  return minEndTime.value
}

// 根据选择的日期自动调整时间
function updateTimeSlotForDate(selectedDate: string) {
  const today = minDate.value
  
  // 如果选择的是今天，根据当前时间设置合理的时间段
  if (selectedDate === today) {
    const timeSlot = getDefaultTimeSlot()
    startTime.value = timeSlot.start
    endTime.value = timeSlot.end
    console.log('更新时间段（今天）:', {
      当前时间: new Date().toLocaleTimeString(),
      开始时间: timeSlot.start,
      结束时间: timeSlot.end
    })
  } else {
    // 如果选择的是明天，使用默认的上午时段（从6点开始）
    startTime.value = '06:00'
    endTime.value = '10:00'
  }
}

// 监听日期变化，自动调整时间
watch(reserveDate, (newDate, oldDate) => {
  if (newDate) {
    // 如果切换到今天，重新计算时间
    if (newDate === minDate.value) {
      updateTimeSlotForDate(newDate)
    } else {
      // 如果是明天，使用默认时间
      startTime.value = '06:00'
      endTime.value = '10:00'
    }
  }
})

// 监听当前日期变化，如果用户停留在今天，定期更新时间段
let timeUpdateInterval: ReturnType<typeof setInterval> | null = null

watch([reserveDate], () => {
  // 清除旧的定时器
  if (timeUpdateInterval) {
    clearInterval(timeUpdateInterval)
    timeUpdateInterval = null
  }
  
  // 如果选择的是今天，每分钟检查一次是否需要更新时间
  if (reserveDate.value === minDate.value) {
    timeUpdateInterval = setInterval(() => {
      const now = new Date()
      const currentHour = now.getHours()
      const [currentStartHour] = startTime.value.split(':').map(Number)
      
      // 如果当前时间已经超过了设置的开始时间，重新计算
      if (currentHour >= currentStartHour) {
        const timeSlot = getDefaultTimeSlot()
        if (timeSlot.start !== startTime.value) {
          startTime.value = timeSlot.start
          endTime.value = timeSlot.end
          console.log('自动更新时间段:', {
            当前时间: now.toLocaleTimeString(),
            新开始时间: timeSlot.start,
            新结束时间: timeSlot.end
          })
        }
      }
    }, 60000) // 每分钟检查一次
  }
}, { immediate: true })

// 监听筛选条件变化，自动查询（包括区域和筛选条件）
watch([selectedBuilding, selectedArea, reserveDate, startTime, endTime, filterHasPower, filterIsWindow, filterIsQuiet], () => {
  if (reserveDate.value && startTime.value && endTime.value) {
    querySeats()
  }
}, { immediate: false })

onMounted(async () => {
  // 设置默认日期为今天
  reserveDate.value = minDate.value
  // 根据当前时间设置合理的时间段（实时计算）
  const timeSlot = getDefaultTimeSlot()
  startTime.value = timeSlot.start
  endTime.value = timeSlot.end
  console.log('组件挂载，初始化时间段:', {
    当前时间: new Date().toLocaleTimeString(),
    开始时间: timeSlot.start,
    结束时间: timeSlot.end
  })
  
  // 先加载选项
  await loadOptions()
  
  // 订阅全局 WebSocket（实时更新）
  offSeatMsg = realtime.onSeatMessage((data: any) => {
    handleWebSocketMessage(data)
  })
  
  // 延迟一下确保所有数据都准备好，然后自动查询一次座位
  setTimeout(() => {
    if (reserveDate.value && startTime.value && endTime.value) {
      querySeats()
    }
  }, 300)
})

onUnmounted(() => {
  // 组件卸载时清除定时器和关闭 WebSocket
  if (messageTimer) {
    clearTimeout(messageTimer)
    messageTimer = null
  }
  if (timeUpdateInterval) {
    clearInterval(timeUpdateInterval)
    timeUpdateInterval = null
  }
  disconnectWebSocket()
})
</script>

<style scoped src="../styles/views/SeatsMap.css"></style>
