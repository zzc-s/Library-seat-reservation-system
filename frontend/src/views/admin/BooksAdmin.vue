<template>
  <section class="books-admin">
    <div class="page-header">
      <h2>图书管理</h2>
      <div class="actions">
        <button type="button" @click="showAddForm = true" class="btn-add">+ 添加图书</button>
      </div>
    </div>

    <!-- 搜索和筛选 -->
    <div class="filter-section">
      <input 
        v-model="searchKeyword" 
        type="text" 
        placeholder="搜索图书（书名、作者、ISBN）" 
        class="search-input"
        @input="load"
      />
      <select v-model="filterCategory" class="filter-select" @change="load">
        <option value="">全部分类</option>
        <option value="小说">小说</option>
        <option value="文学">文学</option>
        <option value="历史大类">历史大类</option>
        <option value="外国小说">外国小说</option>
        <option value="科幻小说">科幻小说</option>
        <option value="悬疑小说">悬疑小说</option>
        <option value="未分类">未分类</option>
      </select>
      <select v-model="filterStatus" class="filter-select" @change="load">
        <option value="">全部状态</option>
        <option value="true">已上架</option>
        <option value="false">未上架</option>
      </select>
    </div>

    <!-- 添加/编辑图书表单 -->
    <div v-if="showAddForm || editingBook" class="book-form-overlay" @click.self="closeForm">
      <div class="book-form">
        <div class="form-header">
          <h3>{{ editingBook ? '编辑图书' : '添加图书' }}</h3>
          <button @click="closeForm" class="close-btn">×</button>
        </div>
        <form @submit.prevent="saveBook">
          <div class="form-group">
            <label>书名 <span class="required">*</span></label>
            <input v-model="formData.title" type="text" required class="form-input" />
          </div>
          <div class="form-group">
            <label>作者 <span class="required">*</span></label>
            <input v-model="formData.author" type="text" required class="form-input" />
          </div>
          <div class="form-group">
            <label>ISBN</label>
            <input v-model="formData.isbn" type="text" class="form-input" />
          </div>
          <div class="form-group">
            <label>出版社</label>
            <input v-model="formData.publisher" type="text" class="form-input" />
          </div>
          <div class="form-group">
            <label>分类</label>
            <select v-model="formData.category" class="form-input">
              <option value="">未分类</option>
              <option value="小说">小说</option>
              <option value="文学">文学</option>
              <option value="历史大类">历史大类</option>
              <option value="外国小说">外国小说</option>
              <option value="科幻小说">科幻小说</option>
              <option value="悬疑小说">悬疑小说</option>
            </select>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>库存数量</label>
              <input v-model.number="formData.stock" type="number" min="0" class="form-input" />
            </div>
            <div class="form-group">
              <label>热度评分</label>
              <input v-model.number="formData.hotScore" type="number" min="0" class="form-input" />
            </div>
          </div>
          <div class="form-group">
            <label>
              <input type="checkbox" v-model="formData.isBorrowable" />
              是否上架（可借阅）
            </label>
          </div>
          <div class="form-group">
            <label>封面图片</label>
            <div class="cover-upload-section">
              <div v-if="coverPreview" class="cover-preview">
                <img :src="coverPreview" alt="封面预览" />
                <button type="button" @click="removeCover" class="btn-remove-cover">×</button>
              </div>
              <div v-else class="cover-upload-placeholder">
                <input 
                  ref="coverInput"
                  type="file" 
                  accept="image/*" 
                  @change="handleCoverChange" 
                  style="display: none"
                />
                <button type="button" @click="$refs.coverInput?.click()" class="btn-upload-cover">
                  📷 选择图片
                </button>
                <small class="upload-hint">支持 JPG、PNG、GIF、WEBP，最大5MB</small>
              </div>
              <div v-if="formData.coverUrl && !coverPreview" class="cover-url-display">
                <span>当前封面：{{ formData.coverUrl }}</span>
                <button type="button" @click="formData.coverUrl = ''" class="btn-clear-url">清除</button>
              </div>
            </div>
          </div>
          <div class="form-actions">
            <button type="button" @click="closeForm" class="btn-cancel">取消</button>
            <button type="submit" class="btn-save">{{ editingBook ? '更新' : '添加' }}</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 图书列表 -->
    <div class="table-container">
      <table border="1" cellspacing="0" cellpadding="8">
        <thead>
          <tr>
            <th>ID</th>
            <th>书名</th>
            <th>作者</th>
            <th>ISBN</th>
            <th>出版社</th>
            <th>分类</th>
            <th>库存</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="book in books" :key="book.id">
            <td>{{ book.id }}</td>
            <td>{{ book.title }}</td>
            <td>{{ book.author }}</td>
            <td>{{ book.isbn || '-' }}</td>
            <td>{{ book.publisher || '-' }}</td>
            <td>{{ book.category || '未分类' }}</td>
            <td>{{ book.stock || 0 }}</td>
            <td>
              <span :class="['status-badge', book.isBorrowable ? 'status-available' : 'status-unavailable']">
                {{ book.isBorrowable ? '已上架' : '未上架' }}
              </span>
            </td>
            <td>
              <button @click="editBook(book)" class="btn-edit">编辑</button>
              <button @click="deleteBook(book.id)" class="btn-delete">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-if="books.length === 0 && !loading" class="empty-state">
        <p>暂无图书数据</p>
      </div>
    </div>

    <!-- 消息提示 -->
    <div v-if="message" :class="['message', messageType]">
      <span>{{ message }}</span>
      <button @click="message = ''" class="close-btn">×</button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import http from '../../lib/http'

