<template>
  <section class="feedback-page">
    <div class="feedback-container">
      <!-- 用户侧：管理员回复的站内通知（与管理端「反馈通知」交互一致） -->
      <div v-if="showUserFeedbackNotifUi && showFeedbackNotifPanel" class="feedback-notifications-panel">
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

      <!-- 顶部搜索栏 -->
      <div class="search-header">
        <div class="search-box">
          <span class="search-input-icon" aria-hidden="true">🔍</span>
          <input 
            type="search"
            v-model="searchKeyword" 
            placeholder="搜索留言内容、用户名或管理员回复..." 
            class="search-input"
            autocomplete="off"
          />
        </div>
      </div>

      <!-- 筛选标签和我要留言按钮 -->
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
        <div class="filter-actions">
          <button
            v-if="showUserFeedbackNotifUi"
            type="button"
            class="btn-feedback-notifications"
            @click="showFeedbackNotifPanel = !showFeedbackNotifPanel"
          >
            反馈通知
            <span v-if="feedbackNotifUnreadCount > 0" class="feedback-notif-badge">{{ feedbackNotifUnreadCount > 99 ? '99+' : feedbackNotifUnreadCount }}</span>
          </button>
          <button class="btn-create" @click="showCreateModal = true">
            <span class="btn-icon">✈️</span>
            我要留言
          </button>
        </div>
      </div>

      <!-- 错误提示 -->
      <div v-if="submitErr && !loading" class="error-banner">
        <span class="error-icon">⚠️</span>
        <span>{{ submitErr }}</span>
        <button @click="submitErr = ''" class="error-close">×</button>
      </div>

      <!-- 反馈列表 -->
      <div class="feedbacks-list-container">
        <div v-if="loading" class="loading">加载中...</div>
        <div v-else-if="filteredFeedbacks.length === 0" class="empty-state">
          <p v-if="activeFilter === 'my'">您还没有发布过留言</p>
          <p v-else-if="activeFilter === 'replied'">暂无已回复的留言</p>
          <p v-else-if="activeFilter === 'unreplied'">暂无未回复的留言</p>
          <p v-else>暂无留言</p>
        </div>
        <div v-else class="feedbacks-list">
          <div 
            v-for="feedback in filteredFeedbacks" 
            :key="feedback.id" 
            class="feedback-item"
          >
            <div class="feedback-avatar">
              <img 
                v-if="feedback.avatarUrl" 
                :src="feedback.avatarUrl" 
                :alt="feedback.username"
                @error="handleImageError"
              />
              <div v-else class="avatar-placeholder">
                {{ feedback.username?.charAt(0)?.toUpperCase() || 'U' }}
              </div>
            </div>
            <div class="feedback-content">
              <div class="feedback-header">
                <span class="feedback-username">{{ feedback.username || '匿名用户' }}</span>
                <span class="feedback-time">{{ formatDateTime(feedback.createdAt) }}</span>
              </div>
              <div class="feedback-text">
                <span class="feedback-action">提出了问题</span>
                <span class="feedback-date">{{ formatDate(feedback.createdAt) }}</span>
              </div>
              <div class="feedback-message">{{ feedback.content }}</div>
              <div v-if="feedback.adminReply" class="feedback-reply">
                <div class="reply-header">
                  <span class="reply-label">管理员回复于</span>
                  <span class="reply-date">{{ formatDateTime(feedback.updatedAt) }}</span>
                </div>
                <div class="reply-content">{{ feedback.adminReply }}</div>
                
                <!-- 用户回复管理员的回复 -->
                <div v-if="feedback.userReply" class="user-reply">
                  <div class="reply-header">
                    <span class="reply-label">您回复于</span>
                    <span class="reply-date">{{ formatDateTime(feedback.updatedAt) }}</span>
                  </div>
                  <div class="reply-content">{{ feedback.userReply }}</div>
                </div>
                
                <!-- 回复按钮（只有当前用户的反馈且管理员已回复才能回复） -->
                <!-- 如果用户已经回复过，但管理员可能再次回复，允许用户继续回复 -->
                <div v-if="isMyFeedback(feedback) && feedback.status !== 'CLOSED'" class="reply-action">
                  <button 
                    v-if="!feedback.userReply || (feedback.userReply && feedback.adminReply)" 
                    @click="showUserReplyModal(feedback)" 
                    class="btn-reply-admin"
                  >
                    💬 {{ feedback.userReply ? '继续回复' : '回复管理员' }}
                  </button>
                </div>
              </div>
              <div v-if="feedback.isPrivate" class="feedback-privacy-badge">
                🔒 隐私
              </div>
              
              <!-- 删除按钮（只有当前用户的反馈才能删除） -->
              <div v-if="isMyFeedback(feedback)" class="feedback-actions">
                <button @click="deleteFeedback(feedback.id)" class="btn-delete">
                  🗑️ 删除
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 用户回复管理员模态框 -->
      <div v-if="replyingFeedback" class="modal-overlay" @click="closeUserReplyModal">
        <div class="modal-content" @click.stop>
          <h3>{{ replyingFeedback.userReply ? '继续回复管理员' : '回复管理员' }}</h3>
          <div v-if="replyingFeedback.adminReply" class="reply-preview">
            <p><strong>管理员回复：</strong></p>
            <p>{{ replyingFeedback.adminReply }}</p>
          </div>
          <form @submit.prevent="submitUserReply">
            <label>
              <span>您的回复</span>
              <textarea 
                v-model="userReplyContent" 
                required 
                rows="5"
                :placeholder="replyingFeedback.userReply ? '请输入您的继续回复...' : '请输入您的回复...'"
                :disabled="submittingReply"
              ></textarea>
            </label>
            <div class="modal-actions">
              <button type="submit" :disabled="submittingReply || !userReplyContent.trim()" class="btn-submit">
                {{ submittingReply ? '提交中...' : '提交' }}
              </button>
              <button type="button" @click="closeUserReplyModal" :disabled="submittingReply">取消</button>
            </div>
            <p v-if="replyErr" class="err">{{ replyErr }}</p>
          </form>
        </div>
      </div>

      <!-- 创建反馈模态框 -->
      <div v-if="showCreateModal" class="modal-overlay" @click="showCreateModal = false">
        <div class="modal-content" @click.stop>
          <div class="modal-header">
            <h3>我要留言</h3>
            <button class="modal-close" @click="showCreateModal = false">×</button>
          </div>
          <form @submit.prevent="submitFeedback" class="feedback-form">
            <label>
              <span>反馈类型</span>
              <select v-model="form.type" required>
                <option value="FACILITY">设施问题</option>
                <option value="SERVICE">服务问题</option>
                <option value="BOOK">图书问题</option>
                <option value="OTHER">其他</option>
              </select>
            </label>
            <label>
              <span>反馈内容</span>
              <textarea 
                v-model="form.content" 
                required 
                rows="5"
                placeholder="请详细描述您的问题或建议..."
              ></textarea>
            </label>
            <label>
              <span>隐私设置</span>
              <div class="privacy-options">
                <label class="radio-label">
                  <input type="radio" v-model="form.isPrivate" :value="false" />
                  <span>公开</span>
                  <span class="hint">（其他用户可见）</span>
                </label>
                <label class="radio-label">
                  <input type="radio" v-model="form.isPrivate" :value="true" />
                  <span>隐私</span>
                  <span class="hint">（仅管理员可见）</span>
                </label>
              </div>
            </label>
            <div class="modal-actions">
              <button type="button" class="btn-cancel" @click="showCreateModal = false">取消</button>
              <button type="submit" class="btn-submit" :disabled="submitting">
                {{ submitting ? '提交中...' : '提交' }}
              </button>
            </div>
            <p v-if="submitMsg" class="msg">{{ submitMsg }}</p>
            <p v-if="submitErr" class="err">{{ submitErr }}</p>
          </form>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import http from '../lib/http'
