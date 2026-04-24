<template>
  <div class="notifications-page">
    <div class="notifications-container">
      <div class="page-header">
        <h2 class="page-title">我的通知</h2>
        <div class="header-actions">
          <button 
            v-if="unreadCount > 0"
            @click="markAllAsRead" 
            class="btn-mark-all-read"
          >
            全部标记为已读
          </button>
          <button @click="loadNotifications" class="btn-refresh">
            🔄 刷新
          </button>
        </div>
      </div>

      <!-- 消息提示 -->
      <div v-if="message" :class="['message', messageType]">
        <span>{{ message }}</span>
        <button @click="closeMessage" class="close-btn">×</button>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading" class="loading">加载中...</div>

      <!-- 空状态 -->
      <div v-else-if="notifications.length === 0" class="empty-state">
        <div class="empty-icon">📬</div>
        <p>暂无通知</p>
      </div>

      <!-- 通知列表 -->
      <div v-else class="notifications-list">
        <div 
          v-for="notification in notifications" 
          :key="notification.id" 
          class="notification-item"
          :class="{ 'unread': !notification.isRead }"
        >
          <div class="notification-icon">
            <span v-if="notification.type === 'BOOK_AVAILABLE'">📚</span>
            <span v-else>🔔</span>
          </div>
          <div class="notification-content">
            <div class="notification-header">
              <h3 class="notification-title">{{ notification.title }}</h3>
              <span class="notification-time">{{ formatDateTime(notification.createdAt) }}</span>
            </div>
            <p class="notification-text">{{ notification.content }}</p>
            <div class="notification-actions">
              <RouterLink 
                v-if="isAdminUser && (notification.type === 'FEEDBACK_NEW' || notification.type === 'FEEDBACK_FOLLOWUP')"
                to="/admin/feedbacks"
                class="btn-view-book"
                @click="markAsRead(notification.id)"
              >
                去反馈管理 →
              </RouterLink>
              <RouterLink 
                v-if="!isAdminUser && notification.type === 'FEEDBACK_ADMIN_REPLY'"
                to="/feedback"
                class="btn-view-book"
                @click="markAsRead(notification.id)"
              >
                查看反馈 →
              </RouterLink>
              <RouterLink 
                v-if="notification.type === 'BOOK_AVAILABLE' && notification.relatedBookId"
                :to="`/book-operation`"
                class="btn-view-book"
                @click="markAsRead(notification.id)"
              >
                去借阅 →
              </RouterLink>
              <button 
                v-if="!notification.isRead"
                @click="markAsRead(notification.id)"
                class="btn-mark-read"
              >
                标记已读
              </button>
              <button 
                @click="deleteNotification(notification.id)"
                class="btn-delete"
              >
                删除
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import http from '../lib/http'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const isAdminUser = computed(() => authStore.isAdmin)
const notifications = ref<any[]>([])
const loading = ref(false)
const message = ref('')
const messageType = ref<'success' | 'error'>('success')
const unreadOnly = ref(false)

const unreadCount = computed(() => {
  return notifications.value.filter(n => !n.isRead).length
})

function formatDateTime(dateTime: string | null | undefined): string {
  if (!dateTime) return '-'
  try {
    const date = new Date(dateTime)
    const now = new Date()
    const diff = now.getTime() - date.getTime()
    const minutes = Math.floor(diff / 60000)
    const hours = Math.floor(diff / 3600000)
    const days = Math.floor(diff / 86400000)

    if (minutes < 1) return '刚刚'
    if (minutes < 60) return `${minutes}分钟前`
    if (hours < 24) return `${hours}小时前`
    if (days < 7) return `${days}天前`
    
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

async function loadNotifications() {
  loading.value = true
  try {
    const params: any = {}
    if (unreadOnly.value) {
      params.unreadOnly = true
    }
    const res = await http.get('/api/notifications', { params })
    notifications.value = res.data || []
  } catch (e: any) {
    console.error('加载通知列表失败', e)
    showMessage('加载通知列表失败：' + (e?.response?.data?.message || e?.message || '未知错误'), 'error')
  } finally {
    loading.value = false
  }
}

async function markAsRead(id: number) {
  try {
    await http.put(`/api/notifications/${id}/read`)
    // 更新本地状态
    const notification = notifications.value.find(n => n.id === id)
    if (notification) {
      notification.isRead = true
    }
    // 通知 App.vue 刷新未读数量
    window.dispatchEvent(new CustomEvent('notification-read'))
    showMessage('已标记为已读', 'success')
  } catch (e: any) {
    showMessage('操作失败：' + (e?.response?.data?.message || e?.message || '未知错误'), 'error')
  }
}

async function markAllAsRead() {
  try {
    await http.put('/api/notifications/read-all')
    // 更新本地状态
    notifications.value.forEach(n => n.isRead = true)
    // 通知 App.vue 刷新未读数量
    window.dispatchEvent(new CustomEvent('notification-read'))
    showMessage('已标记所有通知为已读', 'success')
  } catch (e: any) {
    showMessage('操作失败：' + (e?.response?.data?.message || e?.message || '未知错误'), 'error')
  }
}

async function deleteNotification(id: number) {
  if (!confirm('确认删除这条通知？')) return
  
  try {
    const notification = notifications.value.find(n => n.id === id)
    await http.delete(`/api/notifications/${id}`)
    notifications.value = notifications.value.filter(n => n.id !== id)
    // 如果删除的是未读通知，通知 App.vue 刷新未读数量
    if (notification && !notification.isRead) {
      window.dispatchEvent(new CustomEvent('notification-read'))
    }
    showMessage('删除成功', 'success')
  } catch (e: any) {
    showMessage('删除失败：' + (e?.response?.data?.message || e?.message || '未知错误'), 'error')
  }
}

function showMessage(msg: string, type: 'success' | 'error' = 'success') {
  message.value = msg
  messageType.value = type
  setTimeout(() => {
    message.value = ''
  }, 3000)
}

function closeMessage() {
  message.value = ''
}

onMounted(() => {
  loadNotifications()
  // 通知页面加载时，通知 App.vue 刷新未读数量
  window.dispatchEvent(new CustomEvent('notifications-loaded'))
})
</script>

<style scoped src="../styles/views/Notifications.css"></style>
