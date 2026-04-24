<template>
  <div class="home">
    <!-- 图书轮播图 -->
    <div class="carousel-container">
      <div class="carousel-wrapper" @mouseenter="pauseCarousel" @mouseleave="resumeCarousel">
        <div 
          class="carousel-slide" 
          v-for="(item, index) in carouselItems" 
          :key="index"
          :class="{ active: currentIndex === index }"
          @click="goToBookDetail(item)"
          style="cursor: pointer;"
        >
          <img :src="item.image" :alt="item.title" class="carousel-image" />
          <div class="carousel-overlay">
            <h2 class="carousel-title">{{ item.title }}</h2>
            <p class="carousel-subtitle">{{ item.subtitle }}</p>
          </div>
        </div>
      </div>
      
      <!-- 轮播图指示器 -->
      <div class="carousel-indicators">
        <span 
          v-for="(item, index) in carouselItems" 
          :key="index"
          :class="{ active: currentIndex === index }"
          @click="goToSlide(index)"
        ></span>
      </div>
      
      <!-- 左右切换按钮 -->
      <button class="carousel-btn carousel-prev" @click="prevSlide">‹</button>
      <button class="carousel-btn carousel-next" @click="nextSlide">›</button>
    </div>

    <!-- 公告通知区域 -->
    <div class="notices-section" v-if="notices.length > 0">
      <div class="section-header">
        <h2>📢 最新公告</h2>
        <RouterLink to="/notices" class="view-all-link">查看全部 →</RouterLink>
      </div>
      <div class="notices-grid">
        <div 
          v-for="notice in displayedNotices" 
          :key="notice.id" 
          class="notice-card"
          :class="notice.type"
          @click="goToNotices"
        >
          <div class="notice-card-header">
            <h3>{{ notice.title }}</h3>
            <span class="notice-badge" :class="notice.type">{{ getTypeText(notice.type) }}</span>
          </div>
          <p class="notice-preview">{{ getPreviewText(notice.content) }}</p>
          <span class="notice-date">{{ formatDate(notice.createdAt) }}</span>
        </div>
      </div>
    </div>

    <!-- 功能导航卡片 -->
    <div class="features">
      <RouterLink to="/seats-map" class="feature-card">
        <div class="icon">🗺️</div>
        <h3>可视化选座</h3>
        <p>图形化浏览座位，直观选择心仪位置进行预约</p>
      </RouterLink>

      <RouterLink to="/my-reservations" class="feature-card">
        <div class="icon">📋</div>
        <h3>我的预约</h3>
        <p>查看和管理您的所有预约记录</p>
      </RouterLink>

      <RouterLink to="/notices" class="feature-card">
        <div class="icon">📢</div>
        <h3>公告通知</h3>
        <p>查看最新公告和重要通知</p>
      </RouterLink>
    </div>

    <!-- 系统简介 -->
    <div class="intro">
      <h2>系统特色</h2>
      <div class="intro-grid">
        <div class="intro-item">
          <strong>条件筛选</strong>
          <p>按区域、电源、靠窗等条件筛选，平面图直观选座</p>
        </div>
        <div class="intro-item">
          <strong>实时状态</strong>
          <p>实时显示座位占用情况，避免冲突</p>
        </div>
        <div class="intro-item">
          <strong>灵活预约</strong>
          <p>支持单座和成组预约，满足不同需求</p>
        </div>
        <div class="intro-item">
          <strong>安全可靠</strong>
          <p>JWT 认证 + RBAC 权限控制，保障数据安全</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import http from '../lib/http'

const router = useRouter()
const notices = ref<any[]>([])

// 轮播图数据（基于数据库中的实际图书）
const carouselItems = ref([
  {
    id: null as number | null,
    image: '/images/carousel/carousel-1.jpg',
    title: '红楼梦',
    subtitle: '中国古典四大名著之一，见证生命的绽放'
  },
  {
    id: null as number | null,
    image: '/images/carousel/carousel-2.jpg',
    title: '小王子',
    subtitle: '安托万·德·圣埃克苏佩里经典之作，童话世界的奇妙之旅'
  },
  {
    id: null as number | null,
    image: '/images/carousel/carousel-3.jpg',
    title: '百年孤独',
    subtitle: '加西亚·马尔克斯魔幻现实主义经典，感受时间的流转'
  },
  {
    id: null as number | null,
    image: '/images/carousel/carousel-4.jpg',
    title: '活着',
    subtitle: '余华代表作，生命的意义与坚韧'
  },
  {
    id: null as number | null,
    image: '/images/carousel/carousel-5.jpg',
    title: '老人与海',
    subtitle: '海明威经典之作，永不言败的精神'
  }
])

// 点击轮播图跳转到图书详情
function goToBookDetail(item: typeof carouselItems.value[0]) {
  if (item.id) {
    router.push({ name: 'bookDetail', params: { id: item.id } })
  } else {
    // 如果没有ID，使用标题查询
    router.push({ name: 'bookDetail', params: { id: '0' }, query: { title: item.title } })
  }
}

// 加载图书ID（从数据库获取）
async function loadBookIds() {
  try {
    const res = await http.get('/api/books')
    const books = res.data || []
    
    carouselItems.value = carouselItems.value.map(item => {
      const book = books.find((b: any) => b.title === item.title)
      return {
        ...item,
        id: book?.id || null
      }
    })
  } catch (e) {
    console.error('加载图书ID失败', e)
  }
}

const currentIndex = ref(0)
let carouselTimer: ReturnType<typeof setInterval> | null = null

// 下一张
function nextSlide() {
  currentIndex.value = (currentIndex.value + 1) % carouselItems.value.length
}

// 上一张
function prevSlide() {
  currentIndex.value = (currentIndex.value - 1 + carouselItems.value.length) % carouselItems.value.length
}

// 跳转到指定幻灯片
function goToSlide(index: number) {
  currentIndex.value = index
}

// 暂停轮播
function pauseCarousel() {
  if (carouselTimer) {
    clearInterval(carouselTimer)
    carouselTimer = null
  }
}

// 恢复轮播
function resumeCarousel() {
  startCarousel()
}

// 开始自动轮播
function startCarousel() {
  if (carouselTimer) {
    clearInterval(carouselTimer)
  }
  carouselTimer = setInterval(() => {
    nextSlide()
  }, 4000) // 每4秒切换一次
}

// 显示的公告（最多3条）
const displayedNotices = computed(() => {
  return notices.value.slice(0, 3)
})

function getTypeText(type: string): string {
  const typeMap: Record<string, string> = {
    'NORMAL': '普通',
    'URGENT': '紧急',
    'CLOSURE': '闭馆通知'
  }
  return typeMap[type] || type
}

function getPreviewText(content: string): string {
  if (!content) return ''
  // 取前80个字符，超出部分用省略号
  return content.length > 80 ? content.substring(0, 80) + '...' : content
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

function goToNotices() {
  router.push('/notices')
}

async function loadNotices() {
  try {
    const res = await http.get('/api/notices/public')
    notices.value = res.data || []
  } catch (e: any) {
    console.error('加载公告失败', e)
    // 静默失败，不影响首页显示
    notices.value = []
  }
}

onMounted(() => {
  startCarousel()
  loadBookIds()
  loadNotices()
})

onUnmounted(() => {
  if (carouselTimer) {
    clearInterval(carouselTimer)
  }
})
</script>

<style scoped src="../styles/views/Home.css"></style>