import { useAuthStore } from '../stores/auth'

const authStore = useAuthStore()
const FEEDBACK_USER_NOTIF_TYPES = ['FEEDBACK_ADMIN_REPLY']

const showUserFeedbackNotifUi = computed(
  () => authStore.isAuthenticated && !authStore.isAdmin
)
const showFeedbackNotifPanel = ref(false)
const feedbackNotifList = ref<any[]>([])
const feedbackNotifUnreadCount = ref(0)
const feedbackNotifLoading = ref(false)
let feedbackNotifPollTimer: ReturnType<typeof setInterval> | null = null

function isUserFeedbackNotifType(type: string | undefined) {
  return !!type && FEEDBACK_USER_NOTIF_TYPES.includes(type)
}

async function loadFeedbackNotifUnreadCount() {
  if (!showUserFeedbackNotifUi.value) {
    feedbackNotifUnreadCount.value = 0
    return
  }
  try {
    const res = await http.get('/api/notifications', { params: { unreadOnly: true } })
    const list = res.data || []
    feedbackNotifUnreadCount.value = list.filter(
      (n: any) => isUserFeedbackNotifType(n.type) && !n.isRead
    ).length
  } catch {
    feedbackNotifUnreadCount.value = 0
  }
}

async function loadFeedbackNotifListForPanel() {
  feedbackNotifLoading.value = true
  try {
    const res = await http.get('/api/notifications')
    feedbackNotifList.value = (res.data || []).filter((n: any) => isUserFeedbackNotifType(n.type))
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
  if (open && showUserFeedbackNotifUi.value) {
    await loadFeedbackNotifListForPanel()
    await markPanelFeedbackNotificationsRead()
    feedbackNotifUnreadCount.value = 0
    window.dispatchEvent(new CustomEvent('notification-read'))
  }
})

