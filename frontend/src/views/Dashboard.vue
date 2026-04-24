<template>
  <div class="dashboard">
    <!-- 错误提示 -->
    <div v-if="error" class="message error-message">
      <span>{{ error }}</span>
      <button @click="error = ''" class="close-btn">×</button>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading && !summary" class="loading-container">
      <div class="spinner"></div>
      <p>正在加载数据...</p>
    </div>

    <!-- 主要内容 -->
    <div v-else-if="summary" class="dashboard-content">
      <!-- 统计卡片 -->
      <div class="stats-grid">
        <div class="stat-card stat-card-primary">
          <div class="stat-icon">🪑</div>
          <div class="stat-content">
            <div class="stat-label">总座位数</div>
            <div class="stat-value">{{ summary.totalSeats }}</div>
          </div>
        </div>

        <div class="stat-card stat-card-warning">
          <div class="stat-icon">📅</div>
          <div class="stat-content">
            <div class="stat-label">进行中预约</div>
            <div class="stat-value">{{ summary.reservedNow }}</div>
            <div class="stat-percentage">
              {{ summary.totalSeats > 0 ? Math.round((summary.reservedNow / summary.totalSeats) * 100) : 0 }}%
            </div>
          </div>
        </div>

        <div class="stat-card stat-card-success">
          <div class="stat-icon">✅</div>
          <div class="stat-content">
            <div class="stat-label">使用中</div>
            <div class="stat-value">{{ summary.occupiedNow }}</div>
            <div class="stat-percentage">
              {{ summary.totalSeats > 0 ? Math.round((summary.occupiedNow / summary.totalSeats) * 100) : 0 }}%
            </div>
          </div>
        </div>

        <div class="stat-card stat-card-info">
          <div class="stat-icon">🆓</div>
          <div class="stat-content">
            <div class="stat-label">空闲座位</div>
            <div class="stat-value">{{ summary.idleNow }}</div>
            <div class="stat-percentage">
              {{ summary.totalSeats > 0 ? Math.round((summary.idleNow / summary.totalSeats) * 100) : 0 }}%
            </div>
          </div>
        </div>

        <div class="stat-card stat-card-books">
          <div class="stat-icon">📚</div>
          <div class="stat-content">
            <div class="stat-label">图书总数</div>
            <div class="stat-value">{{ summary.totalBooks }}</div>
            <div class="stat-sub">可借阅：{{ summary.borrowableBooks }}</div>
          </div>
        </div>

        <div class="stat-card stat-card-borrow">
          <div class="stat-icon">📖</div>
          <div class="stat-content">
            <div class="stat-label">借阅中</div>
            <div class="stat-value">{{ summary.activeBorrows }}</div>
          </div>
        </div>
      </div>

      <!-- 座位使用率可视化 -->
      <div class="chart-card usage-card">
        <h3 class="chart-title">
          <span class="icon">📈</span>
          座位使用率
        </h3>
        <div class="usage-bar">
          <div
            class="usage-segment usage-reserved"
            :style="{ width: reservedPercentage + '%' }"
            :title="`进行中预约（未签到入座）: ${reservedBarCount}，进行中预约总数: ${summary.reservedNow}`"
          >
            <span v-if="reservedPercentage > 5">{{ reservedBarCount }}</span>
          </div>
          <div class="usage-segment usage-occupied" :style="{ width: occupiedPercentage + '%' }" :title="`使用中: ${summary.occupiedNow}`">
            <span v-if="occupiedPercentage > 5">{{ summary.occupiedNow }}</span>
          </div>
          <div class="usage-segment usage-idle" :style="{ width: idlePercentage + '%' }" :title="`空闲: ${summary.idleNow}`">
            <span v-if="idlePercentage > 5">{{ summary.idleNow }}</span>
          </div>
        </div>
        <div class="usage-legend">
          <div class="legend-item">
            <span class="legend-color reserved"></span>
            <span>进行中预约</span>
          </div>
          <div class="legend-item">
            <span class="legend-color occupied"></span>
            <span>使用中</span>
          </div>
          <div class="legend-item">
            <span class="legend-color idle"></span>
            <span>空闲</span>
          </div>
        </div>
      </div>

      <!-- 图表区域：两个折线图并排显示 -->
      <div class="charts-row">
        <!-- 近7天预约趋势 -->
        <div class="chart-card chart-card-half">
          <h3 class="chart-title">
            <span class="icon">📊</span>
            近7天预约趋势
          </h3>
          <div class="trend-chart">
            <div class="line-chart-wrapper">
              <!-- Y轴刻度 -->
              <div class="y-axis-labels">
                <div 
                  v-for="(label, index) in reservationYAxisLabels" 
                  :key="index"
                  class="y-axis-label"
                >
                  {{ label }}
                </div>
              </div>
              <div class="line-chart-container">
                <svg class="line-chart" viewBox="0 0 800 150" preserveAspectRatio="none">
                  <!-- 网格线 -->
                  <defs>
                    <pattern id="grid" width="100" height="30" patternUnits="userSpaceOnUse">
                      <path d="M 100 0 L 0 0 0 30" fill="none" stroke="#e5e7eb" stroke-width="0.5"/>
                    </pattern>
                  </defs>
                  <rect width="100%" height="100%" fill="url(#grid)"/>
                  
                  <!-- 填充区域 -->
                  <polygon
                    :points="reservationAreaPoints"
                    fill="rgba(102, 126, 234, 0.2)"
                    stroke="none"
                  />
                  
                  <!-- 折线 -->
                  <polyline
                    :points="linePoints"
                    fill="none"
                    stroke="#667eea"
                    stroke-width="2.5"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                  
                  <!-- 数据点 -->
                  <circle
                    v-for="(point, index) in linePointsArray"
                    :key="index"
                    :cx="point.x"
                    :cy="point.y"
                    r="4"
                    fill="#667eea"
                    stroke="white"
                    stroke-width="1.5"
                    class="data-point"
                    :title="`${formatDate(reservationTrend[index]?.date || '')}: ${reservationTrend[index]?.reservations || 0} 个预约`"
                  />
                </svg>
                <!-- X轴标签 -->
                <div class="chart-labels">
                  <div 
                    v-for="item in reservationTrend" 
                    :key="item.date"
                    class="chart-label"
                  >
                    {{ formatDateShort(item.date) }}
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="trend-summary">
            <div class="summary-item">
              <span class="summary-label">总计：</span>
              <span class="summary-value">{{ totalReservations }}</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">日均：</span>
              <span class="summary-value">{{ averageReservations }}</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">最高：</span>
              <span class="summary-value">{{ maxReservations }}</span>
            </div>
          </div>
        </div>

        <!-- 近7天借阅趋势 -->
        <div class="chart-card chart-card-half">
          <h3 class="chart-title">
            <span class="icon">📈</span>
            近7天借阅趋势
          </h3>
          <div class="trend-chart">
            <div class="line-chart-wrapper">
              <!-- Y轴刻度 -->
              <div class="y-axis-labels">
                <div 
                  v-for="(label, index) in borrowYAxisLabels" 
                  :key="index"
                  class="y-axis-label"
                >
                  {{ label }}
                </div>
              </div>
              <div class="line-chart-container">
                <svg class="line-chart" viewBox="0 0 800 150" preserveAspectRatio="none">
                  <defs>
                    <pattern id="grid2" width="100" height="30" patternUnits="userSpaceOnUse">
                      <path d="M 100 0 L 0 0 0 30" fill="none" stroke="#e5e7eb" stroke-width="0.5"/>
                    </pattern>
                  </defs>
                  <rect width="100%" height="100%" fill="url(#grid2)"/>
                  
                  <!-- 填充区域 -->
                  <polygon
                    :points="borrowAreaPoints"
                    fill="rgba(16, 185, 129, 0.2)"
                    stroke="none"
                  />
                  
                  <!-- 折线 -->
                  <polyline
                    :points="borrowLinePoints"
                    fill="none"
                    stroke="#10b981"
                    stroke-width="2.5"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                  
                  <!-- 数据点 -->
                  <circle
                    v-for="(point, index) in borrowLinePointsArray"
                    :key="index"
                    :cx="point.x"
                    :cy="point.y"
                    r="4"
                    fill="#10b981"
                    stroke="white"
                    stroke-width="1.5"
                    class="data-point"
                    :title="`${formatDate(borrowTrend[index]?.date || '')}: ${borrowTrend[index]?.borrows || 0} 次借阅`"
                  />
                </svg>
                <div class="chart-labels">
                  <div 
                    v-for="item in borrowTrend" 
                    :key="item.date"
                    class="chart-label"
                  >
                    {{ formatDateShort(item.date) }}
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="trend-summary">
            <div class="summary-item">
              <span class="summary-label">总计：</span>
              <span class="summary-value">{{ totalBorrows }}</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">日均：</span>
              <span class="summary-value">{{ averageBorrows }}</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">最高：</span>
              <span class="summary-value">{{ maxBorrows }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import http from '../lib/http'

const summary = ref<any>(null)
const reservationTrend = ref<any[]>([])
const borrowTrend = ref<any[]>([])
const loading = ref(false)
const error = ref('')
const updateTime = ref('')

// 条形图三色之和需等于总座位：空闲 = 总座位 - 进行中预约数，故橙色宽度用「进行中 − 使用中」避免与绿色重复计数
const reservedBarCount = computed(() => {
  if (!summary.value) return 0
  const r = Number(summary.value.reservedNow) || 0
  const o = Number(summary.value.occupiedNow) || 0
  return Math.max(0, r - o)
})

const reservedPercentage = computed(() => {
  if (!summary.value || summary.value.totalSeats === 0) return 0
  return (reservedBarCount.value / summary.value.totalSeats) * 100
})

const occupiedPercentage = computed(() => {
  if (!summary.value || summary.value.totalSeats === 0) return 0
  return (summary.value.occupiedNow / summary.value.totalSeats) * 100
})

const idlePercentage = computed(() => {
  if (!summary.value || summary.value.totalSeats === 0) return 0
  return (summary.value.idleNow / summary.value.totalSeats) * 100
})

// 计算趋势统计
const totalReservations = computed(() => {
  return reservationTrend.value.reduce((sum, item) => sum + item.reservations, 0)
})

const averageReservations = computed(() => {
  if (reservationTrend.value.length === 0) return 0
  return Math.round(totalReservations.value / reservationTrend.value.length)
})

const maxReservations = computed(() => {
  if (reservationTrend.value.length === 0) return 0
  return Math.max(...reservationTrend.value.map(item => item.reservations))
})

const totalBorrows = computed(() => {
  return borrowTrend.value.reduce((sum, item) => sum + item.borrows, 0)
})

const averageBorrows = computed(() => {
  if (borrowTrend.value.length === 0) return 0
  return Math.round(totalBorrows.value / borrowTrend.value.length)
})

const maxBorrows = computed(() => {
  if (borrowTrend.value.length === 0) return 0
  return Math.max(...borrowTrend.value.map(item => item.borrows))
})

// 计算Y轴刻度标签（预约趋势）
const reservationYAxisLabels = computed(() => {
  const max = Math.max(maxReservations.value || 1, 1)
  const step = Math.ceil(max / 5) || 1
  const labels: number[] = []
  for (let i = 0; i <= 5; i++) {
    labels.push(i * step)
  }
  return labels.reverse()
})

// 计算Y轴刻度标签（借阅趋势）
const borrowYAxisLabels = computed(() => {
  const max = Math.max(maxBorrows.value || 1, 1)
  const step = Math.ceil(max / 5) || 1
  const labels: number[] = []
  for (let i = 0; i <= 5; i++) {
    labels.push(i * step)
  }
  return labels.reverse()
})

// 计算折线图坐标点
const linePoints = computed(() => {
  if (reservationTrend.value.length === 0) return ''
  const max = Math.max(maxReservations.value || 1, 1)
  const width = 800
  const height = 150
  const paddingX = 40
  const paddingY = 20
  const chartWidth = width - paddingX * 2
  const chartHeight = height - paddingY * 2
  
  return reservationTrend.value.map((item, index) => {
    const x = paddingX + (index / Math.max(reservationTrend.value.length - 1, 1)) * chartWidth
    const value = item.reservations || 0
    const y = paddingY + chartHeight - (value / max) * chartHeight
    return `${x},${y}`
  }).join(' ')
})

// 计算填充区域坐标点（预约趋势）
const reservationAreaPoints = computed(() => {
  if (reservationTrend.value.length === 0) return ''
  const max = Math.max(maxReservations.value || 1, 1)
  const width = 800
  const height = 150
  const paddingX = 40
  const paddingY = 20
  const chartWidth = width - paddingX * 2
  const chartHeight = height - paddingY * 2
  const bottomY = paddingY + chartHeight
  
  const points: string[] = []
  // 起始点（左下角）
  points.push(`${paddingX},${bottomY}`)
  
  // 数据点
  reservationTrend.value.forEach((item, index) => {
    const x = paddingX + (index / Math.max(reservationTrend.value.length - 1, 1)) * chartWidth
    const value = item.reservations || 0
    const y = paddingY + chartHeight - (value / max) * chartHeight
    points.push(`${x},${y}`)
  })
  
  // 结束点（右下角）
  const lastX = paddingX + chartWidth
  points.push(`${lastX},${bottomY}`)
  
  return points.join(' ')
})

const linePointsArray = computed(() => {
  if (reservationTrend.value.length === 0) return []
  const max = Math.max(maxReservations.value || 1, 1)
  const width = 800
  const height = 150
  const paddingX = 40
  const paddingY = 20
  const chartWidth = width - paddingX * 2
  const chartHeight = height - paddingY * 2
  
  return reservationTrend.value.map((item, index) => {
    const value = item.reservations || 0
    return {
      x: paddingX + (index / Math.max(reservationTrend.value.length - 1, 1)) * chartWidth,
      y: paddingY + chartHeight - (value / max) * chartHeight
    }
  })
})

const borrowLinePoints = computed(() => {
  if (borrowTrend.value.length === 0) return ''
  const max = Math.max(maxBorrows.value || 1, 1)
  const width = 800
  const height = 150
  const paddingX = 40
  const paddingY = 20
  const chartWidth = width - paddingX * 2
  const chartHeight = height - paddingY * 2
  
  return borrowTrend.value.map((item, index) => {
    const x = paddingX + (index / Math.max(borrowTrend.value.length - 1, 1)) * chartWidth
    const value = item.borrows || 0
    const y = paddingY + chartHeight - (value / max) * chartHeight
    return `${x},${y}`
  }).join(' ')
})

// 计算填充区域坐标点（借阅趋势）
const borrowAreaPoints = computed(() => {
  if (borrowTrend.value.length === 0) return ''
  const max = Math.max(maxBorrows.value || 1, 1)
  const width = 800
  const height = 150
  const paddingX = 40
  const paddingY = 20
  const chartWidth = width - paddingX * 2
  const chartHeight = height - paddingY * 2
  const bottomY = paddingY + chartHeight
  
  const points: string[] = []
  // 起始点（左下角）
  points.push(`${paddingX},${bottomY}`)
  
  // 数据点
  borrowTrend.value.forEach((item, index) => {
    const x = paddingX + (index / Math.max(borrowTrend.value.length - 1, 1)) * chartWidth
    const value = item.borrows || 0
    const y = paddingY + chartHeight - (value / max) * chartHeight
    points.push(`${x},${y}`)
  })
  
  // 结束点（右下角）
  const lastX = paddingX + chartWidth
  points.push(`${lastX},${bottomY}`)
  
  return points.join(' ')
})

const borrowLinePointsArray = computed(() => {
  if (borrowTrend.value.length === 0) return []
  const max = Math.max(maxBorrows.value || 1, 1)
  const width = 800
  const height = 150
  const paddingX = 40
  const paddingY = 20
  const chartWidth = width - paddingX * 2
  const chartHeight = height - paddingY * 2
  
  return borrowTrend.value.map((item, index) => {
    const value = item.borrows || 0
    return {
      x: paddingX + (index / Math.max(borrowTrend.value.length - 1, 1)) * chartWidth,
      y: paddingY + chartHeight - (value / max) * chartHeight
    }
  })
})

// 格式化日期
function formatDate(dateStr: string): string {
  const date = new Date(dateStr + 'T00:00:00')
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  const weekday = weekdays[date.getDay()]
  return `${month}-${day} ${weekday}`
}

function formatDateShort(dateStr: string): string {
  const date = new Date(dateStr + 'T00:00:00')
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${month}/${day}`
}

// 更新最后更新时间
function updateTimeString() {
  const now = new Date()
  const hours = String(now.getHours()).padStart(2, '0')
  const minutes = String(now.getMinutes()).padStart(2, '0')
  const seconds = String(now.getSeconds()).padStart(2, '0')
  updateTime.value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')} ${hours}:${minutes}:${seconds}`
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [summaryRes, trendRes] = await Promise.all([
      http.get('/api/dashboard/summary'),
      http.get('/api/dashboard/trend')
    ])
    summary.value = summaryRes.data
    reservationTrend.value = trendRes.data?.reservations || []
    borrowTrend.value = trendRes.data?.borrows || []
    console.log('看板数据加载成功:', {
      summary: summary.value,
      reservationTrend: reservationTrend.value,
      borrowTrend: borrowTrend.value
    })
    updateTimeString()
  } catch (e: any) {
    console.error('加载看板数据失败:', e)
    error.value = e?.response?.data?.message || '加载数据失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped src="../styles/views/Dashboard.css"></style>
