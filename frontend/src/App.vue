<template>
  <div class="app-container">
    <!-- 左侧导航栏（仅登录后显示） -->
    <aside class="sidebar" :class="{ show: !sidebarCollapsed }" v-if="isAuthenticated">
      <div class="sidebar-header">
        <div class="logo">
          <span class="logo-icon">📚</span>
          <span class="logo-text">图书馆座位预约</span>
        </div>
      </div>
      <nav class="sidebar-nav">
        <!-- 公共功能 -->
        <div class="nav-group">
          <RouterLink v-if="!isAdmin" to="/" class="nav-item">
            <span class="nav-icon">🏠</span>
            <span class="nav-text">首页</span>
          </RouterLink>
          <!-- 公告通知：仅普通用户显示，管理员使用"公告"模块 -->
          <RouterLink v-if="!isAdmin" to="/notices" class="nav-item">
            <span class="nav-icon">📢</span>
            <span class="nav-text">公告通知</span>
          </RouterLink>
        </div>
        
        <!-- 管理员功能 -->
        <template v-if="isAdmin">
          <div class="nav-group">
            <RouterLink to="/admin/notices" class="nav-item">
              <span class="nav-icon">📢</span>
              <span class="nav-text">公告</span>
            </RouterLink>
          </div>
        </template>
        
        <!-- 用户功能 -->
        <template v-if="!isAdmin">
          <div class="nav-group">
            <div class="nav-group-title">用户功能</div>
            <!-- 可视化选座（可展开） -->
            <div class="nav-item-parent" :class="{ expanded: seatsMenuExpanded }">
              <div class="nav-item-toggle" @click="seatsMenuExpanded = !seatsMenuExpanded">
                <RouterLink to="/seats-map" class="nav-item-link" @click.stop>
                  <span class="nav-icon">🗺️</span>
                  <span class="nav-text">可视化选座</span>
                </RouterLink>
                <span class="nav-arrow">{{ seatsMenuExpanded ? '▼' : '▶' }}</span>
              </div>
              <div v-if="seatsMenuExpanded" class="nav-submenu">
                <RouterLink to="/reservation-books" class="nav-subitem">
                  <span class="nav-icon">📚</span>
                  <span class="nav-text">预约关联书籍</span>
                </RouterLink>
                <RouterLink to="/my-reservations" class="nav-subitem">
                  <span class="nav-icon">📋</span>
                  <span class="nav-text">我的预约记录</span>
                </RouterLink>
              </div>
            </div>
            <!-- 借阅书籍（可展开） -->
            <div class="nav-item-parent" :class="{ expanded: booksMenuExpanded }">
              <div class="nav-item-toggle" @click="booksMenuExpanded = !booksMenuExpanded">
                <RouterLink to="/book-operation" class="nav-item-link" @click.stop>
                  <span class="nav-icon">📚</span>
                  <span class="nav-text">借阅书籍</span>
                </RouterLink>
                <span class="nav-arrow">{{ booksMenuExpanded ? '▼' : '▶' }}</span>
              </div>
              <div v-if="booksMenuExpanded" class="nav-submenu">
                <RouterLink to="/borrows" class="nav-subitem">
                  <span class="nav-icon">📖</span>
                  <span class="nav-text">我的借阅</span>
                </RouterLink>
                <RouterLink to="/favorites" class="nav-subitem">
                  <span class="nav-icon">⭐</span>
                  <span class="nav-text">我的收藏</span>
                </RouterLink>
                <RouterLink to="/notifications" class="nav-subitem" style="position: relative;">
                  <span class="nav-icon">🔔</span>
                  <span class="nav-text">我的通知</span>
                  <span v-if="unreadNotificationCount > 0" class="notification-badge">{{ unreadNotificationCount > 99 ? '99+' : unreadNotificationCount }}</span>
                </RouterLink>
              </div>
            </div>
            <RouterLink to="/groups" class="nav-item">
              <span class="nav-icon">👥</span>
              <span class="nav-text">自习小组协同预约</span>
            </RouterLink>
            <RouterLink to="/feedback" class="nav-item">
              <span class="nav-icon">💬</span>
              <span class="nav-text">反馈</span>
            </RouterLink>
            <RouterLink to="/profile" class="nav-item">
              <span class="nav-icon">👤</span>
              <span class="nav-text">个人信息</span>
            </RouterLink>
          </div>
        </template>

        <!-- 管理员功能 -->
        <template v-if="isAdmin">
          <div class="nav-group">
            <div class="nav-group-title">管理功能</div>
            <RouterLink to="/dashboard" class="nav-item">
              <span class="nav-icon">📊</span>
              <span class="nav-text">数据看板</span>
            </RouterLink>
            <RouterLink to="/admin/seats" class="nav-item">
              <span class="nav-icon">🪑</span>
              <span class="nav-text">座位管理</span>
            </RouterLink>
            <RouterLink to="/admin/reservations" class="nav-item">
              <span class="nav-icon">📋</span>
              <span class="nav-text">预约管理</span>
            </RouterLink>
            <RouterLink to="/admin/users" class="nav-item">
              <span class="nav-icon">👥</span>
              <span class="nav-text">用户管理</span>
            </RouterLink>
            <RouterLink to="/admin/violations" class="nav-item">
              <span class="nav-icon">⚠️</span>
              <span class="nav-text">违规管理</span>
            </RouterLink>
            <!-- 图书管理（可展开） -->
            <div class="nav-item-parent" :class="{ expanded: adminBooksMenuExpanded }">
              <div class="nav-item-toggle" @click="adminBooksMenuExpanded = !adminBooksMenuExpanded">
                <RouterLink to="/admin/books" class="nav-item-link" @click.stop>
                  <span class="nav-icon">📚</span>
                  <span class="nav-text">图书管理</span>
                </RouterLink>
                <span class="nav-arrow">{{ adminBooksMenuExpanded ? '▼' : '▶' }}</span>
              </div>
              <div v-if="adminBooksMenuExpanded" class="nav-submenu">
                <RouterLink to="/admin/borrows" class="nav-subitem">
                  <span class="nav-icon">📖</span>
                  <span class="nav-text">借阅管理</span>
                </RouterLink>
              </div>
            </div>
            <RouterLink to="/admin/feedbacks" class="nav-item">
              <span class="nav-icon">💬</span>
              <span class="nav-text">反馈管理</span>
            </RouterLink>
            <RouterLink to="/profile" class="nav-item">
              <span class="nav-icon">👤</span>
              <span class="nav-text">个人信息</span>
            </RouterLink>
          </div>
        </template>
      </nav>
    </aside>

    <!-- 主内容区域 -->
    <div class="main-content">
      <!-- 紧急公告通知栏 -->
      <div v-if="urgentNotices.length > 0" class="urgent-notice-bar" :class="{ closure: urgentNotices[0]?.type === 'CLOSURE' }">
        <div class="urgent-notice-content">
          <span class="urgent-icon">⚠️</span>
          <div class="urgent-notice-text">
            <span class="urgent-title">{{ urgentNotices[0].title }}</span>
            <span class="urgent-preview">{{ getNoticePreview(urgentNotices[0].content) }}</span>
          </div>
          <RouterLink to="/notices" class="urgent-notice-link">查看详情 →</RouterLink>
          <button @click="dismissUrgentNotice" class="urgent-notice-close">×</button>
        </div>
      </div>
      
      <!-- 顶部标题栏 -->
      <header class="top-header">
        <div class="header-left">
          <button class="menu-toggle" @click="toggleSidebar">
            <span>☰</span>
          </button>
          <h1 class="page-title">{{ currentPageTitle }}</h1>
        </div>
        <div class="header-right" v-if="isAuthenticated">
          <div class="user-menu">
            <img 
              :src="userAvatar || '/default-avatar.png'" 
              :alt="username || '用户'"
              class="user-avatar"
              :key="userAvatar || 'default'"
              @error="handleAvatarError"
              @load="handleAvatarLoad"
            />
            <span class="username">{{ username || '用户' }}</span>
            <div class="user-dropdown">
              <button class="dropdown-toggle" @click="showUserMenu = !showUserMenu">
                <span>▼</span>
              </button>
              <div v-if="showUserMenu" class="dropdown-menu">
                <RouterLink to="/profile" class="dropdown-item" @click="showUserMenu = false">个人信息</RouterLink>
                <button class="dropdown-item" @click="logout">退出登录</button>
              </div>
            </div>
          </div>
        </div>
        <div class="header-right" v-else>
          <RouterLink to="/login" class="login-link">登录</RouterLink>
        </div>
      </header>
      
      <!-- 页面内容 -->
      <main class="content-area">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useAuthStore } from './stores/auth'
