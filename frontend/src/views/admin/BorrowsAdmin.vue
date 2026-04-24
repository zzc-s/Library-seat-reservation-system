<template>
  <section class="admin-page">
    <div class="page-header">
      <h2>借阅管理</h2>
      <div class="search-section">
        <div class="search-box">
          <span class="search-icon">🔍</span>
          <input 
            type="text" 
            v-model="searchKeyword" 
            @input="handleSearch"
            placeholder="搜索用户名、图书名称、作者或用户ID..." 
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
          找到 {{ filteredBorrows.length }} 条相关记录
        </div>
      </div>
      <div class="filter-section">
        <button 
          v-for="filter in filters" 
          :key="filter.key"
          :class="['filter-btn', { active: activeFilter === filter.key }]"
          @click="activeFilter = filter.key"
        >
          {{ filter.label }}
        </button>
      </div>
    </div>
    
    <div v-if="message" :class="['message', messageType]">
      <span>{{ message }}</span>
      <button @click="message = ''" class="close-btn">×</button>
    </div>
    
    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="filteredBorrows.length === 0" class="empty-state">
      {{ searchKeyword ? '未找到相关借阅记录' : '暂无借阅记录' }}
    </div>
    <div v-else class="admin-list">
      <div v-for="borrow in filteredBorrows" :key="borrow.id" class="admin-item" :class="borrow.status">
        <div class="item-content">
          <div class="item-header">
            <h3 v-html="highlightKeyword(borrow.bookTitle)"></h3>
            <span class="status" :class="borrow.status">{{ getStatusText(borrow.status) }}</span>
          </div>
          <div class="item-info">
            <p><strong>作者：</strong><span v-html="highlightKeyword(borrow.bookAuthor)"></span></p>
            <p><strong>借阅人：</strong><span v-html="highlightKeyword(borrow.username || `用户ID: ${borrow.userId}`)"></span></p>
            <p><strong>借阅日期：</strong>{{ formatDateTime(borrow.borrowDate) }}</p>
            <p v-if="borrow.returnDate"><strong>归还日期：</strong>{{ formatDateTime(borrow.returnDate) }}</p>
            <p v-if="borrow.dueDate">
              <strong>应还日期：</strong>{{ formatDateTime(borrow.dueDate) }}
              <span v-if="isOverdue(borrow)" class="overdue-badge">已逾期 {{ getOverdueDays(borrow) }} 天</span>
            </p>
            <div v-if="borrow.warningCount > 0" class="warning-info">
              <span class="warning-badge">⚠️ 警告次数：{{ borrow.warningCount }}/3</span>
              <span v-if="borrow.lastWarningAt" class="warning-time">
                最后警告：{{ formatDateTime(borrow.lastWarningAt) }}
              </span>
            </div>
          </div>
          <div class="item-actions">
            <button 
              v-if="borrow.status === 'BORROWED' || borrow.status === 'OVERDUE' || isOverdue(borrow)" 
              @click="returnBook(borrow)"
              class="btn-return"
              :disabled="returning"
            >
              归还图书
            </button>
            <button 
              v-if="borrow.status === 'OVERDUE' || isOverdue(borrow)" 
              @click="warnUser(borrow)"
              class="btn-warn"
              :disabled="warning"
            >
              {{ borrow.warningCount >= 3 ? '已冻结' : `发送警告 (${borrow.warningCount || 0}/3)` }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import http from '../../lib/http'

const borrows = ref<any[]>([])
const loading = ref(true)
const activeFilter = ref<'all' | 'overdue' | 'borrowed' | 'returned'>('all')
const searchKeyword = ref('')
const message = ref('')
const messageType = ref<'success' | 'error'>('success')
const warning = ref(false)
const returning = ref(false)

// 筛选选项
const filters = [
  { key: 'all', label: '全部' },
  { key: 'overdue', label: '逾期' },
  { key: 'borrowed', label: '借阅中' },
  { key: 'returned', label: '已归还' }
]

// 筛选后的借阅列表（先按状态筛选，再按搜索关键词筛选）
const filteredBorrows = computed(() => {
  let result = borrows.value
  
  // 先按状态筛选
  if (activeFilter.value === 'overdue') {
    result = result.filter(b => b.status === 'OVERDUE' || isOverdue(b))
  } else if (activeFilter.value === 'borrowed') {
    result = result.filter(b => b.status === 'BORROWED')
  } else if (activeFilter.value === 'returned') {
    result = result.filter(b => b.status === 'RETURNED')
  }
  
  // 再按搜索关键词筛选
  if (searchKeyword.value.trim()) {
    const keyword = searchKeyword.value.trim().toLowerCase()
    result = result.filter(borrow => {
      // 搜索用户名
      const username = (borrow.username || '').toLowerCase()
      // 搜索用户ID
      const userId = String(borrow.userId || '')
      // 搜索图书名称
      const bookTitle = (borrow.bookTitle || '').toLowerCase()
      // 搜索作者
      const bookAuthor = (borrow.bookAuthor || '').toLowerCase()
      
      return username.includes(keyword) || 
             userId.includes(keyword) || 
             bookTitle.includes(keyword) || 
             bookAuthor.includes(keyword)
    })
  }
  
  return result
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

function getStatusText(status: string): string {
  const statusMap: Record<string, string> = {
    'BORROWED': '借阅中',
    'RETURNED': '已归还',
    'OVERDUE': '逾期'
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

// 判断是否逾期
function isOverdue(borrow: any): boolean {
  if (borrow.status === 'RETURNED') return false
  if (!borrow.dueDate) return false
  const dueDate = new Date(borrow.dueDate)
  const now = new Date()
  return dueDate < now
}

// 获取逾期天数
function getOverdueDays(borrow: any): number {
  if (!borrow.dueDate) return 0
  const dueDate = new Date(borrow.dueDate)
  const now = new Date()
  const diffTime = now.getTime() - dueDate.getTime()
  const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24))
  return diffDays > 0 ? diffDays : 0
}

// 归还图书（管理员）
async function returnBook(borrow: any) {
  if (borrow.status === 'RETURNED') {
    showMessage('该图书已归还', 'error')
    return
  }
  
  if (!confirm(`确认归还图书《${borrow.bookTitle}》？\n\n借阅人：${borrow.username || borrow.userId}`)) {
    return
  }
  
  returning.value = true
  try {
    await http.post(`/api/borrows/${borrow.id}/return`)
    showMessage('归还成功', 'success')
    await loadBorrows()
  } catch (e: any) {
    console.error('归还图书失败', e)
    showMessage(e?.response?.data?.message || '归还失败', 'error')
  } finally {
    returning.value = false
  }
}

// 警告用户
async function warnUser(borrow: any) {
  if (borrow.warningCount >= 3) {
    showMessage('该用户已收到3次警告，账号已冻结', 'error')
    return
  }
  
  if (!confirm(`确定要向用户 ${borrow.username || borrow.userId} 发送逾期警告吗？\n\n当前警告次数：${borrow.warningCount || 0}/3`)) {
    return
  }
  
  warning.value = true
  try {
    const res = await http.post(`/api/borrows/${borrow.id}/warn`)
    const data = res.data
    if (data.accountFrozen) {
      showMessage(`已发送第${data.warningCount}次警告，账号已自动冻结`, 'error')
    } else {
      showMessage(`已发送第${data.warningCount}次警告`, 'success')
    }
    await loadBorrows()
  } catch (e: any) {
    console.error('发送警告失败', e)
    showMessage(e?.response?.data?.message || '发送警告失败', 'error')
  } finally {
    warning.value = false
  }
}

function showMessage(msg: string, type: 'success' | 'error' = 'success') {
  message.value = msg
  messageType.value = type
  setTimeout(() => {
    message.value = ''
  }, 3000)
}

async function loadBorrows() {
  loading.value = true
  try {
    const res = await http.get('/api/borrows')
    borrows.value = res.data || []
  } catch (e: any) {
    console.error('加载借阅记录失败', e)
    showMessage('加载借阅记录失败', 'error')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadBorrows()
})
</script>

<style scoped src="../../styles/views/admin/BorrowsAdmin.css"></style>
