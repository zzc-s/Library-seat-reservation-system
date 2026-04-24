<template>
  <div class="groups">
    <div class="header">
      <h2>自习小组协同预约{{ isAdmin ? '（管理员视图：查看所有小组）' : '' }}</h2>
      <div class="header-actions">
        <input
          v-model.trim="groupNameQuery"
          class="group-search"
          type="text"
          placeholder="搜索小组名称..."
        />
        <button v-if="!isAdmin" @click="showCreateDialog = true" class="btn-create">创建小组</button>
        <button @click="showNotifications = !showNotifications" class="btn-notifications">
          通知
          <span v-if="unreadCount > 0" class="badge">{{ unreadCount }}</span>
        </button>
      </div>
    </div>

    <!-- 导航筛选（类似借阅管理） -->
    <div class="filter-tabs">
      <button
        v-for="tab in groupTabs"
        :key="tab.key"
        :class="['filter-tab', { active: activeGroupTab === tab.key }]"
        @click="activeGroupTab = tab.key"
      >
        {{ tab.label }}
        <span v-if="tab.count > 0" class="tab-count">{{ tab.count }}</span>
      </button>
    </div>

    <!-- 通知面板 -->
    <div v-if="showNotifications" class="notifications-panel">
      <div class="panel-header">
        <h3>小组通知</h3>
        <button @click="showNotifications = false" class="close-btn">×</button>
      </div>
      <div class="notifications-content">
        <div v-if="notifications.length === 0" class="empty-state">暂无通知</div>
        <div v-else class="notifications-list">
          <div 
            v-for="notif in notifications" 
            :key="notif.id"
            :class="['notification-item', { 'unread': !notif.isRead }]"
            @click="markAsRead(notif.id)"
          >
            <div class="notification-content">
              <div class="notification-title">{{ notif.groupName }}</div>
              <div class="notification-text">{{ notif.content }}</div>
              <div class="notification-time">{{ formatDateTime(notif.createdAt) }}</div>
            </div>
            <span v-if="!notif.isRead" class="unread-dot"></span>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 创建小组对话框 -->
    <div v-if="showCreateDialog" class="dialog">
      <h3>创建自习小组</h3>
      <input v-model="newGroupName" placeholder="小组名称" />
      <p class="hint-text">提示：创建后需要发布小组，并设置预约起始时间，其他用户才能申请加入</p>
      <div class="dialog-actions">
        <button @click="createGroup">创建</button>
        <button @click="showCreateDialog = false">取消</button>
      </div>
    </div>

    <!-- 发布小组对话框 -->
    <div v-if="showPublishDialog" class="dialog">
      <h3>发布小组</h3>
      <p class="hint-text">设置预约起始时间后，其他用户才能看到并申请加入您的小组</p>
      <div class="form-row">
        <label>预约起始时间</label>
        <input 
          type="datetime-local" 
          v-model="publishReservationStartTime" 
          :min="minDateTime"
          class="form-input"
        />
      </div>
      <div class="dialog-actions">
        <button @click="publishGroup">发布</button>
        <button @click="showPublishDialog = false; publishGroupId = null; publishReservationStartTime = ''">取消</button>
      </div>
    </div>

    <!-- 小组详情对话框 -->
    <div v-if="showDetailDialog && currentGroup" class="detail-dialog">
      <div class="detail-header">
        <h3>{{ currentGroup.name }} - 详情</h3>
        <button @click="closeDetailDialog" class="close-btn">×</button>
      </div>
      
      <div class="detail-content">
        <!-- 成员列表 -->
        <div class="section">
          <h4>成员列表（{{ members.length }}人）</h4>
          <div class="members-list">
            <div v-for="member in members" :key="member.userId" class="member-item">
              <span class="member-name">{{ member.username }}</span>
              <span :class="['member-role', member.role === 'LEADER' ? 'leader' : 'member']">
                {{ member.role === 'LEADER' ? '组长' : '成员' }}
              </span>
            </div>
          </div>
          <div v-if="!isCurrentUserMember && currentGroup && !currentGroup.hasPendingRequest" class="join-prompt">
            <p class="prompt-text">您还不是此小组的成员</p>
            <button @click="handleRequestJoinFromDetail" class="btn-join-inline">申请加入</button>
          </div>
          <div v-if="currentGroup && currentGroup.hasPendingRequest" class="request-status">
            <p class="status-text">您的申请正在等待组长审批</p>
          </div>
        </div>

        <!-- 待审批申请（仅组长可见） -->
        <div v-if="isCurrentUserLeader && joinRequests.length > 0" class="section">
          <h4>待审批申请（{{ joinRequests.filter((r: any) => r.status === 'PENDING').length }}条）</h4>
          <div class="requests-list">
            <div v-for="request in joinRequests.filter((r: any) => r.status === 'PENDING')" :key="request.id" class="request-item">
              <div class="request-info">
                <div class="request-username">{{ request.username }}</div>
                <div class="request-time">申请时间：{{ formatDateTime(request.createdAt) }}</div>
              </div>
              <div class="request-actions">
                <button @click="approveRequest(request.id)" class="btn-approve">批准</button>
                <button @click="rejectRequest(request.id)" class="btn-reject">拒绝</button>
              </div>
            </div>
          </div>
        </div>

        <!-- 创建协同预约 -->
        <div class="section">
          <h4>创建协同预约</h4>
          <div class="reservation-form">
            <div class="form-row">
              <label>开始时间</label>
              <input 
                type="datetime-local" 
                v-model="groupStartTime" 
                :min="minDateTime"
                class="form-input"
              />
            </div>
            <div class="form-row">
              <label>结束时间</label>
              <input 
                type="datetime-local" 
                v-model="groupEndTime" 
                :min="groupStartTime || minDateTime"
                class="form-input"
              />
            </div>
            <div class="form-row">
              <label>选择座位（可多选，至少2个）</label>
              <button @click="openSeatSelection" class="btn-select-seats">
                选择座位 (已选{{ selectedSeatIds.length }}个)
              </button>
              <span v-if="selectedSeatIds.length > 0 && selectedSeatIds.length < 2" class="hint-text" style="color: #ef4444; font-size: 12px; margin-top: 4px; display: block;">
                至少需要选择2个座位才能创建协同预约
              </span>
            </div>
            <div v-if="selectedSeatIds.length > 0" class="selected-seats">
              <div v-for="seatId in selectedSeatIds" :key="seatId" class="seat-tag">
                {{ getSeatName(seatId) }}
                <button @click="removeSeat(seatId)" class="remove-btn">×</button>
              </div>
            </div>
            <div class="form-actions">
              <button 
                @click="createGroupReservation" 
                :disabled="!canCreateReservation"
                class="btn-primary"
              >
                创建协同预约
              </button>
            </div>
          </div>
        </div>

        <!-- 预约记录 -->
        <div class="section">
          <h4>预约记录（{{ groupReservations.length }}条）</h4>
          <div v-if="groupReservations.length === 0" class="empty-state">
            暂无预约记录
          </div>
          <div v-else class="reservations-list">
            <div v-for="reservation in groupReservations" :key="reservation.id" class="reservation-item">
              <div class="reservation-info">
                <div>座位：{{ formatSeatIds(reservation.seatIds) }}</div>
                <div>时间：{{ formatDateTime(reservation.startTime) }} - {{ formatDateTime(reservation.endTime) }}</div>
                <div>状态：<span :class="['status-badge', getStatusClass(reservation.status)]">
                  {{ getStatusText(reservation.status) }}
                </span></div>
              </div>
              <div v-if="currentGroup && currentGroup.myRole === 'LEADER'" class="reservation-actions">
                <button
                  v-if="reservation.status === 'PENDING' && canLeaderConfirmReservation(reservation)"
                  @click="confirmReservation(reservation.id)"
                  class="btn-confirm"
                >
                  确认预约
                </button>
                <button v-if="reservation.status === 'CONFIRMED'" @click="checkReservationStatus(reservation.id)" class="btn-check" style="margin-left: 8px; background: #3b82f6; color: white;">检查状态</button>
                <button v-if="reservation.status === 'PENDING' || reservation.status === 'CONFIRMED'" @click="cancelGroupReservation(reservation.id)" class="btn-cancel" style="margin-left: 8px; background: #ef4444; color: white;">取消预约</button>
              </div>
              <div
                v-if="currentGroup && currentGroup.myRole === 'LEADER' && reservation.status === 'PENDING' && !canLeaderConfirmReservation(reservation)"
                class="hint-text"
                style="margin-top: 8px;"
              >
                需先发布小组并满足满员（成员数=座位数）且到达开放时间后，才可确认预约
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 座位选择对话框 -->
    <div v-if="showSeatSelection" class="seat-selection-dialog">
      <div class="dialog-header">
        <h3>选择座位（全屏模式）</h3>
        <button @click="closeSeatSelection" class="close-btn">×</button>
      </div>
      <div class="seat-selection-content">
        <!-- 人数选择和筛选 -->
        <div class="group-size-section">
          <div class="form-row-inline">
            <label>选择人数：</label>
            <select v-model.number="groupSize" @change="onGroupSizeChange" class="size-select">
              <option :value="0">手动选择</option>
              <option :value="2">2人</option>
              <option :value="3">3人</option>
              <option :value="4">4人</option>
              <option :value="5">5人</option>
              <option :value="6">6人</option>
            </select>
            <button v-if="groupSize > 0" @click="recommendAdjacentSeats" class="btn-recommend">
              ⭐ 自动推荐相邻座位
            </button>
            <input v-model="seatSearch" placeholder="搜索座位..." class="search-input-inline" />
          </div>
        </div>
        
        <!-- 可视化座位显示（按楼层分组显示） -->
        <div v-if="seatSelectionLoading" class="loading-state">加载中...</div>
        <div v-else-if="seatsByFloorForSelection.size === 0" class="empty-state">未查询到座位数据</div>
        <div v-else class="seats-visualization-container">
          <!-- 推荐信息（显示在座位网格旁边） -->
          <div v-if="recommendedSeats.length > 0" class="recommendation-sidebar">
            <div class="recommendation-header">
              <span class="recommend-icon">⭐</span>
              <span class="recommend-title">已推荐 {{ recommendedSeats.length }} 个相邻座位</span>
            </div>
            <div class="recommended-seats-list">
              <div 
                v-for="seatId in recommendedSeats" 
                :key="seatId"
                class="recommended-seat-item"
              >
                {{ getSeatName(seatId) }}
              </div>
            </div>
            <button @click="applyRecommendation" class="btn-apply-recommend-sidebar">应用推荐</button>
          </div>
          
          <!-- 按楼层分组显示座位 -->
          <div class="seats-grid-by-floor">
            <div 
              v-for="[floor, floorSeats] in seatsByFloorForSelection" 
              :key="floor"
              class="floor-section"
            >
              <h3 class="floor-title">A楼 {{ floor }}层（{{ floorSeats.length }}个座位）</h3>
              <div class="seat-grid-visual" :style="getGridStyleForSelection(floorSeats)">
                <div
                  v-for="seat in floorSeats"
                  :key="seat.id"
                  class="seat-item-visual"
                  :class="getSeatClassForSelection(seat)"
                  @click="toggleSeatSelection(seat.id)"
                  :title="getSeatTooltipForSelection(seat)"
                >
                  <div class="seat-label-visual">{{ seat.label || seat.seatCode }}</div>
                  <div class="seat-icons-visual">
                    <span v-if="seat.hasPower" class="icon">🔌</span>
                    <span v-if="seat.isWindow" class="icon">🪟</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        
        <div class="selection-info-bar">
          <span>已选择 <strong>{{ selectedSeatIds.length }}</strong> 个座位</span>
          <button v-if="selectedSeatIds.length > 0" @click="clearSeatSelection" class="btn-clear">清空选择</button>
        </div>
        
        <div class="dialog-actions">
          <button @click="closeSeatSelection" class="btn-secondary">取消</button>
          <button @click="confirmSeatSelection" class="btn-primary">确认 ({{ selectedSeatIds.length }})</button>
        </div>
      </div>
    </div>
    
    <div class="groups-list">
      <div v-for="group in tabGroups" :key="group.id" class="group-card" :class="{ 'group-card-disabled': activeGroupTab === 'full' }">
        <div class="card-header-section">
          <h3>{{ group.name }}</h3>
          <div v-if="group.myRole === 'LEADER'" class="group-status-badges">
            <span v-if="!group.isPublished" class="status-badge unpublished">未发布</span>
            <span v-else-if="group.reservationStartTime && new Date(group.reservationStartTime) > new Date()" class="status-badge pending">待开放</span>
            <span v-else class="status-badge published">已发布</span>
          </div>
          <div v-else-if="activeGroupTab === 'full'" class="group-status-badges">
            <span class="status-badge full">已满</span>
          </div>
        </div>
        <p>成员数：{{ group.memberCount }}</p>
        <p v-if="group.seatCapacity" class="capacity-line">
          名额：{{ group.memberCount }}/{{ group.seatCapacity }}
        </p>
        <p v-if="group.leaderName">组长：{{ group.leaderName }}</p>
        <p v-if="group.reservationStartTime">开放时间：{{ formatDateTime(group.reservationStartTime) }}</p>
        <p v-if="group.myRole">我的角色：{{ group.myRole === 'LEADER' ? '组长' : '成员' }}</p>
        <p v-else-if="group.hasPendingRequest" class="pending-request">申请待审批</p>
        <p v-else class="not-member">未参与此小组</p>
        <div class="card-actions">
          <button @click="viewGroup(group.id)" class="btn-view">查看详情</button>
          <button
            v-if="activeGroupTab !== 'full' && !group.myRole && !group.hasPendingRequest"
            @click="requestJoinGroup(group.id)"
            class="btn-join"
          >
            申请加入
          </button>
          <button
            v-if="activeGroupTab === 'full' && !group.myRole"
            type="button"
            class="btn-join btn-join-disabled"
            disabled
          >
            成员已满
          </button>
          <button 
            v-if="group.myRole === 'LEADER' && !group.isPublished" 
            @click="showPublishDialogFunc(group.id)" 
            class="btn-publish"
          >
            发布小组
          </button>
          <button 
            v-if="group.myRole === 'LEADER'" 
            @click="deleteGroup(group.id)" 
            class="btn-delete"
          >
            删除
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import http from '../lib/http'
import { useAuthStore } from '../stores/auth'
import { useRealtimeStore } from '../stores/realtime'