import http from './lib/http'
import { useRouter } from 'vue-router'
import { useRealtimeStore } from './stores/realtime'

const auth = useAuthStore()
const realtime = useRealtimeStore()
const router = useRouter()
const isAuthenticated = computed(() => auth.isAuthenticated)
const isAdmin = computed(() => auth.isAdmin)
const username = computed(() => auth.username)
const userAvatar = ref<string | null>(null)
const showUserMenu = ref(false)
const sidebarCollapsed = ref(false)
const urgentNotices = ref<any[]>([])
const unreadNotificationCount = ref(0)
const seatsMenuExpanded = ref(false)
const booksMenuExpanded = ref(false)
const adminBooksMenuExpanded = ref(false)

// 监听路由变化，自动展开包含当前路由的菜单
watch(() => router.currentRoute.value.path, (path) => {
  if (path === '/my-reservations' || path === '/reservation-books' || path === '/seats-map') {
    seatsMenuExpanded.value = true
  }
  if (path === '/book-operation' || path === '/borrows' || path === '/favorites' || path === '/notifications') {
    booksMenuExpanded.value = true
  }
  if (path === '/admin/books' || path === '/admin/borrows') {
    adminBooksMenuExpanded.value = true
  }
}, { immediate: true })

// 从localStorage获取已关闭的通知ID列表
function getDismissedNoticeIds(): Set<number> {
  try {
    const stored = localStorage.getItem('dismissedUrgentNoticeIds')
    if (stored) {
      const ids = JSON.parse(stored)
      return new Set(ids)
    }
  } catch (e) {
    console.error('读取已关闭通知列表失败', e)
  }
  return new Set<number>()
}

