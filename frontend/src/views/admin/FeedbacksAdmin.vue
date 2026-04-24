<template>
  <section class="admin-page">
    <div class="page-header page-header-row">
      <h2>反馈管理</h2>
      <button
        type="button"
        class="btn-feedback-notifications"
        @click="showFeedbackNotifPanel = !showFeedbackNotifPanel"
      >
        反馈通知
        <span v-if="feedbackNotifUnreadCount > 0" class="feedback-notif-badge">{{ feedbackNotifUnreadCount > 99 ? '99+' : feedbackNotifUnreadCount }}</span>
      </button>
    </div>

    <!-- 右上角固定面板（与自习小组「通知」交互一致） -->
    <div v-if="showFeedbackNotifPanel" class="feedback-notifications-panel">
      <div class="feedback-panel-header">
        <h3>反馈通知</h3>
        <button type="button" class="panel-close-btn" @click="showFeedbackNotifPanel = false">×</button>
      </div>
      <div class="feedback-panel-body">
        <div v-if="feedbackNotifLoading" class="panel-loading">加载中…</div>
        <div v-else-if="feedbackNotifList.length === 0" class="panel-empty">暂无反馈通知</div>
        <div v-else class="feedback-notif-list">
          <div
            v-for="notif in feedbackNotifList"
            :key="notif.id"
            :class="['feedback-notif-item', { unread: !notif.isRead }]"
          >
            <div class="feedback-notif-text">
              <div class="feedback-notif-title">{{ notif.title }}</div>
              <div class="feedback-notif-content">{{ notif.content }}</div>
              <div class="feedback-notif-time">{{ formatDateTime(notif.createdAt) }}</div>
            </div>
            <span v-if="!notif.isRead" class="unread-dot" />
          </div>
        </div>
      </div>
    </div>

    <div class="search-row">
      <div class="search-box-admin">
        <span class="search-input-icon" aria-hidden="true">🔍</span>
        <input
          v-model="searchKeyword"
          type="search"
          class="search-input-admin"
          placeholder="搜索反馈内容、用户名..."
          autocomplete="off"
        />
      </div>
    </div>
    
    <!-- 筛选标签 -->
    <div class="filter-section">
      <div class="filter-tabs">
        <button 
          v-for="tab in filterTabs" 
          :key="tab.key"
          :class="['filter-tab', { active: activeFilter === tab.key }]"
          @click="activeFilter = tab.key"
        >
          {{ tab.label }}
        </button>
      </div>
    </div>
    
    <div v-if="message" :class="['message', messageType]">
      <span>{{ message }}</span>
      <button @click="message = ''" class="close-btn">×</button>
    </div>
    
    <div v-if="loading && feedbacks.length === 0" class="loading">加载中...</div>
    <div v-else-if="displayedFeedbacks.length === 0" class="empty-state">
      {{ emptyListHint }}
    </div>
    <div v-else class="admin-list">
      <div v-for="feedback in displayedFeedbacks" :key="feedback.id" class="admin-item" :class="feedback.status">
        <div class="item-content">
          <div class="item-header">
            <div class="header-left">
              <h3>{{ feedback.username || `用户ID: ${feedback.userId}` }}</h3>
              <span class="type">{{ getTypeText(feedback.type) }}</span>
              <span v-if="feedback.isPrivate" class="privacy-badge">🔒 隐私</span>
            </div>
            <span class="status" :class="feedback.status">{{ getStatusText(feedback.status) }}</span>
          </div>
          <div class="feedback-content">
            <p><strong>反馈内容：</strong></p>
            <p>{{ feedback.content }}</p>
          </div>
          <div v-if="feedback.adminReply" class="feedback-reply">
            <p><strong>管理员回复：</strong></p>
            <p>{{ feedback.adminReply }}</p>
          </div>
          <div v-if="feedback.userReply" class="user-reply">
            <p><strong>用户回复：</strong></p>
            <p>{{ feedback.userReply }}</p>
          </div>
          <div class="item-actions">
            <button 
              v-if="feedback.status !== 'CLOSED'" 
              @click="showReplyModal(feedback)"
              class="btn-reply"
            >
              回复
            </button>
            <button 
              v-if="feedback.status !== 'CLOSED'" 
              @click="closeFeedback(feedback.id)" 
              class="btn-close"
            >
              关闭
            </button>
          </div>
          <div class="item-time">{{ formatDateTime(feedback.createdAt) }}</div>
        </div>
      </div>
    </div>
    
    <!-- 回复模态框 -->
    <div v-if="replyingFeedback" class="modal" @click.self="closeReplyModal">
      <div class="modal-content">
        <h3>回复反馈</h3>
        <form @submit.prevent="submitReply">
          <label>
            <span>回复内容</span>
            <textarea 
              v-model="replyContent" 
              required 
              rows="5"
              placeholder="请输入回复内容..."
              :disabled="saving"
            ></textarea>
          </label>
          <div class="modal-actions">
            <button type="submit" :disabled="saving || !replyContent.trim()" class="btn-submit">
              {{ saving ? '提交中...' : '提交' }}
            </button>
            <button type="button" @click="closeReplyModal" :disabled="saving">取消</button>
          </div>
        </form>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import http from '../../lib/http'