const auth = useAuthStore()
const realtime = useRealtimeStore()
const isAdmin = computed(() => auth.isAdmin)
const groups = ref<any[]>([])
const showCreateDialog = ref(false)
const newGroupName = ref('')
const groupNameQuery = ref('')
const activeGroupTab = ref<'mine' | 'others' | 'joinable' | 'full'>('joinable')

const showDetailDialog = ref(false)
const currentGroup = ref<any>(null)
const members = ref<any[]>([])
const groupReservations = ref<any[]>([])
const joinRequests = ref<any[]>([])
const groupStartTime = ref('')
const groupEndTime = ref('')
const selectedSeatIds = ref<number[]>([])
const showSeatSelection = ref(false)
const availableSeats = ref<any[]>([])
const seatSearch = ref('')
const groupSize = ref(0) // 选择的组人数，0表示手动选择
const recommendedSeats = ref<number[]>([]) // 推荐的座位ID列表
const seatsByAreaForSelection = ref<Map<string, any[]>>(new Map())
const seatsByFloorForSelection = ref<Map<number, any[]>>(new Map()) // 按楼层分组的座位
const allSeatsForSelection = ref<any[]>([]) // 所有座位（不按区域分组）
const seatSelectionLoading = ref(false)
const seatSelectionDate = ref('')
const seatSelectionStartTime = ref('')
const seatSelectionEndTime = ref('')
const showNotifications = ref(false)
const notifications = ref<any[]>([])
const unreadCount = ref(0)
const showPublishDialog = ref(false)
const publishGroupId = ref<number | null>(null)
const publishReservationStartTime = ref('')
let offSeatMsg: null | (() => void) = null