const feedbacks = ref<any[]>([])
const loading = ref(true)
const submitting = ref(false)
const submitMsg = ref('')
const submitErr = ref('')
const showCreateModal = ref(false)
const searchKeyword = ref('')
const activeFilter = ref('all')

// 用户回复相关
const replyingFeedback = ref<any>(null)
const userReplyContent = ref('')
const submittingReply = ref(false)
const replyErr = ref('')

const filterTabs = [
  { key: 'all', label: '全部' },
  { key: 'replied', label: '已回复' },
  { key: 'unreplied', label: '未回复' },
  { key: 'my', label: '我的发布' }
]

const form = ref({
  type: 'OTHER',
  content: '',
  isPrivate: false // 默认公开
})

// 过滤反馈列表
const filteredFeedbacks = computed(() => {
  let result = feedbacks.value

  // 根据筛选标签过滤
  if (activeFilter.value === 'replied') {
    result = result.filter(f => f.adminReply)
  } else if (activeFilter.value === 'unreplied') {
    result = result.filter(f => !f.adminReply)
  } else if (activeFilter.value === 'my') {
    // 通过用户名判断是否是当前用户的反馈
    const currentUsername = authStore.username
    if (currentUsername) {
      result = result.filter(f => {
        return f.username === currentUsername || 
               (f.userId && f.userId === getCurrentUserId())
      })
    } else {
      result = []
    }
  }

  // 根据搜索关键词过滤
  if (searchKeyword.value.trim()) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(f => 
      f.content?.toLowerCase().includes(keyword) ||
      f.username?.toLowerCase().includes(keyword) ||
      f.adminReply?.toLowerCase().includes(keyword)
    )
  }

  return result
})

function getCurrentUserId(): number | null {
  // 尝试从反馈列表中获取当前用户的userId
  // 如果反馈列表中有当前用户的反馈，可以从那里获取userId
  const currentUsername = authStore.username
  if (currentUsername && feedbacks.value.length > 0) {
    const myFeedback = feedbacks.value.find((f: any) => f.username === currentUsername)
    if (myFeedback && myFeedback.userId) {
      return myFeedback.userId
    }
  }
  return null
}

// 判断是否是当前用户的反馈
function isMyFeedback(feedback: any): boolean {
  if (!authStore.isAuthenticated) return false
  const currentUsername = authStore.username
  const currentUserId = getCurrentUserId()
  return (currentUsername && feedback.username === currentUsername) || 
         (currentUserId && feedback.userId === currentUserId)
}

// 显示用户回复模态框
function showUserReplyModal(feedback: any) {
  if (!authStore.isAuthenticated) {
    submitErr.value = '请先登录'
    return
  }
  replyingFeedback.value = feedback
  // 如果已有回复，显示提示
  userReplyContent.value = feedback.userReply ? '' : ''
  replyErr.value = ''
}

// 关闭用户回复模态框
function closeUserReplyModal() {
  replyingFeedback.value = null
  userReplyContent.value = ''
  replyErr.value = ''
}

// 提交用户回复
async function submitUserReply() {
  if (!userReplyContent.value.trim()) {
    replyErr.value = '回复内容不能为空'
    return
  }
  
  if (!replyingFeedback.value) {
    replyErr.value = '反馈信息错误'
    return
  }
  
  submittingReply.value = true
  replyErr.value = ''
  
  try {
    await http.put(`/api/feedbacks/${replyingFeedback.value.id}/user-reply`, {
      userReply: userReplyContent.value.trim()
    })
    
    submitMsg.value = '回复成功'
    closeUserReplyModal()
    await loadFeedbacks()
    setTimeout(() => {
      submitMsg.value = ''
    }, 2000)
  } catch (e: any) {
    console.error('回复失败', e)
    if (e?.response?.status === 401) {
      replyErr.value = '登录已过期，请重新登录'
      const router = (await import('../router')).default
      router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    } else if (e?.response?.status === 403) {
      replyErr.value = '只能回复自己的反馈'
    } else {
      replyErr.value = e?.response?.data?.message || e?.message || '回复失败，请稍后重试'
    }
  } finally {
    submittingReply.value = false
  }
}