import { useRealtimeStore } from '../../stores/realtime'

const FEEDBACK_NOTIF_TYPES = ['FEEDBACK_NEW', 'FEEDBACK_FOLLOWUP']

const showFeedbackNotifPanel = ref(false)
const feedbackNotifList = ref<any[]>([])
const feedbackNotifUnreadCount = ref(0)
const feedbackNotifLoading = ref(false)

const feedbacks = ref<any[]>([])
const loading = ref(false)
const replyingFeedback = ref<any>(null)
const replyContent = ref('')
const saving = ref(false)
const message = ref('')
const messageType = ref<'success' | 'error'>('success')
const activeFilter = ref<'all' | 'pending' | 'processed'>('all')
const searchKeyword = ref('')
const debouncedSearch = ref('')
let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null

watch(searchKeyword, (v) => {
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(() => {
    debouncedSearch.value = (v || '').trim()
  }, 320)
})

// 筛选标签
const filterTabs = [
  { key: 'all', label: '全部' },
  { key: 'pending', label: '待处理' },
  { key: 'processed', label: '已处理' }
]

// 按状态与关键字筛选（关键字防抖后参与过滤）
const displayedFeedbacks = computed(() => {
  let list = feedbacks.value
  if (activeFilter.value === 'pending') {
    list = list.filter(f => f.status === 'PENDING')
  } else if (activeFilter.value === 'processed') {
    list = list.filter(f => f.status === 'PROCESSED')
  }
  const k = debouncedSearch.value.toLowerCase()
  if (!k) return list
  return list.filter(
    f =>
      f.content?.toLowerCase().includes(k) ||
      f.username?.toLowerCase().includes(k) ||
      String(f.userId ?? '').includes(k) ||
      f.adminReply?.toLowerCase().includes(k) ||
      f.userReply?.toLowerCase().includes(k) ||
      getTypeText(f.type).toLowerCase().includes(k)
  )
})

const emptyListHint = computed(() => {
  if (feedbacks.value.length === 0) return '暂无反馈'
  return '没有符合条件的反馈'
})

function isFeedbackNotifType(type: string | undefined) {
  return !!type && FEEDBACK_NOTIF_TYPES.includes(type)
}

async function loadFeedbackNotifUnreadCount() {
  try {
    const res = await http.get('/api/notifications', { params: { unreadOnly: true } })
    const list = res.data || []
    feedbackNotifUnreadCount.value = list.filter(
      (n: any) => isFeedbackNotifType(n.type) && !n.isRead
    ).length
  } catch {
    feedbackNotifUnreadCount.value = 0
  }
}

async function loadFeedbackNotifListForPanel() {
  feedbackNotifLoading.value = true
  try {
    const res = await http.get('/api/notifications')
    feedbackNotifList.value = (res.data || []).filter((n: any) => isFeedbackNotifType(n.type))
  } catch {
    feedbackNotifList.value = []
  } finally {
    feedbackNotifLoading.value = false
  }
}

async function markPanelFeedbackNotificationsRead() {
  for (const n of feedbackNotifList.value) {
    if (!n.isRead) {
      try {
        await http.put(`/api/notifications/${n.id}/read`)
        n.isRead = true
      } catch {
        /* ignore */
      }
    }
  }
}

watch(showFeedbackNotifPanel, async (open) => {
  if (open) {
    await loadFeedbackNotifListForPanel()
    await markPanelFeedbackNotificationsRead()
    feedbackNotifUnreadCount.value = 0
    window.dispatchEvent(new CustomEvent('notification-read'))
  }
})