const visibleGroups = computed(() => {
  const q = groupNameQuery.value.trim().toLowerCase()
  if (!q) return groups.value
  return groups.value.filter((g: any) => (g?.name || '').toLowerCase().includes(q))
})

const mineGroups = computed(() => {
  if (isAdmin.value) return []
  const myUid = auth.currentUid
  if (!myUid) return []
  return visibleGroups.value.filter((g: any) => g.leaderId === myUid)
})

const othersGroups = computed(() => {
  if (isAdmin.value) return visibleGroups.value
  const myUid = auth.currentUid
  if (!myUid) return visibleGroups.value
  return visibleGroups.value.filter((g: any) => g.leaderId !== myUid)
})

const joinableGroups = computed(() => {
  if (isAdmin.value) return visibleGroups.value
  return visibleGroups.value.filter((g: any) => {
    // 自己已经在组里/已申请待审批：仍展示在“可申请加入”区（因为按钮不会出现）
    if (g.myRole || g.hasPendingRequest) return true
    // 未满或未计算容量（尚无已确认协同预约）：允许申请
    return g.isFull !== true
  })
})

const fullGroups = computed(() => {
  if (isAdmin.value) return []
  return visibleGroups.value.filter((g: any) => !g.myRole && !g.hasPendingRequest && g.isFull === true)
})

const groupTabs = computed(() => {
  return [
    { key: 'mine', label: '我创建的', count: mineGroups.value.length },
    { key: 'others', label: '他人创建的', count: othersGroups.value.length },
    { key: 'joinable', label: '可申请加入', count: joinableGroups.value.length },
    { key: 'full', label: '已满', count: fullGroups.value.length }
  ] as const
})

const tabGroups = computed(() => {
  switch (activeGroupTab.value) {
    case 'mine':
      return mineGroups.value
    case 'others':
      return othersGroups.value
    case 'full':
      return fullGroups.value
    case 'joinable':
    default:
      return joinableGroups.value
  }
})
// 判断当前用户是否是小组成员
const isCurrentUserMember = computed(() => {
  if (!currentGroup.value) return false
  // 通过groups列表中的myRole来判断当前用户是否是小组成员
  return currentGroup.value.myRole !== null && currentGroup.value.myRole !== undefined
})

// 判断当前用户是否是组长
const isCurrentUserLeader = computed(() => {
  if (!currentGroup.value) return false
  return currentGroup.value.myRole === 'LEADER'
})

