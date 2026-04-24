<template>
  <div class="book-detail">
    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <div v-else-if="book" class="book-content">
      <!-- 返回按钮 -->
      <button @click="goBack" class="back-btn">← 返回</button>

      <!-- 图书信息卡片 -->
      <div class="book-card">
        <div class="book-header">
          <h1 class="book-title">{{ book.title }}</h1>
        </div>

        <div class="book-body">
          <!-- 左侧：图书封面 -->
          <div class="book-cover-section">
            <div class="book-cover-wrapper">
              <img 
                :src="getBookImage(book.title)" 
                :alt="book.title" 
                class="book-cover"
                @error="handleImageError"
              />
            </div>
          </div>

          <!-- 右侧：图书信息 -->
          <div class="book-info-section">
            <div class="book-meta">
              <div class="meta-item">
                <span class="meta-label">作者：</span>
                <span class="meta-value">{{ book.author || '未知' }}</span>
              </div>
              <div class="meta-item" v-if="book.isbn">
                <span class="meta-label">ISBN：</span>
                <span class="meta-value">{{ book.isbn }}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">可借阅：</span>
                <span class="meta-value">{{ book.isBorrowable ? '是' : '否' }}</span>
              </div>
              <div class="meta-item" v-if="book.hotScore !== null && book.hotScore !== undefined">
                <span class="meta-label">热度：</span>
                <span class="meta-value">{{ book.hotScore }}分</span>
              </div>
              <div class="meta-item" v-if="book.publisher">
                <span class="meta-label">出版社：</span>
                <span class="meta-value">{{ book.publisher }}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">库存：</span>
                <span class="meta-value" :class="{ 'stock-zero': !book.stock || book.stock === 0 }">
                  {{ book.stock || 0 }}本
                </span>
              </div>
            </div>

            <!-- 操作按钮 -->
            <div class="book-actions">
              <button 
                v-if="book.isBorrowable && book.stock > 0"
                @click="openBorrowDialog"
                class="btn-borrow"
                :disabled="borrowing"
              >
                {{ borrowing ? '借阅中...' : '借阅图书' }}
              </button>
              <button 
                v-else-if="book.isBorrowable && (!book.stock || book.stock === 0)"
                disabled
                class="btn-borrow disabled"
              >
                库存不足
              </button>
              <button 
                v-else
                disabled
                class="btn-borrow disabled"
              >
                不可借阅
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 内容概述 -->
      <div class="book-section">
        <h2 class="section-title">内容概述</h2>
        <div class="section-content">
          <p class="book-description">{{ book.description || '暂无内容简介' }}</p>
        </div>
      </div>

      <!-- 借阅详情 -->
      <div class="book-section" v-if="book.isBorrowable">
        <h2 class="section-title">借阅信息</h2>
        <div class="section-content">
          <div class="borrow-info">
            <p><strong>借阅期限：</strong>默认30天，您可以在借阅时选择归还日期</p>
            <p><strong>库存状态：</strong>
              <span :class="{ 'stock-zero': !book.stock || book.stock === 0 }">
                {{ book.stock || 0 }}本可借
              </span>
            </p>
            <p v-if="book.stock && book.stock > 0"><strong>借阅说明：</strong>点击"借阅图书"按钮，选择归还日期后即可完成借阅</p>
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
          <div v-if="book" class="book-info-modal">
            <p><strong>书名：</strong>{{ book.title }}</p>
            <p><strong>作者：</strong>{{ book.author || '未知' }}</p>
            <p v-if="book.publisher"><strong>出版社：</strong>{{ book.publisher }}</p>
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
          <button @click="confirmBorrow" class="btn-confirm" :disabled="!returnDate || borrowing">确认借阅</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import http from '../lib/http'

interface Book {
  id: number
  title: string
  author: string
  isbn?: string
  isBorrowable?: boolean
  hotScore?: number
  publisher?: string
  stock?: number
  description?: string
}

const route = useRoute()
const router = useRouter()

const book = ref<Book | null>(null)
const loading = ref(true)
const error = ref('')
const showBorrowDialog = ref(false)
const returnDate = ref('')
const borrowing = ref(false)

// 计算最小归还日期（明天）
const minReturnDate = computed(() => {
  const tomorrow = new Date()
  tomorrow.setDate(tomorrow.getDate() + 1)
  return tomorrow.toISOString().split('T')[0]
})

// 根据标题获取图书图片
function getBookImage(title: string): string {
  const imageMap: Record<string, string> = {
    '红楼梦': '/images/carousel/carousel-1.jpg',
    '小王子': '/images/carousel/carousel-2.jpg',
    '百年孤独': '/images/carousel/carousel-3.jpg',
    '活着': '/images/carousel/carousel-4.jpg',
    '老人与海': '/images/carousel/carousel-5.jpg'
  }
  return imageMap[title] || '/images/carousel/carousel-1.jpg'
}


function handleImageError(e: Event) {
  const img = e.target as HTMLImageElement
  img.src = '/images/carousel/carousel-1.jpg'
}

async function loadBook() {
  try {
    loading.value = true
    error.value = ''
    
    const bookId = route.params.id as string
    const title = route.query.title as string
    
    if (bookId && bookId !== 'undefined') {
      // 根据ID获取
      const res = await http.get(`/api/books/${bookId}`)
      book.value = res.data
    } else if (title) {
      // 根据标题获取
      const res = await http.get(`/api/books/by-title?title=${encodeURIComponent(title)}`)
      book.value = res.data
    } else {
      error.value = '缺少图书ID或标题参数'
    }
  } catch (e: any) {
    console.error('加载图书详情失败', e)
    error.value = e.response?.data?.message || '加载图书详情失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.back()
}

// 打开借阅对话框
function openBorrowDialog() {
  if (!book.value) return
  // 设置默认归还日期为30天后
  const defaultDate = new Date()
  defaultDate.setDate(defaultDate.getDate() + 30)
  returnDate.value = defaultDate.toISOString().split('T')[0]
  showBorrowDialog.value = true
}

// 关闭借阅对话框
function closeBorrowDialog() {
  showBorrowDialog.value = false
  returnDate.value = ''
}

// 确认借阅
async function confirmBorrow() {
  if (!book.value || !returnDate.value) {
    alert('请选择归还日期')
    return
  }
  
  borrowing.value = true
  try {
    // 将日期转换为本地日期时间字符串（设置为当天的23:59:59）
    // 格式：yyyy-MM-ddTHH:mm:ss（不包含时区信息）
    const dueDateTime = returnDate.value + 'T23:59:59'
    
    await http.post('/api/borrows', { 
      bookId: book.value.id,
      dueDate: dueDateTime
    })
    alert('借阅成功！')
    closeBorrowDialog()
    // 重新加载图书信息以更新库存
    await loadBook()
  } catch (e: any) {
    alert('借阅失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  } finally {
    borrowing.value = false
  }
}

onMounted(() => {
  loadBook()
})
</script>

<style scoped src="../styles/views/BookDetail.css"></style>