function getTypeText(type: string): string {
  const typeMap: Record<string, string> = {
    'FACILITY': '设施问题',
    'SERVICE': '服务问题',
    'BOOK': '图书问题',
    'OTHER': '其他'
  }
  return typeMap[type] || type
}

function getStatusText(status: string): string {
  const statusMap: Record<string, string> = {
    'PENDING': '待处理',
    'PROCESSED': '已处理',
    'CLOSED': '已关闭'
  }
  return statusMap[status] || status
}

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

function showReplyModal(feedback: any) {
  replyingFeedback.value = feedback
  replyContent.value = feedback.adminReply || ''
}

function closeReplyModal() {
  replyingFeedback.value = null
  replyContent.value = ''
}

async function submitReply() {
  if (!replyContent.value.trim()) {
    showMessage('回复内容不能为空', 'error')
    return
  }

  const feedbackId = replyingFeedback.value.id
  const replyText = replyContent.value.trim()

  saving.value = true
  message.value = ''
  try {
    const res = await http.put(`/api/feedbacks/${feedbackId}/reply`, {
      adminReply: replyText
    })
    showMessage(res.data?.message || '回复成功', 'success')
    closeReplyModal()
    await loadFeedbacks()
  } catch (e: any) {
    console.error('回复反馈失败:', e)
    const errorMsg = e?.message || e?.response?.data?.message || '回复失败'
    showMessage(errorMsg, 'error')
  } finally {
    saving.value = false
  }
}

async function closeFeedback(id: number) {
  if (!confirm('确认关闭该反馈？')) return
  
  try {
    await http.put(`/api/feedbacks/${id}/close`)
    showMessage('反馈已关闭', 'success')
    setTimeout(() => {
      loadFeedbacks()
    }, 500)
  } catch (e: any) {
    showMessage(e?.response?.data?.message || '关闭失败', 'error')
  }
}

async function loadFeedbacks() {
  loading.value = true
  try {
    const res = await http.get('/api/feedbacks')
    feedbacks.value = res.data || []
  } catch (e: any) {
    console.error('加载反馈失败', e)
    showMessage('加载反馈失败', 'error')
  } finally {
    loading.value = false
  }
}

function showMessage(msg: string, type: 'success' | 'error' = 'success') {
  message.value = msg
  messageType.value = type
  setTimeout(() => {
    message.value = ''
  }, 3000)
}

// 答辩演示：保持与后端的反馈 WebSocket 连接与实时推送；不在页面上展示连接状态 UI
const realtime = useRealtimeStore()
let offFeedbackMsg: null | (() => void) = null

function connectWebSocket() {
  // WebSocket 由 App 全局维护，这里只订阅消息
  if (offFeedbackMsg) return
  offFeedbackMsg = realtime.onFeedbackMessage((data: any) => {
    handleWebSocketMessage(data)
  })
}

function handleWebSocketMessage(data: any) {
  if (data.type === 'newFeedback') {
    showMessage(`收到新反馈：${data.username}`, 'success')
    loadFeedbacks()
    loadFeedbackNotifUnreadCount()
  } else if (data.type === 'feedbackReply') {
    const feedback = feedbacks.value.find((f: any) => f.id === data.feedbackId)
    if (feedback) {
      feedback.adminReply = data.adminReply
      feedback.status = 'PROCESSED'
    } else {
      loadFeedbacks()
    }
  } else if (data.type === 'feedbackStatusUpdate') {
    const feedback = feedbacks.value.find((f: any) => f.id === data.feedbackId)
    if (feedback) {
      feedback.status = data.status
    } else {
      loadFeedbacks()
    }
  }
}

function disconnectWebSocket() {
  if (offFeedbackMsg) {
    offFeedbackMsg()
    offFeedbackMsg = null
  }
}

onMounted(() => {
  loadFeedbacks()
  connectWebSocket()
  loadFeedbackNotifUnreadCount()
  window.dispatchEvent(new CustomEvent('notifications-loaded'))
})

onBeforeUnmount(() => {
  disconnectWebSocket()
  if (searchDebounceTimer) {
    clearTimeout(searchDebounceTimer)
    searchDebounceTimer = null
  }
})
</script>

<style scoped src="../../styles/views/admin/FeedbacksAdmin.css"></style>