// 保存已关闭的通知ID到localStorage
function saveDismissedNoticeId(id: number) {
  try {
    const dismissedIds = getDismissedNoticeIds()
    dismissedIds.add(id)
    localStorage.setItem('dismissedUrgentNoticeIds', JSON.stringify(Array.from(dismissedIds)))
  } catch (e) {
    console.error('保存已关闭通知ID失败', e)
  }
}

const currentPageTitle = computed(() => {
  const route = router.currentRoute.value
  const titleMap: Record<string, string> = {
    'home': '首页',
    'dashboard': '数据看板',
    'notices': '公告通知',
    'myReservations': '我的预约记录',
    'seatsMap': '可视化选座',
    'bookOperation': '借阅书籍',
    'borrows': '我的借阅',
    'groups': '自习小组协同预约',
    'feedback': '反馈',
    'profile': '个人信息',
    'adminSeats': '座位管理',
    'adminReservations': '预约管理',
    'adminUsers': '用户管理',
    'adminViolations': '违规管理',
    'adminNotices': '公告',
    'adminBooks': '图书管理',
    'adminBorrows': '借阅管理',
    'adminFeedbacks': '反馈管理',
    'reservationBooks': '预约关联书籍',
    'notifications': '我的通知'
  }
  return titleMap[route.name as string] || '图书馆座位预约系统'
})

function handleAvatarError(event: Event) {
  const target = event.target as HTMLImageElement
  console.error('头像加载失败:', target.src)
  // 如果已经是默认头像，就不再尝试
  if (!target.src.includes('default-avatar')) {
    userAvatar.value = '/default-avatar.png'
  }
}

function handleAvatarLoad() {
  console.log('头像加载成功:', userAvatar.value)
}

function getNoticePreview(content: string): string {
  if (!content) return ''
  return content.length > 60 ? content.substring(0, 60) + '...' : content
}

function dismissUrgentNotice() {
  if (urgentNotices.value.length > 0) {
    const noticeId = urgentNotices.value[0].id
    // 保存到localStorage
    saveDismissedNoticeId(noticeId)
    // 立即从列表中移除
    urgentNotices.value = urgentNotices.value.filter(notice => notice.id !== noticeId)
  }
}

