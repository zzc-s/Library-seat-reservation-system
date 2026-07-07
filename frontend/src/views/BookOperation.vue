<template>
  <div class="book-operation-page">
    <div class="book-operation-container">
      <h2 class="page-title">借阅书籍</h2>

      <!-- 搜索和筛选区域 -->
      <div class="filter-section">
        <div class="search-bar">
          <span class="search-input-icon" aria-hidden="true">🔍</span>
          <input 
            v-model="searchKeyword" 
            type="search" 
            placeholder="搜索书名、作者或ISBN..." 
            class="search-input"
            autocomplete="off"
          />
        </div>
        
        <div class="category-filter">
          <button 
            v-for="cat in categories" 
            :key="cat"
            @click="selectedCategory = cat; loadBooks()"
            :class="['category-btn', { active: selectedCategory === cat }]"
          >
            {{ cat }}
          </button>
        </div>
      </div>

      <!-- 消息提示 -->
      <div v-if="message" :class="['message', messageType]">
        <span>{{ message }}</span>
        <button @click="closeMessage" class="close-btn">×</button>
      </div>

      <!-- 图书列表 -->
      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="books.length === 0" class="empty-state">
        <p>未找到符合条件的图书</p>
      </div>
      <div v-else class="books-grid">
        <div 
          v-for="book in books" 
          :key="book.id" 
          class="book-card"
          @click="viewBookDetail(book.id)"
        >
          <div class="book-cover">
            <img 
              v-if="book.title && !isImageFailed(book)" 
              :src="getBookCoverUrl(book)" 
              :alt="book.title"
              class="cover-image"
              @error="handleImageError($event, book)"
            />
            <div v-if="!book.title || isImageFailed(book)" class="cover-placeholder">📚</div>
          </div>
          <div class="book-info">
            <div class="book-status">
              <span :class="['status-badge', getStatusClass(book)]">
                {{ getStatusText(book) }}
              </span>
            </div>
            <h3 class="book-title">{{ book.title }}</h3>
            <p class="book-author">作者：{{ book.author || '未知' }}</p>
            <p class="book-publisher" v-if="book.publisher">出版社：{{ book.publisher }}</p>
            <p class="book-stock">
              <span :class="{ 'stock-zero': !book.stock || book.stock === 0 }">
                库存{{ book.stock || 0 }}本
              </span>
            </p>
            <div class="book-actions" @click.stop>
              <!-- 未上架图书：显示订阅按钮 -->
              <template v-if="!book.isBorrowable || book.isBorrowable === false">
                <button 
                  @click="toggleSubscription(book.id)"
                  class="btn-subscribe"
                  :class="{ subscribed: subscriptionIds.includes(book.id) }"
                >
                  {{ subscriptionIds.includes(book.id) ? '🔔 已订阅' : '🔔 订阅' }}
                </button>
                <span class="book-status-text">图书未上架，订阅后上架时通知您</span>
              </template>
              <!-- 已上架图书：显示借阅和收藏按钮 -->
              <template v-else>
                <button 
                  v-if="book.stock > 0"
                  @click="openBorrowDialog(book)"
                  :disabled="borrowingIds.includes(book.id)"
                  class="btn-borrow"
                >
                  {{ borrowingIds.includes(book.id) ? '借阅中...' : '借书' }}
                </button>
                <button 
                  v-else
                  disabled
                  class="btn-borrow disabled"
                >
                  库存不足
                </button>
                <button 
                  @click="toggleFavorite(book.id)"
                  class="btn-favorite"
                  :class="{ favorited: favoriteIds.includes(book.id) }"
                >
                  {{ favoriteIds.includes(book.id) ? '★ 已收藏' : '☆ 收藏' }}
                </button>
              </template>
            </div>
          </div>
        </div>
      </div>

      <!-- 借阅对话框 -->
      <div v-if="showBorrowDialog" class="modal-overlay" @click="closeBorrowDialog">
        <div class="modal-content" @click.stop>
          <div class="modal-header">
            <h3>借阅图书</h3>
            <button @click="closeBorrowDialog" class="modal-close">×</button>
          </div>
          <div class="modal-body">
            <div v-if="selectedBook" class="book-info-modal">
              <p><strong>书名：</strong>{{ selectedBook.title }}</p>
              <p><strong>作者：</strong>{{ selectedBook.author || '未知' }}</p>
            </div>
            <div class="form-group">
              <label>选择归还日期：</label>
              <input 
                type="date" 
                v-model="returnDate"
                :min="minReturnDate"
                class="date-input"
              />
              <p class="form-hint">默认借阅期限为30天，您可以选择提前归还的日期</p>
            </div>
          </div>
          <div class="modal-footer">
            <button @click="closeBorrowDialog" class="btn-cancel">取消</button>
            <button @click="confirmBorrow" class="btn-confirm" :disabled="!returnDate">确认借阅</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import http from '../lib/http'