// 删除反馈
async function deleteFeedback(id: number) {
  if (!confirm('确定要删除这条反馈吗？删除后无法恢复。')) {
    return
  }
  
  submitErr.value = ''
  try {
    const res = await http.delete(`/api/feedbacks/${id}`)
    submitMsg.value = res.data?.message || '反馈已删除'
    await loadFeedbacks()
    setTimeout(() => {
      submitMsg.value = ''
    }, 2000)
  } catch (e: any) {
    console.error('删除反馈失败', e)
    const errorMessage = e?.response?.data?.message || e?.message || '删除失败，请稍后重试'
    
    if (e?.response?.status === 401) {
      submitErr.value = '登录已过期，请重新登录'
      const router = (await import('../router')).default
      router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    } else if (e?.response?.status === 403) {
      submitErr.value = errorMessage
    } else {
      submitErr.value = errorMessage
    }
    
    // 显示错误消息
    setTimeout(() => {
      submitErr.value = ''
    }, 5000)
  }
}

function handleImageError(e: Event) {
  const img = e.target as HTMLImageElement
  img.style.display = 'none'
  const placeholder = img.nextElementSibling as HTMLElement
  if (placeholder) {
    placeholder.style.display = 'flex'
  }
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
      minute: '2-digit',
      second: '2-digit'
    })
  } catch {
    return dateTime
  }
}

function formatDate(dateTime: string | null | undefined): string {
  if (!dateTime) return '-'
  try {
    const date = new Date(dateTime)
    return date.toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    })
  } catch {
    return dateTime
  }
}

async function submitFeedback() {
  submitMsg.value = ''
  submitErr.value = ''
  
  // 检查登录状态
  if (!authStore.isAuthenticated) {
    submitErr.value = '请先登录后再提交反馈'
    const router = (await import('../router')).default
    router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  
  submitting.value = true
  try {
    await http.post('/api/feedbacks', form.value)
    submitMsg.value = '留言提交成功'
    form.value.content = ''
    form.value.type = 'OTHER'
    form.value.isPrivate = false
    showCreateModal.value = false
    await loadFeedbacks()
  } catch (e: any) {
    console.error('提交反馈失败', e)
    if (e?.response?.status === 401) {
      submitErr.value = '登录已过期，请重新登录'
      const router = (await import('../router')).default
      router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    } else if (e?.response?.status === 403) {
      submitErr.value = '权限不足，请确认已登录或联系管理员'
    } else {
      submitErr.value = e?.response?.data?.message || '提交失败，请稍后重试'
    }
  } finally {
    submitting.value = false
  }
}

async function loadFeedbacks() {
  loading.value = true
  submitErr.value = '' // 清除之前的错误
  try {
    const res = await http.get('/api/feedbacks/public')
    feedbacks.value = res.data || []
    console.log('加载反馈成功，数量:', feedbacks.value.length)
    
    // 如果用户已登录，也尝试加载"我的反馈"以确保能看到自己的所有反馈
    if (authStore.isAuthenticated) {
      try {
        const myRes = await http.get('/api/feedbacks/my')
        const myFeedbacks = myRes.data || []
        console.log('我的反馈数量:', myFeedbacks.length)
        
        // 合并自己的反馈（避免重复，并补充用户信息）
        const existingIds = new Set(feedbacks.value.map((f: any) => f.id))
        myFeedbacks.forEach((fb: any) => {
          if (!existingIds.has(fb.id)) {
            // 如果反馈不在列表中，添加用户信息后加入
            feedbacks.value.push({
              ...fb,
              username: authStore.username || '我',
              avatarUrl: null
            })
          } else {
            // 如果已存在，确保用户信息正确
            const existing = feedbacks.value.find((f: any) => f.id === fb.id)
            if (existing && !existing.username) {
              existing.username = authStore.username || '我'
            }
          }
        })
      } catch (myErr: any) {
        console.warn('加载我的反馈失败（可选）', myErr)
      }
    }
  } catch (e: any) {
    console.error('加载反馈失败', e)
    console.error('错误详情:', e?.response?.data || e?.message)
    feedbacks.value = []
    // 显示错误提示
    if (e?.response?.status === 500) {
      submitErr.value = '服务器错误：可能是数据库表不存在，请检查数据库'
    } else if (e?.response?.status === 403) {
      submitErr.value = '权限不足，请重新登录'
    } else {
      submitErr.value = '加载反馈失败: ' + (e?.response?.data?.message || e?.message || '未知错误')
    }
  } finally {
    loading.value = false
    if (authStore.isAuthenticated && !authStore.isAdmin) {
      loadFeedbackNotifUnreadCount()
    }
  }
}

onMounted(() => {
  loadFeedbacks()
  loadFeedbackNotifUnreadCount()
  feedbackNotifPollTimer = setInterval(() => {
    loadFeedbackNotifUnreadCount()
  }, 30000)
  window.dispatchEvent(new CustomEvent('notifications-loaded'))
})

onBeforeUnmount(() => {
  if (feedbackNotifPollTimer) {
    clearInterval(feedbackNotifPollTimer)
    feedbackNotifPollTimer = null
  }
})
</script>

<style scoped src="../styles/views/Feedback.css"></style>