const books = ref<any[]>([])
const loading = ref(false)
const searchKeyword = ref('')
const filterCategory = ref('')
const filterStatus = ref('')
const showAddForm = ref(false)
const editingBook = ref<any>(null)
const message = ref('')
const messageType = ref<'success' | 'error'>('success')

// 避免搜索输入触发的并发请求乱序覆盖：只应用最后一次请求的结果
let loadSeq = 0

const formData = ref({
  title: '',
  author: '',
  isbn: '',
  publisher: '',
  category: '',
  stock: 5,
  hotScore: 0,
  isBorrowable: false,
  coverUrl: ''
})

const coverPreview = ref<string | null>(null)
const coverInput = ref<HTMLInputElement | null>(null)
const uploadingCover = ref(false)

async function load() {
  const seq = ++loadSeq
  loading.value = true
  try {
    const params: any = {}
    const kw = searchKeyword.value.trim()
    if (kw) {
      params.keyword = kw
    }
    if (filterCategory.value) {
      params.category = filterCategory.value
    }
    
    const res = await http.get('/api/books', { params })
    // 若已有更新的请求在飞行中/已完成，则忽略旧响应，避免“先不生效、后又覆盖”
    if (seq !== loadSeq) return
    let booksList = res.data || []
    
    // 客户端筛选状态
    if (filterStatus.value !== '') {
      const status = filterStatus.value === 'true'
      booksList = booksList.filter((b: any) => b.isBorrowable === status)
    }
    
    books.value = booksList
  } catch (e: any) {
    if (seq !== loadSeq) return
    console.error('加载图书列表失败', e)
    showMessage('加载失败：' + (e?.response?.data?.message || e?.message || '未知错误'), 'error')
  } finally {
    // 只有最后一次请求才能关 loading，避免闪烁/状态错乱
    if (seq === loadSeq) loading.value = false
  }
}

function editBook(book: any) {
  editingBook.value = book
  formData.value = {
    title: book.title || '',
    author: book.author || '',
    isbn: book.isbn || '',
    publisher: book.publisher || '',
    category: book.category || '',
    stock: book.stock || 0,
    hotScore: book.hotScore || 0,
    isBorrowable: book.isBorrowable || false,
    coverUrl: book.coverUrl || ''
  }
  // 如果有封面URL，显示预览
  if (book.coverUrl) {
    coverPreview.value = book.coverUrl
  } else {
    coverPreview.value = null
  }
  showAddForm.value = true
}

function closeForm() {
  showAddForm.value = false
  editingBook.value = null
  formData.value = {
    title: '',
    author: '',
    isbn: '',
    publisher: '',
    category: '',
    stock: 5,
    hotScore: 0,
    isBorrowable: false,
    coverUrl: ''
  }
  coverPreview.value = null
  if (coverInput.value) {
    coverInput.value.value = ''
  }
}

async function handleCoverChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  
  // 验证文件类型
  if (!file.type.startsWith('image/')) {
    showMessage('请选择图片文件', 'error')
    return
  }
  
  // 验证文件大小（5MB）
  if (file.size > 5 * 1024 * 1024) {
    showMessage('图片大小不能超过5MB', 'error')
    return
  }
  
  uploadingCover.value = true
  try {
    // 先显示本地预览
    const reader = new FileReader()
    reader.onload = (e) => {
      coverPreview.value = e.target?.result as string
    }
    reader.readAsDataURL(file)
    
    // 上传到服务器
    const uploadFormData = new FormData()
    uploadFormData.append('file', file)
    
    // 注意：不要手动设置 Content-Type，让浏览器自动设置（包括 boundary）
    const res = await http.post('/api/books/upload-cover', uploadFormData)
    
    // 设置封面URL
    formData.value.coverUrl = res.data.coverUrl
    showMessage('封面上传成功', 'success')
  } catch (e: any) {
    console.error('封面上传失败', e)
    showMessage('封面上传失败：' + (e?.response?.data?.message || e?.message || '未知错误'), 'error')
    coverPreview.value = null
    if (coverInput.value) {
      coverInput.value.value = ''
    }
  } finally {
    uploadingCover.value = false
  }
}

function removeCover() {
  coverPreview.value = null
  formData.value.coverUrl = ''
  if (coverInput.value) {
    coverInput.value.value = ''
  }
}

async function saveBook() {
  try {
    if (editingBook.value) {
      // 更新图书
      await http.put(`/api/books/${editingBook.value.id}`, formData.value)
      showMessage('图书更新成功', 'success')
    } else {
      // 添加图书
      await http.post('/api/books', formData.value)
      showMessage('图书添加成功', 'success')
    }
    closeForm()
    await load()
  } catch (e: any) {
    console.error('保存图书失败', e)
    showMessage('保存失败：' + (e?.response?.data?.message || e?.message || '未知错误'), 'error')
  }
}

async function deleteBook(id: number) {
  if (!confirm('确认删除该图书？此操作不可恢复。')) return
  
  try {
    await http.delete(`/api/books/${id}`)
    showMessage('删除成功', 'success')
    await load()
  } catch (e: any) {
    console.error('删除图书失败', e)
    showMessage('删除失败：' + (e?.response?.data?.message || e?.message || '未知错误'), 'error')
  }
}

function showMessage(msg: string, type: 'success' | 'error' = 'success') {
  message.value = msg
  messageType.value = type
  setTimeout(() => {
    message.value = ''
  }, 3000)
}

onMounted(() => {
  load()
})
</script>

<style scoped src="../../styles/views/admin/BooksAdmin.css"></style>
