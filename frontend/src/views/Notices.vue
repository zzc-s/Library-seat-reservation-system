<template>
  <section class="notices-page">
    <div class="notices-container">
      <h2>公告通知</h2>
      
      <!-- 搜索框 -->
      <div class="search-section">
        <div class="search-box">
          <span class="search-icon">🔍</span>
          <input 
            type="text" 
            v-model="searchKeyword" 
            @input="handleSearch"
            placeholder="搜索公告标题..." 
            class="search-input"
          />
          <button 
            v-if="searchKeyword" 
            @click="clearSearch" 
            class="clear-btn"
            title="清除搜索"
          >
            ×
          </button>
        </div>
        <div v-if="searchKeyword" class="search-result-info">
          找到 {{ filteredNotices.length }} 条相关公告
        </div>
        <div v-else-if="typeFilter !== 'ALL'" class="search-result-info">
          {{ currentTypeLabel }} · 共 {{ filteredNotices.length }} 条
        </div>
      </div>

      <!-- 类型分区 -->
      <div class="type-tabs" role="tablist" aria-label="公告类型">
        <button
          v-for="tab in typeTabs"
          :key="tab.value"
          type="button"
          role="tab"
          :aria-selected="typeFilter === tab.value"
          :class="['type-tab', { active: typeFilter === tab.value }]"
          @click="typeFilter = tab.value"
        >
          {{ tab.label }}
        </button>
      </div>

      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="filteredNotices.length === 0" class="empty-state">
        {{ emptyHint }}
      </div>
      <div v-else class="notices-list">
        <div 
          v-for="notice in filteredNotices" 
          :key="notice.id" 
          class="notice-item"
          :class="notice.type"
        >
          <div class="notice-header">
            <h3 v-html="highlightKeyword(notice.title)"></h3>
            <span class="notice-type">{{ getTypeText(notice.type) }}</span>
          </div>
          <div class="notice-content">{{ notice.content }}</div>
          <div class="notice-time">{{ formatDateTime(notice.createdAt) }}</div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import http from '../lib/http'

type NoticeTypeFilter = 'ALL' | 'URGENT' | 'NORMAL' | 'CLOSURE'

const notices = ref<any[]>([])
const loading = ref(true)
const searchKeyword = ref('')
const typeFilter = ref<NoticeTypeFilter>('ALL')

const typeTabs: { value: NoticeTypeFilter; label: string }[] = [
  { value: 'ALL', label: '全部' },
  { value: 'URGENT', label: '紧急' },
  { value: 'NORMAL', label: '普通' },
  { value: 'CLOSURE', label: '闭馆通知' }
]

const currentTypeLabel = computed(() => {
  const tab = typeTabs.find(t => t.value === typeFilter.value)
  return tab?.label ?? ''
})

// 过滤后的公告列表（先按类型，再按标题关键字）
const filteredNotices = computed(() => {
  let list = notices.value
  if (typeFilter.value !== 'ALL') {
    list = list.filter(n => n.type === typeFilter.value)
  }
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) return list
  return list.filter(notice => notice.title?.toLowerCase().includes(kw))
})

const emptyHint = computed(() => {
  if (searchKeyword.value.trim()) return '未找到相关公告'
  if (typeFilter.value !== 'ALL') return `当前分类（${currentTypeLabel.value}）下暂无公告`
  return '暂无公告'
})

// 处理搜索
function handleSearch() {
  // 搜索逻辑已通过 computed 自动处理
}

// 清除搜索
function clearSearch() {
  searchKeyword.value = ''
}

// 高亮关键字
function highlightKeyword(text: string): string {
  if (!searchKeyword.value.trim() || !text) {
    return text
  }
  // 转义特殊字符，避免正则表达式错误
  const keyword = searchKeyword.value.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const regex = new RegExp(`(${keyword})`, 'gi')
  return text.replace(regex, '<mark>$1</mark>')
}

function getTypeText(type: string): string {
  const typeMap: Record<string, string> = {
    'NORMAL': '普通',
    'URGENT': '紧急',
    'CLOSURE': '闭馆通知'
  }
  return typeMap[type] || type
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

onMounted(async () => {
  try {
    const res = await http.get('/api/notices/public')
    notices.value = res.data || []
  } catch (e: any) {
    console.error('加载公告失败', e)
    // 如果是数据库表不存在，显示友好提示
    if (e?.message?.includes('权限') || e?.response?.status === 403) {
      notices.value = []
    }
  } finally {
    loading.value = false
  }
})
</script>

<style scoped src="../styles/views/Notices.css"></style>