const router = useRouter()

const books = ref<any[]>([])
const loading = ref(false)
const searchKeyword = ref('')
const selectedCategory = ref('全部')
const categories = ref(['全部', '历史大类', '小说', '文学', '社交', '心理学', '外国小说'])
const message = ref('')
const messageType = ref<'success' | 'error'>('success')
const borrowingIds = ref<number[]>([])
const favoriteIds = ref<number[]>([])
const subscriptionIds = ref<number[]>([]) // 已订阅的图书ID列表
const failedImages = ref<Set<string>>(new Set()) // 记录加载失败的图片ISBN
const showBorrowDialog = ref(false)
const selectedBook = ref<any>(null)
const returnDate = ref('')

let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null

watch(searchKeyword, () => {
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(() => {
    searchDebounceTimer = null
    loadBooks()
  }, 320)
})

function getStatusText(book: any): string {
  if (!book.isBorrowable) return '不可借阅'
  if (!book.stock || book.stock === 0) return '库存不足'
  return '书籍已上架'
}

function getStatusClass(book: any): string {
  if (!book.isBorrowable) return 'status-unavailable'
  if (!book.stock || book.stock === 0) return 'status-out-of-stock'
  return 'status-available'
}

async function loadBooks() {
  loading.value = true
  try {
    const params: any = {}
    const kw = searchKeyword.value.trim()
    if (kw) {
      params.keyword = kw
    }
    if (selectedCategory.value && selectedCategory.value !== '全部') {
      params.category = selectedCategory.value
    }
    
    const res = await http.get('/api/books', { params })
    books.value = res.data || []
    
    // 加载收藏列表和订阅列表（如果已登录）
    await loadFavorites()
    await loadSubscriptions()
  } catch (e: any) {
    console.error('加载图书失败', e)
    showMessage('加载图书失败：' + (e?.response?.data?.message || e?.message || '未知错误'), 'error')
  } finally {
    loading.value = false
  }
}

// 加载收藏列表
async function loadFavorites() {
  try {
    // 检查是否已登录
    const { useAuthStore } = await import('../stores/auth')
    const auth = useAuthStore()
    if (!auth.isAuthenticated) {
      favoriteIds.value = []
      return
    }
    
    const res = await http.get('/api/favorites/ids')
    favoriteIds.value = res.data || []
  } catch (e: any) {
    // 如果未登录（401）或权限不足（403），静默处理（不显示错误）
    if (e?.response?.status === 401 || e?.response?.status === 403) {
      console.debug('获取收藏列表失败（未登录或权限不足）:', e)
    } else {
      console.error('获取收藏列表失败:', e)
    }
    favoriteIds.value = []
  }
}

