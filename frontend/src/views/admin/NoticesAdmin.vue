<template>
  <section class="admin-page">
    <h2>公告</h2>
    <div class="admin-actions">
      <button @click="showCreateModal = true" class="btn-create">创建公告</button>
    </div>
    <div v-if="loading" class="loading">加载中...</div>
    <div v-else class="admin-list">
      <div v-for="notice in notices" :key="notice.id" class="admin-item">
        <div class="item-content">
          <h3>{{ notice.title }}</h3>
          <p>{{ notice.content }}</p>
          <div class="item-meta">
            <span class="type">{{ getTypeText(notice.type) }}</span>
            <span :class="['status', notice.isPublished ? 'published' : 'draft']">
              {{ notice.isPublished ? '已发布' : '草稿' }}
            </span>
            <span class="time">创建：{{ formatDateTime(notice.createdAt) }}</span>
            <span v-if="notice.expiresAt" class="expire">过期：{{ formatDateTime(notice.expiresAt) }}</span>
            <span v-else class="expire muted">永不过期</span>
            <span
              v-if="!notice.isPublished && notice.expiresAt && isPast(notice.expiresAt)"
              class="expire-tag"
            >已到期自动下架</span>
          </div>
        </div>
        <div class="item-actions">
          <button @click="editNotice(notice)">编辑</button>
          <button @click="deleteNotice(notice.id)" class="btn-danger">删除</button>
        </div>
      </div>
    </div>
    
    <!-- 创建/编辑模态框 -->
    <div v-if="showCreateModal || editingNotice" class="modal" @click.self="closeModal">
      <div class="modal-content">
        <h3>{{ editingNotice ? '编辑公告' : '创建公告' }}</h3>
        <form @submit.prevent="saveNotice">
          <label>
            <span>标题</span>
            <input v-model="form.title" required />
          </label>
          <label>
            <span>内容</span>
            <textarea v-model="form.content" required rows="5"></textarea>
          </label>
          <label>
            <span>类型</span>
            <select v-model="form.type" required>
              <option value="NORMAL">普通</option>
              <option value="URGENT">紧急</option>
              <option value="CLOSURE">闭馆通知</option>
            </select>
          </label>
          <label>
            <input type="checkbox" v-model="form.isPublished" />
            <span>立即发布（勾选后普通用户才能看到此公告）</span>
          </label>
          <label class="checkbox-row">
            <input type="checkbox" v-model="form.noExpire" />
            <span>永不过期（到期后系统将自动取消发布）</span>
          </label>
          <label v-if="!form.noExpire">
            <span>过期时间</span>
            <input v-model="form.expiresAtLocal" type="datetime-local" required />
          </label>
          <div class="modal-actions">
            <button type="submit" :disabled="saving">{{ saving ? '保存中...' : '保存' }}</button>
            <button type="button" @click="closeModal">取消</button>
          </div>
        </form>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import http from '../../lib/http'

const notices = ref<any[]>([])
const loading = ref(true)
const showCreateModal = ref(false)
const editingNotice = ref<any>(null)
const saving = ref(false)
const form = ref({
  title: '',
  content: '',
  type: 'NORMAL',
  isPublished: true,
  noExpire: true,
  expiresAtLocal: ''
})

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

function toDatetimeLocalValue(s: string | null | undefined): string {
  if (!s) return ''
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return ''
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${day}T${h}:${min}`
}

function isPast(dateTime: string | null | undefined): boolean {
  if (!dateTime) return false
  const t = new Date(dateTime).getTime()
  if (Number.isNaN(t)) return false
  return t <= Date.now()
}

function editNotice(notice: any) {
  editingNotice.value = notice
  const exp = notice.expiresAt as string | null | undefined
  const hasExp = !!exp
  form.value = {
    title: notice.title,
    content: notice.content,
    type: notice.type,
    isPublished: notice.isPublished,
    noExpire: !hasExp,
    expiresAtLocal: hasExp ? toDatetimeLocalValue(exp) : ''
  }
}

function closeModal() {
  showCreateModal.value = false
  editingNotice.value = null
  form.value = {
    title: '',
    content: '',
    type: 'NORMAL',
    isPublished: true,
    noExpire: true,
    expiresAtLocal: ''
  }
}

async function saveNotice() {
  if (!form.value.noExpire && !String(form.value.expiresAtLocal || '').trim()) {
    alert('未勾选「永不过期」时请填写过期时间')
    return
  }
  const payload = {
    title: form.value.title,
    content: form.value.content,
    type: form.value.type,
    isPublished: form.value.isPublished,
    expiresAt: form.value.noExpire ? null : String(form.value.expiresAtLocal).trim()
  }
  saving.value = true
  try {
    if (editingNotice.value) {
      await http.put(`/api/notices/${editingNotice.value.id}`, payload)
    } else {
      await http.post('/api/notices', payload)
    }
    closeModal()
    loadNotices()
  } catch (e: any) {
    alert(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function deleteNotice(id: number) {
  if (!confirm('确认删除该公告？')) return
  try {
    await http.delete(`/api/notices/${id}`)
    loadNotices()
  } catch (e: any) {
    alert(e?.response?.data?.message || '删除失败')
  }
}

async function loadNotices() {
  loading.value = true
  try {
    const res = await http.get('/api/notices')
    notices.value = res.data || []
  } catch (e: any) {
    console.error('加载公告失败', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadNotices()
})
</script>

<style scoped src="../../styles/views/admin/NoticesAdmin.css"></style>