async function loadUrgentNotices() {
  try {
    const res = await http.get('/api/notices/public')
    const allNotices = res.data || []
    // 获取已关闭的通知ID列表
    const dismissedIds = getDismissedNoticeIds()
    // 只显示紧急和闭馆通知，且未被关闭的
    // 注意：后端 /api/notices/public 已经过滤了已发布的公告，所以这里不需要再检查 isPublished
    urgentNotices.value = allNotices.filter((notice: any) => 
      (notice.type === 'URGENT' || notice.type === 'CLOSURE') && 
      !dismissedIds.has(notice.id)
    )
  } catch (e: any) {
    console.error('加载紧急公告失败', e)
    // 静默失败，不显示通知栏
    urgentNotices.value = []
  }
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

// 点击外部关闭用户菜单
watch(showUserMenu, (newVal) => {
  if (newVal) {
    const closeMenu = (e: MouseEvent) => {
      const target = e.target as HTMLElement
      if (!target.closest('.user-menu')) {
        showUserMenu.value = false
        document.removeEventListener('click', closeMenu)
      }
    }
    setTimeout(() => {
      document.addEventListener('click', closeMenu)
    }, 0)
  }
})

// 监听登录状态变化，切换用户时重新加载头像
watch(() => auth.isAuthenticated, async (newVal, oldVal) => {
  // 当从已登录变为未登录时，清除头像
  if (oldVal && !newVal) {
    userAvatar.value = null
    console.log('用户已登出，清除头像')
  }
  // 当从未登录变为已登录时，加载头像
  else if (!oldVal && newVal) {
    console.log('用户已登录，加载头像')
    await loadUserAvatar()
  }
  // 当用户切换时（保持登录状态但用户可能不同），重新加载头像
  else if (newVal && oldVal) {
    console.log('检测到用户切换，重新加载头像')
    await loadUserAvatar()
  }
})

// 监听用户名变化，切换用户时重新加载头像
watch(() => auth.username, async (newUsername, oldUsername) => {
  if (newUsername && newUsername !== oldUsername && auth.isAuthenticated) {
    console.log('用户名变化，重新加载头像:', oldUsername, '->', newUsername)
    await loadUserAvatar()
  }
})

function handleAvatarUpdate(event: Event) {
  const customEvent = event as CustomEvent
  const avatarUrl = customEvent.detail?.avatarUrl
  
  // 如果 avatarUrl 为 null 或 undefined，清空头像
  if (!avatarUrl) {
    userAvatar.value = null
    console.log('App.vue 清空头像')
    return
  }
  
  // 处理相对路径和绝对路径
  let finalAvatarUrl: string
  if (avatarUrl.startsWith('http://') || avatarUrl.startsWith('https://')) {
    finalAvatarUrl = avatarUrl
  } else if (avatarUrl.startsWith('/')) {
    finalAvatarUrl = avatarUrl
  } else {
    finalAvatarUrl = `/${avatarUrl}`
  }
  // 添加时间戳防止缓存，强制刷新图片
  userAvatar.value = finalAvatarUrl + (finalAvatarUrl.includes('?') ? '&' : '?') + '_t=' + Date.now()
  console.log('App.vue 更新头像URL:', userAvatar.value)
}

// 加载用户头像
async function loadUserAvatar() {
  if (!auth.token) {
    userAvatar.value = null
    return
  }
  
  try {
    const res = await http.get('/api/auth/profile')
    const avatarUrl = res.data?.avatarUrl
    
    // 更新头像
    if (avatarUrl) {
      // 处理相对路径和绝对路径
      let finalAvatarUrl: string
      if (avatarUrl.startsWith('http://') || avatarUrl.startsWith('https://')) {
        finalAvatarUrl = avatarUrl
      } else if (avatarUrl.startsWith('/')) {
        // 使用相对路径，通过Vite代理访问
        finalAvatarUrl = avatarUrl
      } else {
        finalAvatarUrl = `/${avatarUrl}`
      }
      // 添加时间戳防止缓存，强制刷新图片
      userAvatar.value = finalAvatarUrl + (finalAvatarUrl.includes('?') ? '&' : '?') + '_t=' + Date.now()
      console.log('App.vue 设置头像URL:', userAvatar.value)
    } else {
      // 如果没有头像，设置为null以显示默认头像
      userAvatar.value = null
      console.log('用户没有头像，使用默认头像')
    }
  } catch (e: any) {
    console.error('加载头像失败:', e)
    userAvatar.value = null
  }
}

// 应用启动时验证 token 是否有效，并检测后端是否重启
onMounted(async () => {
  // 监听头像更新事件
  window.addEventListener('avatar-updated', handleAvatarUpdate)
  
  // 监听通知相关事件，刷新未读数量
  window.addEventListener('notification-read', loadUnreadNotificationCount)
  window.addEventListener('notifications-loaded', loadUnreadNotificationCount)
  
  // 如果有 token，尝试验证是否有效
  if (auth.token) {
    try {
      // 尝试调用 profile 接口验证 token，并获取服务器启动时间
      const res = await http.get('/api/auth/profile')
      const serverStartTime = res.data?.serverStartTime
      
      // 加载用户头像
      await loadUserAvatar()
      
      console.log('当前存储的服务器启动时间:', auth.serverStartTime)
      console.log('服务器返回的启动时间:', serverStartTime)
      
      // 检测后端是否重启：比较服务器启动时间
      if (serverStartTime) {
        if (auth.serverStartTime && auth.serverStartTime !== serverStartTime) {
          // 启动时间不一致，说明后端重启了，清除登录状态
          console.log('检测到后端重启（启动时间不一致），清除登录状态')
          console.log('旧启动时间:', auth.serverStartTime, '新启动时间:', serverStartTime)
          auth.clear()
          return
        }
        // 更新服务器启动时间（首次登录或启动时间一致）
        if (!auth.serverStartTime || auth.serverStartTime === serverStartTime) {
          auth.setServerStartTime(serverStartTime)
          console.log('更新服务器启动时间:', serverStartTime)
        }
      } else {
        console.warn('服务器未返回启动时间')
      }
      // 如果成功，说明 token 有效，保持登录状态
    } catch (e: any) {
      // 如果失败（401 未授权、网络错误等），清除本地存储
      // 这会在后端重启或 token 失效时自动清除登录状态
      console.error('验证 token 失败:', e)
      if (e?.response?.status === 401 || !e?.response) {
        console.log('Token 无效或后端不可用，清除登录状态')
        auth.clear()
      }
    }
  } else {
    console.log('没有 token，跳过验证')
  }

  // 启动全局 WebSocket（按登录态/角色自动连接 seat-status 与 feedback）
  realtime.syncConnections()
  
  // 加载紧急公告
  loadUrgentNotices()
  
  // 普通用户：侧栏「我的通知」角标；管理员在「反馈管理」内用反馈通知面板，不轮询此接口
  if (auth.isAuthenticated && !auth.isAdmin) {
    loadUnreadNotificationCount()
    const notificationInterval = setInterval(() => {
      if (auth.isAuthenticated && !auth.isAdmin) {
        loadUnreadNotificationCount()
      } else {
        clearInterval(notificationInterval)
      }
    }, 30000)
  }
})

// 登录态/角色变化时，同步全局 WebSocket 连接（支持“登录后再切角色/退出”）
watch(
  () => [auth.token, auth.role],
  () => {
    realtime.syncConnections()
  }
)

async function loadUnreadNotificationCount() {
  try {
    const res = await http.get('/api/notifications/unread-count')
    unreadNotificationCount.value = res.data?.count || 0
  } catch (e: any) {
    // 静默处理错误
    console.debug('获取未读通知数量失败:', e)
    unreadNotificationCount.value = 0
  }
}

onUnmounted(() => {
  window.removeEventListener('avatar-updated', handleAvatarUpdate)
  window.removeEventListener('notification-read', loadUnreadNotificationCount)
  window.removeEventListener('notifications-loaded', loadUnreadNotificationCount)
  realtime.disconnectAll()
})

async function logout() {
  try {
    await http.post('/api/auth/logout')
  } catch (e) {
    // 即使登出接口失败也要清本地态
    console.error(e)
  } finally {
    // 清除头像
    userAvatar.value = null
    auth.clear()
    if (router.currentRoute.value.name !== 'home') {
      router.replace({ name: 'home' })
    }
  }
}
</script>

<style src="./styles/App.css"></style>