// 加载订阅列表
async function loadSubscriptions() {
  try {
    // 检查是否已登录
    const { useAuthStore } = await import('../stores/auth')
    const auth = useAuthStore()
    if (!auth.isAuthenticated) {
      subscriptionIds.value = []
      return
    }
    
    const res = await http.get('/api/subscriptions/ids')
    subscriptionIds.value = res.data || []
  } catch (e: any) {
    // 如果未登录（401）或权限不足（403），静默处理（不显示错误）
    if (e?.response?.status === 401 || e?.response?.status === 403) {
      console.debug('获取订阅列表失败（未登录或权限不足）:', e)
    } else {
      console.error('获取订阅列表失败:', e)
    }
    subscriptionIds.value = []
  }
}

// 订阅/取消订阅
async function toggleSubscription(bookId: number) {
  try {
    // 检查是否已登录
    const { useAuthStore } = await import('../stores/auth')
    const auth = useAuthStore()
    if (!auth.isAuthenticated || !auth.token) {
      showMessage('请先登录后再使用订阅功能', 'error')
      const router = (await import('../router')).default
      router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
      return
    }
    
    const index = subscriptionIds.value.indexOf(bookId)
    if (index > -1) {
      // 取消订阅
      await http.delete(`/api/subscriptions/${bookId}`)
      subscriptionIds.value.splice(index, 1)
      showMessage('已取消订阅', 'success')
    } else {
      // 添加订阅
      await http.post('/api/subscriptions', { bookId })
      subscriptionIds.value.push(bookId)
      showMessage('订阅成功，图书上架后会通知您', 'success')
    }
  } catch (error: any) {
    console.error('订阅操作失败:', error)
    let message = '操作失败，请稍后重试'
    if (error.response?.status === 401) {
      message = '登录已过期，请重新登录'
      const router = (await import('../router')).default
      router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    } else if (error.response?.status === 403) {
      message = '权限不足，请确认已登录或联系管理员'
    } else {
      message = error.response?.data?.message || error.message || message
    }
    showMessage(message, 'error')
  }
}

// 查看图书详情
function viewBookDetail(bookId: number) {
  router.push({ name: 'bookDetail', params: { id: bookId } })
}

// 打开借阅对话框
function openBorrowDialog(book: any) {
  selectedBook.value = book
  // 设置默认归还日期为30天后
  const defaultDate = new Date()
  defaultDate.setDate(defaultDate.getDate() + 30)
  returnDate.value = defaultDate.toISOString().split('T')[0]
  showBorrowDialog.value = true
}

// 关闭借阅对话框
function closeBorrowDialog() {
  showBorrowDialog.value = false
  selectedBook.value = null
  returnDate.value = ''
}

// 确认借阅
async function confirmBorrow() {
  if (!selectedBook.value || !returnDate.value) {
    showMessage('请选择归还日期', 'error')
    return
  }
  
  const bookId = selectedBook.value.id
  if (borrowingIds.value.includes(bookId)) return
  
  borrowingIds.value.push(bookId)
  try {
    // 将日期转换为本地日期时间字符串（设置为当天的23:59:59）
    // 格式：yyyy-MM-ddTHH:mm:ss（不包含时区信息）
    const dueDateTime = returnDate.value + 'T23:59:59'
    
    await http.post('/api/borrows', { 
      bookId,
      dueDate: dueDateTime
    })
    showMessage('借阅成功！', 'success')
    closeBorrowDialog()
    // 重新加载图书列表以更新库存
    await loadBooks()
  } catch (e: any) {
    showMessage('借阅失败：' + (e?.response?.data?.message || e?.message || '未知错误'), 'error')
  } finally {
    borrowingIds.value = borrowingIds.value.filter(id => id !== bookId)
  }
}

// 计算最小归还日期（明天）
const minReturnDate = computed(() => {
  const tomorrow = new Date()
  tomorrow.setDate(tomorrow.getDate() + 1)
  return tomorrow.toISOString().split('T')[0]
})

