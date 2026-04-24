<template>
  <section class="borrows-page">
    <div class="borrows-container">
      <h2>图书借阅</h2>
      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="borrows.length === 0" class="empty-state">暂无借阅记录</div>
      <div v-else class="borrows-list">
        <div 
          v-for="borrow in borrows" 
          :key="borrow.id" 
          class="borrow-item"
          :class="borrow.status"
        >
          <div class="borrow-header">
            <h3>{{ borrow.bookTitle }}</h3>
            <span class="borrow-status">{{ getStatusText(borrow.status) }}</span>
          </div>
          <div class="borrow-info">
            <p><strong>作者：</strong>{{ borrow.bookAuthor }}</p>
            <p><strong>借阅日期：</strong>{{ formatDateTime(borrow.borrowDate) }}</p>
            <p v-if="borrow.returnDate"><strong>归还日期：</strong>{{ formatDateTime(borrow.returnDate) }}</p>
            <p v-if="borrow.dueDate && borrow.status === 'BORROWED'">
              <strong>应还日期：</strong>
              <span :class="{ overdue: isOverdue(borrow.dueDate) }">
                {{ formatDateTime(borrow.dueDate) }}
              </span>
            </p>
            <p v-if="borrow.status === 'BORROWED'" class="return-notice">
              <strong>提示：</strong>请将图书归还给管理员，由管理员处理归还手续
            </p>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import http from '../lib/http'

const borrows = ref<any[]>([])
const loading = ref(true)

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

function isOverdue(dueDate: string): boolean {
  if (!dueDate) return false
  return new Date(dueDate) < new Date()
}

async function loadBorrows() {
  loading.value = true
  try {
    const res = await http.get('/api/borrows/my')
    borrows.value = res.data || []
  } catch (e: any) {
    console.error('加载借阅记录失败', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadBorrows()
})
</script>

<style scoped src="../styles/views/Borrows.css"></style>