// 计算最小日期时间
const minDateTime = computed(() => {
  const now = new Date()
  const minutes = now.getMinutes()
  const roundedMinutes = Math.ceil(minutes / 15) * 15
  const roundedDate = new Date(now)
  if (roundedMinutes >= 60) {
    roundedDate.setHours(now.getHours() + 1, 0, 0, 0)
  } else {
    roundedDate.setMinutes(roundedMinutes, 0, 0)
  }
  const year = roundedDate.getFullYear()
  const month = String(roundedDate.getMonth() + 1).padStart(2, '0')
  const day = String(roundedDate.getDate()).padStart(2, '0')
  const hours = String(roundedDate.getHours()).padStart(2, '0')
  const minutesStr = String(roundedDate.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hours}:${minutesStr}`
})

const canCreateReservation = computed(() => {
  return groupStartTime.value && 
         groupEndTime.value && 
         selectedSeatIds.value.length >= 2 &&
         new Date(groupStartTime.value) < new Date(groupEndTime.value)
})

function canLeaderConfirmReservation(reservation: any) {
  if (!currentGroup.value) return false
  if (currentGroup.value.myRole !== 'LEADER') return false
  if (!currentGroup.value.isPublished) return false
  if (!currentGroup.value.reservationStartTime) return false
  if (new Date(currentGroup.value.reservationStartTime).getTime() > Date.now()) return false
  // 按当前这条协同预约计算“满员”，避免依赖 groups 列表里的 isFull（其值可能基于已确认记录，导致待确认按钮不显示）
  const seatCount = String(reservation?.seatIds || '')
    .split(',')
    .map((s: string) => s.trim())
    .filter((s: string) => s.length > 0).length
  if (seatCount <= 0) return false
  return members.value.length === seatCount
}

async function loadGroups() {
  try {
    const res = await http.get('/api/groups')
    groups.value = res.data
  } catch (e) {
    console.error('加载小组失败', e)
  }
}

async function refreshCurrentGroupData() {
  if (!showDetailDialog.value || !currentGroup.value) return
  try {
    const id = currentGroup.value.id
    const [groupsRes, membersRes, reservationsRes] = await Promise.all([
      http.get('/api/groups'),
      http.get(`/api/groups/${id}/members`),
      http.get(`/api/groups/${id}/reservations`)
    ])
    groups.value = groupsRes.data || []
    const latestGroup = groups.value.find((g: any) => g.id === id)
    if (latestGroup) currentGroup.value = latestGroup
    members.value = membersRes.data || []
    groupReservations.value = reservationsRes.data || []
    if (currentGroup.value?.myRole === 'LEADER') {
      await loadJoinRequests(id)
    }
  } catch (e) {
    console.error('实时刷新小组详情失败', e)
  }
}

async function createGroup() {
  try {
    await http.post('/api/groups', { name: newGroupName.value })
    showCreateDialog.value = false
    newGroupName.value = ''
    await loadGroups()
    alert('小组创建成功！请在详情中发布小组并设置预约起始时间，其他用户才能申请加入。')
  } catch (e: any) {
    // http 响应拦截器多数情况下会把后端 message 包装成 Error(message)
    alert(e?.response?.data?.message || e?.message || '创建失败')
  }
}

function showPublishDialogFunc(groupId: number) {
  publishGroupId.value = groupId
  showPublishDialog.value = true
}

async function publishGroup() {
  if (!publishGroupId.value || !publishReservationStartTime.value) {
    alert('请设置预约起始时间')
    return
  }
  
  try {
    await http.post(`/api/groups/${publishGroupId.value}/publish`, {
      reservationStartTime: formatLocalDateTime(publishReservationStartTime.value)
    })
    showPublishDialog.value = false
    publishGroupId.value = null
    publishReservationStartTime.value = ''
    await loadGroups()
    alert('小组已发布！')
  } catch (e: any) {
    // 从错误对象中提取消息（响应拦截器已将错误转换为 Error 对象）
    const errorMessage = e?.message || e?.response?.data?.message || '发布失败'
    alert(errorMessage)
  }
}

async function deleteGroup(groupId: number) {
  if (!confirm('确定要删除这个小组吗？删除后将无法恢复，所有相关的申请和预约也会被删除。')) {
    return
  }
  
  try {
    await http.delete(`/api/groups/${groupId}`)
    await loadGroups()
    alert('小组已删除')
  } catch (e: any) {
    alert(e.response?.data?.message || '删除失败')
  }
}

async function loadNotifications() {
  try {
    const res = await http.get('/api/groups/notifications')
    notifications.value = res.data
    // 计算未读数量
    unreadCount.value = notifications.value.filter((n: any) => !n.isRead).length
  } catch (e: any) {
    console.error('加载通知失败', e)
    notifications.value = []
    unreadCount.value = 0
  }
}

async function loadUnreadCount() {
  try {
    const res = await http.get('/api/groups/notifications/unread-count')
    unreadCount.value = res.data?.count || 0
  } catch (e: any) {
    console.error('加载未读数量失败', e)
    // 如果加载失败，设置为0，不影响其他功能
    unreadCount.value = 0
  }
}

async function markAllAsRead() {
  // 先本地立即消红点（用户体验），再异步落库
  const hasUnread = notifications.value.some((n: any) => !n.isRead)
  if (!hasUnread) return
  notifications.value.forEach((n: any) => {
    n.isRead = true
  })
  unreadCount.value = 0
  try {
    await http.post('/api/groups/notifications/read-all')
  } catch (e: any) {
    // 回滚体验没必要；下次拉取会自动同步
    console.error('全部标记已读失败', e)
  }
}

async function markAsRead(notificationId: number) {
  try {
    await http.post(`/api/groups/notifications/${notificationId}/read`)
    await loadNotifications()
  } catch (e: any) {
    console.error('标记已读失败', e)
  }
}

async function viewGroup(id: number) {
  try {
    const group = groups.value.find(g => g.id === id)
    if (!group) return
    
    currentGroup.value = group
    showDetailDialog.value = true
    
    // 加载成员列表
    const membersRes = await http.get(`/api/groups/${id}/members`)
    members.value = membersRes.data
    
    // 加载预约记录
    const reservationsRes = await http.get(`/api/groups/${id}/reservations`)
    groupReservations.value = reservationsRes.data
    
    // 如果是组长，加载加入申请列表
    if (group.myRole === 'LEADER') {
      await loadJoinRequests(id)
    } else {
      joinRequests.value = []
    }
    
    // 加载可用座位
    await loadAvailableSeats()
  } catch (e: any) {
    console.error('加载小组详情失败', e)
    alert(e.response?.data?.message || '加载失败')
  }
}

async function requestJoinGroup(groupId: number) {
  if (!confirm('确定要申请加入这个自习小组吗？')) {
    return
  }
  
  try {
    await http.post(`/api/groups/${groupId}/join`)
    alert('申请已提交，等待组长审批')
    // 刷新小组列表
    await loadGroups()
    // 如果当前正在查看该小组的详情，重新加载数据
    if (currentGroup.value && currentGroup.value.id === groupId) {
      const group = groups.value.find(g => g.id === groupId)
      if (group) {
        currentGroup.value = group
      }
    }
  } catch (e: any) {
    console.error('申请加入小组失败', e)
    const fromBody = (e?.response?.data as { message?: string } | undefined)?.message
    const msg = (fromBody && String(fromBody)) || (e instanceof Error ? e.message : '') || '申请失败'
    alert(msg)
  }
}

async function handleRequestJoinFromDetail() {
  if (!currentGroup.value) return
  await requestJoinGroup(currentGroup.value.id)
}

async function loadJoinRequests(groupId: number) {
  try {
    const res = await http.get(`/api/groups/${groupId}/join-requests`)
    joinRequests.value = res.data
  } catch (e: any) {
    // 如果不是组长，会返回403，这是正常的
    if (e.response?.status !== 403) {
      console.error('加载申请列表失败', e)
    }
    joinRequests.value = []
  }
}

async function approveRequest(requestId: number) {
  if (!currentGroup.value) return
  if (!confirm('确定要批准这个申请吗？')) {
    return
  }
  
  try {
    await http.post(`/api/groups/${currentGroup.value.id}/join-requests/${requestId}/approve`)
    alert('申请已批准')
    // 重新加载申请列表和成员列表
    await loadJoinRequests(currentGroup.value.id)
    await viewGroup(currentGroup.value.id)
  } catch (e: any) {
    console.error('批准申请失败', e)
    alert(e.response?.data?.message || '批准失败')
  }
}

async function rejectRequest(requestId: number) {
  if (!currentGroup.value) return
  if (!confirm('确定要拒绝这个申请吗？')) {
    return
  }
  
  try {
    await http.post(`/api/groups/${currentGroup.value.id}/join-requests/${requestId}/reject`)
    alert('申请已拒绝')
    // 重新加载申请列表
    await loadJoinRequests(currentGroup.value.id)
  } catch (e: any) {
    console.error('拒绝申请失败', e)
    alert(e.response?.data?.message || '拒绝失败')
  }
}

// 打开座位选择对话框时加载座位
async function openSeatSelection() {
  showSeatSelection.value = true
  groupSize.value = 0
  recommendedSeats.value = []
  seatSelectionLoading.value = true // 立即显示加载状态
  
  // 从预约时间中提取日期和时间段
  if (groupStartTime.value && groupEndTime.value) {
    try {
      const startDate = new Date(groupStartTime.value)
      const endDate = new Date(groupEndTime.value)
      
      // 检查日期是否有效
      if (isNaN(startDate.getTime()) || isNaN(endDate.getTime())) {
        throw new Error('预约时间格式无效')
      }
      
      seatSelectionDate.value = startDate.toISOString().split('T')[0]
      seatSelectionStartTime.value = startDate.toTimeString().slice(0, 5)
      seatSelectionEndTime.value = endDate.toTimeString().slice(0, 5)
      
      console.log('从预约时间提取:', {
        date: seatSelectionDate.value,
        start: seatSelectionStartTime.value,
        end: seatSelectionEndTime.value
      })
    } catch (e: any) {
      console.error('解析预约时间失败:', e)
      // 如果解析失败，使用默认值
      const now = new Date()
      seatSelectionDate.value = now.toISOString().split('T')[0]
      seatSelectionStartTime.value = '08:00'
      seatSelectionEndTime.value = '12:00'
    }
  } else {
    // 如果没有预约时间，使用默认值（今天，8:00-12:00）
    const now = new Date()
    seatSelectionDate.value = now.toISOString().split('T')[0]
    seatSelectionStartTime.value = '08:00'
    seatSelectionEndTime.value = '12:00'
    console.log('使用默认时间:', {
      date: seatSelectionDate.value,
      start: seatSelectionStartTime.value,
      end: seatSelectionEndTime.value
    })
  }
  
  // 确保参数都已设置后再加载
  if (seatSelectionDate.value && seatSelectionStartTime.value && seatSelectionEndTime.value) {
    await loadSeatsForSelection()
  } else {
    console.error('座位查询参数未正确设置')
    seatSelectionLoading.value = false
    alert('无法加载座位：时间参数未设置')
  }
}

// 关闭座位选择对话框
function closeSeatSelection() {
  showSeatSelection.value = false
  groupSize.value = 0
  recommendedSeats.value = []
  seatSearch.value = ''
}

// 加载座位数据（使用可视化查询API，类似SeatsMap.vue）
async function loadSeatsForSelection() {
  // 检查参数完整性
  if (!seatSelectionDate.value || !seatSelectionStartTime.value || !seatSelectionEndTime.value) {
    console.warn('座位查询参数不完整:', { 
      date: seatSelectionDate.value, 
      start: seatSelectionStartTime.value, 
      end: seatSelectionEndTime.value 
    })
    seatSelectionLoading.value = false
    allSeatsForSelection.value = []
    return
  }
  
  seatSelectionLoading.value = true
  try {
    const timeSlot = `${seatSelectionStartTime.value}-${seatSelectionEndTime.value}`
    const params: any = {
      reserveDate: seatSelectionDate.value,
      timeSlot: timeSlot
    }
    
    console.log('正在查询座位，参数:', params)
    const res = await http.get('/api/seat/query', { params })
    console.log('座位查询响应:', res)
    const data = res.data || {}
    const allSeatsList = data.seats || []
    
    console.log('获取到座位数量:', allSeatsList.length)
    
    if (!allSeatsList || allSeatsList.length === 0) {
      console.warn('未查询到座位数据')
      allSeatsForSelection.value = []
      seatsByAreaForSelection.value = new Map()
      seatsByFloorForSelection.value = new Map()
      availableSeats.value = []
      seatSelectionLoading.value = false
      return
    }
    
    // 应用搜索过滤
    let filteredSeats = allSeatsList
    if (seatSearch.value) {
      const search = seatSearch.value.toLowerCase()
      filteredSeats = filteredSeats.filter((s: any) => 
        (s.seatCode || s.label || '').toLowerCase().includes(search) ||
        (s.building || '').toLowerCase().includes(search)
      )
    }
    
    // 按区域分组
    const grouped = new Map<string, any[]>()
    filteredSeats.forEach((seat: any) => {
      let area = seat.area || seat.zone
      if (!area) {
        if (seat.building && seat.floor != null && seat.zone) {
          area = `${seat.building}${seat.floor}层${seat.zone}`
        } else if (seat.building && seat.floor != null) {
          area = `${seat.building}${seat.floor}层`
        } else if (seat.zone) {
          area = seat.zone
        } else {
          area = '未分类区域'
        }
      }
      
      const normalizedArea = (area || '未分类区域').trim()
      if (!grouped.has(normalizedArea)) {
        grouped.set(normalizedArea, [])
      }
      grouped.get(normalizedArea)!.push(seat)
    })
    
    // 对每个区域的座位进行排序（优先使用 row_num/col_num）
    grouped.forEach((seats) => {
      seats.sort((a, b) => {
        // 优先使用 row_num/col_num，如果没有则使用 row/col，最后使用 x/y
        const rowA = a.rowNum || a.row || a.y || 0
        const rowB = b.rowNum || b.row || b.y || 0
        const colA = a.colNum || a.col || a.x || 0
        const colB = b.colNum || b.col || b.x || 0
        if (rowA !== rowB) return rowA - rowB
        return colA - colB
      })
    })
    
    seatsByAreaForSelection.value = grouped
    availableSeats.value = filteredSeats
    
    // 按楼层分组座位
    const floorGrouped = new Map<number, any[]>()
    filteredSeats.forEach((seat: any) => {
      const floor = seat.floor || 1
      if (!floorGrouped.has(floor)) {
        floorGrouped.set(floor, [])
      }
      floorGrouped.get(floor)!.push(seat)
    })
    
    // 对每个楼层的座位进行排序（优先使用 row_num/col_num）
    floorGrouped.forEach((seats) => {
      seats.sort((a, b) => {
        const rowA = a.rowNum || a.row || a.y || 0
        const rowB = b.rowNum || b.row || b.y || 0
        const colA = a.colNum || a.col || a.x || 0
        const colB = b.colNum || b.col || b.x || 0
        if (rowA !== rowB) return rowA - rowB
        return colA - colB
      })
    })
    
    seatsByFloorForSelection.value = floorGrouped
    
    // 合并所有座位到一个数组中（用于推荐算法）
    allSeatsForSelection.value = filteredSeats.sort((a, b) => {
      // 优先使用 row_num/col_num，如果没有则使用 row/col，最后使用 x/y
      const rowA = a.rowNum || a.row || a.y || 0
      const rowB = b.rowNum || b.row || b.y || 0
      const colA = a.colNum || a.col || a.x || 0
      const colB = b.colNum || b.col || b.x || 0
      if (rowA !== rowB) return rowA - rowB
      return colA - colB
    })
    
    // 如果已选择人数，自动推荐相邻座位（按楼层分组）
    if (groupSize.value > 0 && seatsByFloorForSelection.value.size > 0) {
      recommendAdjacentSeats()
    }
  } catch (e: any) {
    console.error('加载座位失败', e)
    console.error('错误详情:', e.response?.data || e.message)
    alert(`加载座位失败: ${e.response?.data?.message || e.message || '未知错误'}`)
    seatsByAreaForSelection.value = new Map()
    seatsByFloorForSelection.value = new Map()
    availableSeats.value = []
    allSeatsForSelection.value = []
  } finally {
    seatSelectionLoading.value = false
  }
}


// 旧函数保留（兼容性）
async function loadAvailableSeats() {
  // 如果座位选择对话框已打开，使用新的加载函数
  if (showSeatSelection.value) {
    await loadSeatsForSelection()
  } else {
    try {
      const res = await http.get('/api/seats')
      availableSeats.value = res.data.filter((s: any) => 
        s.status === 'FREE' || s.status === 'IDLE'
      )
    } catch (e) {
      console.error('加载座位失败', e)
    }
  }
}

function closeDetailDialog() {
  showDetailDialog.value = false
  currentGroup.value = null
  members.value = []
  groupReservations.value = []
  groupStartTime.value = ''
  groupEndTime.value = ''
  selectedSeatIds.value = []
}

function toggleSeatSelection(seatId: number) {
  // 查找座位对象
  let seat: any = null
  for (const areaSeats of seatsByAreaForSelection.value.values()) {
    seat = areaSeats.find((s: any) => s.id === seatId)
    if (seat) break
  }
  
  // 检查座位是否可用
  if (seat) {
    const status = seat.status
    const statusNum = typeof status === 'number' ? status : 
      (status === 'FREE' || status === 'IDLE' ? 0 : 1)
    if (statusNum !== 0) {
      // 座位已被占用或已预约，不能选择
      return
    }
  }
  
  const index = selectedSeatIds.value.indexOf(seatId)
  if (index > -1) {
    selectedSeatIds.value.splice(index, 1)
  } else {
    selectedSeatIds.value.push(seatId)
  }
}

function removeSeat(seatId: number) {
  const index = selectedSeatIds.value.indexOf(seatId)
  if (index > -1) {
    selectedSeatIds.value.splice(index, 1)
  }
}

function confirmSeatSelection() {
  if (selectedSeatIds.value.length < 2) {
    alert('至少需要选择2个座位才能创建协同预约')
    return
  }
  closeSeatSelection()
}

// 清空座位选择
function clearSeatSelection() {
  selectedSeatIds.value = []
  recommendedSeats.value = []
}

// 获取座位样式（用于网格布局）
function getGridStyleForSelection(seats: any[]) {
  if (seats.length === 0) return {}
  const maxCol = Math.max(...seats.map(s => s.col || s.colNum || s.x || 0), 0)
  if (maxCol > 0) {
    return { gridTemplateColumns: `repeat(${maxCol + 1}, 1fr)` }
  }
  // 如果没有列信息，使用自动布局
  return {}
}

// 获取座位类名（根据状态）
function getSeatClassForSelection(seat: any) {
  const classes: string[] = []
  
  // 状态类
  const status = seat.status
  const statusNum = typeof status === 'number' ? status : 
    (status === 'FREE' || status === 'IDLE' ? 0 :
     status === 'RESERVED' ? 1 :
     status === 'OCCUPIED' ? 2 :
     (status === 'BROKEN' || status === 'FAULT') ? 3 : 0)
  
  if (statusNum === 0) {
    classes.push('available')
  } else if (statusNum === 1) {
    classes.push('reserved')
  } else if (statusNum === 2) {
    classes.push('occupied')
  } else if (statusNum === 3) {
    classes.push('broken')
  }
  
  // 选中状态
  if (selectedSeatIds.value.includes(seat.id)) {
    classes.push('selected')
  }
  
  // 推荐状态
  if (recommendedSeats.value.includes(seat.id)) {
    classes.push('recommended')
  }
  
  // 不可选择状态（已占用或已预约）
  if (statusNum !== 0) {
    classes.push('disabled')
  }
  
  return classes.join(' ')
}

// 获取座位提示信息
function getSeatTooltipForSelection(seat: any) {
  let tooltip = `${seat.seatCode || seat.label}\n状态：${getStatusTextForSelection(seat.status)}`
  if (seat.hasPower) tooltip += '\n有电源'
  if (seat.isWindow) tooltip += '\n靠窗'
  return tooltip
}

// 获取状态文本
function getStatusTextForSelection(status: any) {
  if (typeof status === 'number') {
    const numStatusMap: Record<number, string> = {
      0: '空闲',
      1: '已预约',
      2: '使用中',
      3: '维修中'
    }
    return numStatusMap[status] || '未知'
  }
  
  const statusMap: Record<string, string> = {
    'FREE': '空闲',
    'IDLE': '空闲',
    'RESERVED': '已预约',
    'OCCUPIED': '使用中',
    'BROKEN': '故障',
    'FAULT': '维修'
  }
  return statusMap[status] || status || '未知'
}

// 人数选择改变
function onGroupSizeChange() {
  recommendedSeats.value = []
  // 只有当座位数据已加载时才自动推荐
  if (groupSize.value > 0 && seatsByFloorForSelection.value.size > 0) {
    // 自动推荐
    recommendAdjacentSeats()
  }
}

// 推荐相邻座位（基于 row_num/col_num 坐标，按楼层分组推荐）
function recommendAdjacentSeats() {
  if (groupSize.value <= 0 || groupSize.value > 6) {
    recommendedSeats.value = []
    return
  }
  
  recommendedSeats.value = []
  
  // 按楼层分组，在每个楼层内寻找相邻座位
  seatsByFloorForSelection.value.forEach((floorSeats, floor) => {
    if (recommendedSeats.value.length > 0) return // 已找到推荐，直接返回
    
    // 过滤出可用座位（只考虑当前楼层）
    const freeSeats = floorSeats.filter((s: any) => {
      const status = s.status
      const statusNum = typeof status === 'number' ? status : 
        (status === 'FREE' || status === 'IDLE' ? 0 : 1)
      return statusNum === 0
    })
    
    if (freeSeats.length < groupSize.value) return
    
    // 使用 row_num/col_num 坐标进行排序和查找
    const sortedSeats = [...freeSeats].sort((a, b) => {
      // 优先使用 row_num/col_num，如果没有则使用 row/col，最后使用 x/y
      const rowA = a.rowNum || a.row || a.y || 0
      const rowB = b.rowNum || b.row || b.y || 0
      const colA = a.colNum || a.col || a.x || 0
      const colB = b.colNum || b.col || b.x || 0
      if (rowA !== rowB) return rowA - rowB
      return colA - colB
    })
    
    // 检查是否有有效的坐标数据
    const hasValidCoords = sortedSeats.some(s => {
      const row = s.rowNum || s.row || s.y || 0
      const col = s.colNum || s.col || s.x || 0
      return row > 0 && col > 0
    })
    
    if (!hasValidCoords) {
      // 如果没有有效坐标，使用标签排序作为备选
      const seatsByLabel = [...freeSeats].sort((a, b) => {
        const labelA = (a.label || a.seatCode || '').toLowerCase()
        const labelB = (b.label || b.seatCode || '').toLowerCase()
        return labelA.localeCompare(labelB)
      })
      
      if (seatsByLabel.length >= groupSize.value) {
        recommendedSeats.value = seatsByLabel.slice(0, groupSize.value).map((s: any) => s.id)
        return
      }
      return
    }
    
    // 策略1：寻找同一行的完全连续座位（列号连续）
    for (let i = 0; i <= sortedSeats.length - groupSize.value; i++) {
      const candidateSeats = sortedSeats.slice(i, i + groupSize.value)
      const firstSeat = candidateSeats[0]
      
      // 检查是否在同一行
      const firstRow = firstSeat.rowNum || firstSeat.row || firstSeat.y || 0
      const allSameRow = candidateSeats.every(s => {
        const row = s.rowNum || s.row || s.y || 0
        return row === firstRow && row > 0
      })
      
      if (allSameRow) {
        // 检查列是否完全连续
        let isAdjacent = true
        for (let j = 1; j < candidateSeats.length; j++) {
          const prevCol = candidateSeats[j - 1].colNum || candidateSeats[j - 1].col || candidateSeats[j - 1].x || 0
          const currCol = candidateSeats[j].colNum || candidateSeats[j].col || candidateSeats[j].x || 0
          if (currCol !== prevCol + 1) {
            isAdjacent = false
            break
          }
        }
        
        if (isAdjacent) {
          recommendedSeats.value = candidateSeats.map((s: any) => s.id)
          return
        }
      }
    }
    
    // 策略2：寻找同一行的接近座位（列号间隔不超过1）
    for (let i = 0; i <= sortedSeats.length - groupSize.value; i++) {
      const candidateSeats = sortedSeats.slice(i, i + groupSize.value)
      const firstSeat = candidateSeats[0]
      
      const firstRow = firstSeat.rowNum || firstSeat.row || firstSeat.y || 0
      const allSameRow = candidateSeats.every(s => {
        const row = s.rowNum || s.row || s.y || 0
        return row === firstRow && row > 0
      })
      
      if (allSameRow) {
        // 检查列是否接近（间隔不超过1）
        let isAdjacent = true
        for (let j = 1; j < candidateSeats.length; j++) {
          const prevCol = candidateSeats[j - 1].colNum || candidateSeats[j - 1].col || candidateSeats[j - 1].x || 0
          const currCol = candidateSeats[j].colNum || candidateSeats[j].col || candidateSeats[j].x || 0
          if (currCol - prevCol > 2) { // 间隔超过1列（即 gap > 2）
            isAdjacent = false
            break
          }
        }
        
        if (isAdjacent) {
          recommendedSeats.value = candidateSeats.map((s: any) => s.id)
          return
        }
      }
    }
    
    // 策略3：如果找不到相邻座位，至少选择当前楼层的前N个可用座位
    if (recommendedSeats.value.length === 0 && freeSeats.length >= groupSize.value) {
      recommendedSeats.value = sortedSeats.slice(0, groupSize.value).map((s: any) => s.id)
      return
    }
  })
  
  if (recommendedSeats.value.length === 0) {
    alert('未找到合适的相邻座位组合，请手动选择')
  }
}

// 应用推荐
function applyRecommendation() {
  if (recommendedSeats.value.length > 0) {
    selectedSeatIds.value = [...recommendedSeats.value]
  }
}

function getSeatName(seatId: number): string {
  // 先从 availableSeats 中查找
  let seat = availableSeats.value.find(s => s.id === seatId)
  // 如果找不到，从 seatsByAreaForSelection 中查找
  if (!seat) {
    for (const areaSeats of seatsByAreaForSelection.value.values()) {
      seat = areaSeats.find((s: any) => s.id === seatId)
      if (seat) break
    }
  }
  return seat ? `${seat.building}-${seat.floor}-${seat.label || seat.seatCode}` : `座位${seatId}`
}

function formatSeatIds(seatIdsStr: string): string {
  const ids = seatIdsStr.split(',').map(id => parseInt(id.trim()))
  return ids.map(id => getSeatName(id)).join(', ')
}

function formatDateTime(dateStr: string): string {
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

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

async function createGroupReservation() {
  if (!currentGroup.value) return
  
  // 验证至少需要2个座位
  if (selectedSeatIds.value.length < 2) {
    alert('协同预约至少需要选择2个座位')
    return
  }
  
  try {
    const requestData = {
      seatIds: selectedSeatIds.value,
      startTime: formatLocalDateTime(groupStartTime.value),
      endTime: formatLocalDateTime(groupEndTime.value)
    }
    
    await http.post(`/api/groups/${currentGroup.value.id}/reservations`, requestData)
    
    // 重新加载预约记录
    const reservationsRes = await http.get(`/api/groups/${currentGroup.value.id}/reservations`)
    groupReservations.value = reservationsRes.data
    
    // 清空表单
    groupStartTime.value = ''
    groupEndTime.value = ''
    selectedSeatIds.value = []
    
    alert('协同预约创建成功！')
  } catch (e: any) {
    console.error('创建协同预约失败', e)
    console.error('错误响应状态:', e.response?.status)
    console.error('错误响应数据:', JSON.stringify(e.response?.data, null, 2))
    
    // 处理冲突错误，显示详细信息
    const errorData = e.response?.data
    if (!errorData) {
      // 如果errorData不存在，可能是网络错误或其他问题
      const errorMessage = e.message || '创建失败：未知错误'
      alert(errorMessage)
      return
    }
    
    // 检查是否有冲突详情（这是最重要的信息）
    if (errorData.conflictDetails && Array.isArray(errorData.conflictDetails) && errorData.conflictDetails.length > 0) {
      // 构建详细的错误信息，将座位ID替换为座位名称
      let errorMessage = '创建失败：所选座位和时间与已有预约冲突\n\n'
      errorMessage += '冲突详情：\n'
      errorData.conflictDetails.forEach((detail: string, index: number) => {
        // 将"座位 X"替换为实际的座位名称
        let formattedDetail = detail
        if (errorData.conflictedSeats && Array.isArray(errorData.conflictedSeats)) {
          errorData.conflictedSeats.forEach((seatId: number) => {
            const seatName = getSeatName(seatId)
            // 替换"座位 X"为实际座位名称，支持多种格式
            formattedDetail = formattedDetail.replace(
              new RegExp(`座位\\s*${seatId}\\b`, 'g'),
              seatName
            )
          })
        }
        errorMessage += `${index + 1}. ${formattedDetail}\n`
      })
      errorMessage += '\n请选择其他座位或时间。'
      alert(errorMessage)
      return
    }
    
    // 如果有冲突座位但没有详细信息，至少显示座位名称
    if (errorData.conflictedSeats && Array.isArray(errorData.conflictedSeats) && errorData.conflictedSeats.length > 0) {
      const conflictedSeatNames = errorData.conflictedSeats.map((seatId: number) => getSeatName(seatId))
      let errorMessage = '创建失败：以下座位在该时段已被预约\n\n'
      errorMessage += '冲突座位：\n'
      conflictedSeatNames.forEach((name: string, index: number) => {
        errorMessage += `${index + 1}. ${name}\n`
      })
      errorMessage += '\n请选择其他座位或时间。'
      alert(errorMessage)
      return
    }
    
    // 其他错误，显示通用错误信息
    const message = errorData.message || '创建失败'
    alert(message)
  }
}

async function confirmReservation(reservationId: number) {
  if (!currentGroup.value) return
  
  if (!confirm('确认此协同预约？确认后将为所有成员创建个人预约。')) {
    return
  }
  
  try {
    const res = await http.post(`/api/groups/${currentGroup.value.id}/reservations/${reservationId}/confirm`)
    
    // 重新加载预约记录
    const reservationsRes = await http.get(`/api/groups/${currentGroup.value.id}/reservations`)
    groupReservations.value = reservationsRes.data
    
    const data = res.data || {}
    const createdCount = data.createdCount || 0
    const totalMembers = data.totalMembers || 0
    
    if (createdCount < totalMembers) {
      alert('预约已确认')
    } else {
      alert('预约已确认')
    }
  } catch (e: any) {
    console.error('确认预约失败', e)
    const fromBody = (e?.response?.data as { message?: string } | undefined)?.message
    const msg = (fromBody && String(fromBody)) || (e instanceof Error ? e.message : '') || '确认失败'
    alert(msg)
  }
}

async function checkReservationStatus(reservationId: number) {
  if (!currentGroup.value) return
  
  try {
    const res = await http.get(`/api/groups/${currentGroup.value.id}/reservations/${reservationId}/check`)
    const data = res.data || {}
    const memberStatus = data.memberStatus || []
    
    let message = `预约状态检查：\n\n`
    message += `协同预约状态：${data.groupReservationStatus}\n`
    message += `成员总数：${data.memberCount}\n\n`
    message += `成员预约创建情况：\n`
    
    memberStatus.forEach((status: any) => {
      message += `- ${status.username} (${status.role === 'LEADER' ? '组长' : '成员'})：`
      if (status.hasReservation) {
        message += `已创建 (预约ID: ${status.reservationId}, 状态: ${status.reservationStatus})\n`
      } else {
        message += `❌ 未创建\n`
      }
    })
    
    alert(message)
  } catch (e: any) {
    console.error('检查预约状态失败', e)
    alert(e.response?.data?.message || '检查失败')
  }
}

async function cancelGroupReservation(reservationId: number) {
  if (!currentGroup.value) return
  
  if (!confirm('确定要取消这个协同预约吗？取消后，所有组员的个人预约也会被取消。')) {
    return
  }
  
  try {
    const res = await http.post(`/api/groups/${currentGroup.value.id}/reservations/${reservationId}/cancel`)
    const data = res.data || {}
    
    alert(data.message || '协同预约已取消')
    
    // 重新加载预约记录
    const reservationsRes = await http.get(`/api/groups/${currentGroup.value.id}/reservations`)
    groupReservations.value = reservationsRes.data
  } catch (e: any) {
    console.error('取消协同预约失败', e)
    alert(e.response?.data?.message || '取消失败')
  }
}

function getStatusClass(status: string): string {
  const statusMap: Record<string, string> = {
    'PENDING': 'status-pending',
    'CONFIRMED': 'status-confirmed',
    'CANCELLED': 'status-cancelled',
    'EXPIRED': 'status-expired',
    'COMPLETED': 'status-completed'
  }
  return statusMap[status] || 'status-unknown'
}

function getStatusText(status: string): string {
  const statusMap: Record<string, string> = {
    'PENDING': '待确认',
    'CONFIRMED': '已确认',
    'CANCELLED': '已取消',
    'EXPIRED': '已过期',
    'COMPLETED': '已完成'
  }
  return statusMap[status] || status
}

// 监听通知面板显示状态
watch(showNotifications, (newVal) => {
  if (newVal) {
    // 打开面板即视为“已读”
    loadNotifications().then(() => markAllAsRead())
  }
})

// 监听座位搜索变化
watch(seatSearch, () => {
  if (showSeatSelection.value) {
    loadSeatsForSelection()
  }
})

onMounted(() => {
  loadGroups()
  loadUnreadCount()
  // 定期刷新未读数量
  setInterval(() => {
    loadUnreadCount()
  }, 30000) // 每30秒刷新一次

  // 使用全局 WebSocket 实时刷新小组与协同预约数据
  offSeatMsg = realtime.onSeatMessage((data: any) => {
    if (!data || typeof data !== 'object') return
    if (data.type !== 'groupChanged') return
    // 小组列表与详情都按事件增量刷新
    loadGroups()
    refreshCurrentGroupData()
  })
})

onUnmounted(() => {
  if (offSeatMsg) {
    offSeatMsg()
    offSeatMsg = null
  }
})
</script>

<style scoped src="../styles/views/Groups.css"></style>