async function toggleFavorite(bookId: number) {
  try {
    // 检查是否已登录
    const { useAuthStore } = await import('../stores/auth')
    const auth = useAuthStore()
    if (!auth.isAuthenticated || !auth.token) {
      showMessage('请先登录后再使用收藏功能', 'error')
      const router = (await import('../router')).default
      router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
      return
    }
    
    const index = favoriteIds.value.indexOf(bookId)
    if (index > -1) {
      // 取消收藏
      await http.delete(`/api/favorites/${bookId}`)
      favoriteIds.value.splice(index, 1)
      showMessage('已取消收藏', 'success')
      // 重新加载收藏列表
      await loadFavorites()
    } else {
      // 添加收藏
      await http.post('/api/favorites', { bookId })
      favoriteIds.value.push(bookId)
      showMessage('收藏成功', 'success')
    }
  } catch (error: any) {
    console.error('收藏操作失败:', error)
    let message = '操作失败，请稍后重试'
    if (error.response?.status === 401) {
      message = '登录已过期，请重新登录'
      const router = (await import('../router')).default
      router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    } else if (error.response?.status === 403) {
      message = '权限不足，请确认已登录或联系管理员'
    } else {
      message = error.response?.data?.message || error.message || message
    }
    showMessage(message, 'error')
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

// 根据书名生成图书封面URL（图片文件名是中文书名）
function getBookCoverUrl(book: any): string {
  if (!book.title) return ''
  const baseUrl = '/uploads/book-covers'
  
  // 根据书名构建图片路径（文件名就是书名.jpg）
  // 处理特殊字符：
  // 1. 简·爱 -> 简.爱（文件名中的点）
  // 2. 围城 -> 微成（文件名可能是误写）
  let filename = book.title.replace(/·/g, '.').trim()
  
  // 特殊处理：围城 -> 微成
  if (filename === '围城') {
    filename = '微成'
  }
  
  // Spring Boot会自动处理中文URL，直接使用文件名即可
  // 默认使用jpg格式
  return `${baseUrl}/${filename}.jpg`
}

// 检查图片是否加载失败
function isImageFailed(book: any): boolean {
  if (!book.title) return true
  // 使用书名作为key（处理特殊字符）
  const titleKey = book.title.replace(/·/g, '.').trim()
  return failedImages.value.has(titleKey)
}

// 处理图片加载失败
function handleImageError(event: Event, book: any) {
  const img = event.target as HTMLImageElement
  if (!img || !book.title) {
    return
  }
  
  const titleKey = book.title.replace(/·/g, '.').trim()
  let currentSrc = img.src
  
  // 提取基础路径（不含扩展名）
  const baseUrl = '/uploads/book-covers'
  let filename = book.title.replace(/·/g, '.').trim()
  if (filename === '围城') {
    filename = '微成'
  }
  
  // 尝试其他格式：jpg -> png -> jpeg -> webp
  if (currentSrc.includes('.jpg')) {
    // 尝试 png
    img.src = `${baseUrl}/${filename}.png`
    return
  }
  if (currentSrc.includes('.png')) {
    // 尝试 jpeg
    img.src = `${baseUrl}/${filename}.jpeg`
    return
  }
  if (currentSrc.includes('.jpeg')) {
    // 尝试 webp
    img.src = `${baseUrl}/${filename}.webp`
    return
  }
  if (currentSrc.includes('.webp')) {
    // 所有格式都失败，标记为失败并隐藏图片
    failedImages.value.add(titleKey)
    img.style.display = 'none'
    return
  }
  
  // 如果都不是，标记为失败
  failedImages.value.add(titleKey)
  img.style.display = 'none'
}

onMounted(() => {
  loadBooks()
})

onBeforeUnmount(() => {
  if (searchDebounceTimer) {
    clearTimeout(searchDebounceTimer)
    searchDebounceTimer = null
  }
})
</script>

<style scoped src="../styles/views/BookOperation.css"></style>
