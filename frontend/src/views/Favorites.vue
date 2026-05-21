<template>
  <div class="favorites-page">
    <div class="favorites-container">
      <h2 class="page-title">我的收藏</h2>

      <!-- 消息提示 -->
      <div v-if="message" :class="['message', messageType]">
        <span>{{ message }}</span>
        <button @click="closeMessage" class="close-btn">×</button>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading" class="loading">加载中...</div>
      
      <!-- 空状态 -->
      <div v-else-if="favorites.length === 0" class="empty-state">
        <div class="empty-icon">⭐</div>
        <p>还没有收藏任何图书</p>
        <RouterLink to="/book-operation" class="btn-browse">去浏览图书</RouterLink>
      </div>

      <!-- 收藏列表 -->
      <div v-else class="favorites-grid">
        <div 
          v-for="item in favorites" 
          :key="item.id" 
          class="book-card"
        >
          <div class="book-cover">
            <img 
              v-if="item.book && item.book.title && !isImageFailed(item.book)" 
              :src="getBookCoverUrl(item.book)" 
              :alt="item.book.title"
              class="cover-image"
              @error="handleImageError($event, item.book)"
            />
            <div v-if="!item.book || !item.book.title || isImageFailed(item.book)" class="cover-placeholder">📚</div>
          </div>
          <div class="book-info">
            <div class="book-status">
              <span :class="['status-badge', getStatusClass(item.book)]">
                {{ getStatusText(item.book) }}
              </span>
            </div>
            <h3 class="book-title">{{ item.book?.title || '未知图书' }}</h3>
            <p class="book-author">作者：{{ item.book?.author || '未知' }}</p>
            <p class="book-publisher" v-if="item.book?.publisher">出版社：{{ item.book.publisher }}</p>
            <p class="book-stock">
              <span :class="{ 'stock-zero': !item.book?.stock || item.book.stock === 0 }">
                库存{{ item.book?.stock || 0 }}本
              </span>
            </p>
            <p class="favorite-date">收藏时间：{{ formatDateTime(item.createdAt) }}</p>
            <div class="book-actions">
              <button 
                v-if="item.book && item.book.isBorrowable && item.book.stock > 0"
                @click="borrowBook(item.book.id)"
                :disabled="borrowingIds.includes(item.book.id)"
                class="btn-borrow"
              >
                {{ borrowingIds.includes(item.book.id) ? '借阅中...' : '借书' }}
              </button>
              <button 
                v-else-if="item.book && !item.book.isBorrowable"
                disabled
                class="btn-borrow disabled"
              >
                不可借阅
              </button>
              <button 
                v-else-if="item.book && (!item.book.stock || item.book.stock === 0)"
                disabled
                class="btn-borrow disabled"
              >
                库存不足
              </button>
              <button 
                @click="removeFavorite(item.bookId)"
                class="btn-remove-favorite"
              >
                ❌ 取消收藏
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import http from '../lib/http'

const router = useRouter()
const favorites = ref<any[]>([])
const loading = ref(false)
const message = ref('')
const messageType = ref<'success' | 'error'>('success')
const borrowingIds = ref<number[]>([])
const failedImages = ref<Set<string>>(new Set())

function getStatusText(book: any): string {
  if (!book) return '未知'
  if (!book.isBorrowable) return '不可借阅'
  if (!book.stock || book.stock === 0) return '库存不足'
  return '可借阅'
}

function getStatusClass(book: any): string {
  if (!book) return 'status-unavailable'
  if (!book.isBorrowable) return 'status-unavailable'
  if (!book.stock || book.stock === 0) return 'status-out-of-stock'
  return 'status-available'
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

// 根据书名生成图书封面URL
function getBookCoverUrl(book: any): string {
  if (!book || !book.title) return ''
  const baseUrl = 'http://localhost:8081/uploads/book-covers'
  let filename = book.title.replace(/·/g, '.').trim()
  if (filename === '围城') {
    filename = '微成'
  }
  return `${baseUrl}/${filename}.jpg`
}

// 检查图片是否加载失败
function isImageFailed(book: any): boolean {
  if (!book || !book.title) return true
  const titleKey = book.title.replace(/·/g, '.').trim()
  return failedImages.value.has(titleKey)
}

// 处理图片加载失败
function handleImageError(event: Event, book: any) {
  const img = event.target as HTMLImageElement
  if (!img || !book || !book.title) {
    return
  }
  
  const titleKey = book.title.replace(/·/g, '.').trim()
  let currentSrc = img.src
  
  const baseUrl = 'http://localhost:8081/uploads/book-covers'
  let filename = book.title.replace(/·/g, '.').trim()
  if (filename === '围城') {
    filename = '微成'
  }
  
  // 尝试其他格式：jpg -> png -> jpeg -> webp
  if (currentSrc.includes('.jpg')) {
    img.src = `${baseUrl}/${filename}.png`
    return
  }
  if (currentSrc.includes('.png')) {
    img.src = `${baseUrl}/${filename}.jpeg`
    return
  }
  if (currentSrc.includes('.jpeg')) {
    img.src = `${baseUrl}/${filename}.webp`
    return
  }
  if (currentSrc.includes('.webp')) {
    failedImages.value.add(titleKey)
    img.style.display = 'none'
    return
  }
  
  failedImages.value.add(titleKey)
  img.style.display = 'none'
}

async function loadFavorites() {
  loading.value = true
  try {
    const res = await http.get('/api/favorites')
    favorites.value = res.data || []
  } catch (e: any) {
    console.error('加载收藏列表失败', e)
    showMessage('加载收藏列表失败：' + (e?.response?.data?.message || e?.message || '未知错误'), 'error')
  } finally {
    loading.value = false
  }
}

async function removeFavorite(bookId: number) {
  if (!confirm('确认取消收藏该图书？')) return
  
  try {
    await http.delete(`/api/favorites/${bookId}`)
    showMessage('取消收藏成功', 'success')
    // 重新加载收藏列表
    await loadFavorites()
  } catch (e: any) {
    showMessage('取消收藏失败：' + (e?.response?.data?.message || e?.message || '未知错误'), 'error')
  }
}

async function borrowBook(bookId: number) {
  if (borrowingIds.value.includes(bookId)) return
  
  borrowingIds.value.push(bookId)
  try {
    await http.post('/api/borrows', { bookId })
    showMessage('借阅成功！', 'success')
    // 重新加载收藏列表以更新库存
    await loadFavorites()
  } catch (e: any) {
    showMessage('借阅失败：' + (e?.response?.data?.message || e?.message || '未知错误'), 'error')
  } finally {
    borrowingIds.value = borrowingIds.value.filter(id => id !== bookId)
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
  loadFavorites()
})
</script>

<style scoped src="../styles/views/Favorites.css"></style>
