<template>
  <div class="seats-admin">
    <div class="header">
      <h2>座位管理</h2>
      <div class="header-actions">
        <button @click="exportExcel" class="btn btn-secondary" :disabled="exporting">批量导出</button>
        <button @click="showBatchImportDialog = true" class="btn btn-secondary">批量导入</button>
        <button
          @click="batchDeleteSeats"
          class="btn btn-danger"
          :disabled="selectedIds.length === 0"
          title="删除选中的座位（仅当前页可勾选）"
        >
          批量删除
        </button>
        <button @click="showCreateDialog = true" class="btn btn-primary">添加座位</button>
      </div>
    </div>
    
    <!-- 创建/编辑座位对话框 -->
    <div v-if="showCreateDialog || editingSeat" class="dialog-overlay" @click="closeDialog">
      <div class="dialog" @click.stop>
        <h3>{{ editingSeat ? '编辑座位' : '创建座位' }}</h3>
        <div class="form-grid">
          <div class="form-item">
            <label>座位标签 *</label>
            <input v-model.trim="formData.label" placeholder="如：A1-019" />
            <small class="field-hint">要求：标签格式为 A1-001（楼栋前缀 + 楼层 + “-” + 三位序号）</small>
          </div>
          <div class="form-item">
            <label>楼栋 *</label>
            <select v-model="formData.building">
              <option value="A楼">A楼</option>
            </select>
            <small class="field-hint">与批量导入一致：当前仅支持A楼</small>
          </div>
          <div class="form-item">
            <label>楼层 *</label>
            <select v-model.number="formData.floor">
              <option :value="null">请选择</option>
              <option :value="1">1</option>
              <option :value="2">2</option>
            </select>
          </div>
          <div class="form-item">
            <label>区域类型 *</label>
            <select v-model="formData.zone">
              <option value="">请选择</option>
              <option value="安静区">安静区</option>
              <option value="自习区">自习区</option>
            </select>
          </div>
          <div class="form-item">
            <label>行号 *</label>
            <input v-model.number="formData.rowNum" type="number" min="1" step="1" placeholder="用于 CSS Grid 布局的行号（正整数）" />
          </div>
          <div class="form-item">
            <label>列号 *</label>
            <input v-model.number="formData.colNum" type="number" min="1" step="1" placeholder="用于 CSS Grid 布局的列号（正整数）" />
          </div>
          <div class="form-item">
            <label>有电源 *</label>
            <select v-model="formData.hasPower">
              <option :value="null">请选择</option>
              <option :value="true">是</option>
              <option :value="false">否</option>
            </select>
          </div>
          <div class="form-item">
            <label>靠窗 *</label>
            <select v-model="formData.isWindow">
              <option :value="null">请选择</option>
              <option :value="true">是</option>
              <option :value="false">否</option>
            </select>
          </div>
          <div class="form-item">
            <label>状态 *</label>
            <select v-model="formData.status">
              <option value="FREE">空闲</option>
              <option value="IDLE">闲置</option>
              <option value="RESERVED">已预约</option>
              <option value="OCCUPIED">使用中</option>
              <option value="BROKEN">故障</option>
              <option value="FAULT">维修</option>
            </select>
          </div>
          <div class="form-item full-width">
            <label>备注</label>
            <textarea v-model="formData.note" placeholder="备注信息" rows="3"></textarea>
          </div>
        </div>
        <div class="dialog-actions">
          <button @click="saveSeat" class="btn btn-primary">保存</button>
          <button @click="closeDialog" class="btn btn-secondary">取消</button>
        </div>
      </div>
    </div>

    <!-- 批量导入对话框 -->
    <div v-if="showBatchImportDialog" class="dialog-overlay" @click="closeBatchImportDialog">
      <div class="dialog batch-import-dialog" @click.stop>
        <h3>批量导入座位</h3>
        <div class="batch-import-content">
          <div class="import-tips">
            <p><strong>使用说明：</strong></p>
            <ul>
              <li>仅支持 .xlsx 格式的 Excel 文件</li>
              <li>Excel 第一行为表头，从第二行开始为数据</li>
              <li><strong>必填列（均不能为空）：</strong>标签、楼栋、楼层、区域类型、行号、列号、有电源、靠窗、状态</li>
              <li><strong>规则：</strong>楼栋仅允许 A楼；楼层仅允许 1/2；区域类型仅允许 安静区/自习区；有电源/靠窗仅允许 是/否；状态仅允许 FREE/IDLE/RESERVED/OCCUPIED/BROKEN/FAULT；标签格式必须类似 A1-019（楼栋前缀+楼层+三位序号）</li>
              <li><strong>列名与顺序必须完全一致：</strong>标签 | 楼栋 | 楼层 | 区域类型 | 行号 | 列号 | 有电源 | 靠窗 | 状态 | 备注</li>
            </ul>
            <button type="button" class="btn-link" @click="downloadImportTemplate" :disabled="importing || exporting">
              📥 下载 Excel 模板（.xlsx）
            </button>
          </div>
          <div class="import-editor">
            <div class="file-upload-area">
              <input 
                type="file" 
                ref="excelFileInput"
                accept=".xlsx"
                @change="handleExcelFileSelect"
                class="file-input"
                :disabled="importing"
              />
              <div v-if="selectedExcelFile" class="file-info">
                <p>已选择文件：<strong>{{ selectedExcelFile.name }}</strong></p>
                <p class="file-size">文件大小：{{ formatFileSize(selectedExcelFile.size) }}</p>
              </div>
              <div v-else class="file-placeholder">
                <p>📄 点击选择 Excel 文件或拖拽文件到此处</p>
                <p class="hint">仅支持 .xlsx 格式</p>
              </div>
            </div>
            <div v-if="importError" class="import-error">
              {{ importError }}
            </div>
          </div>
        </div>
        <div class="dialog-actions">
          <button 
            @click="importExcel()" 
            class="btn btn-primary" 
            :disabled="importing || !selectedExcelFile"
          >
            {{ importing ? '导入中...' : '确认导入' }}
          </button>
          <button @click="closeBatchImportDialog" class="btn btn-secondary" :disabled="importing">取消</button>
        </div>
      </div>
    </div>

    <!-- 多条件查询：两行排布，与表格/侧栏字号一致 -->
    <div class="filter-section">
      <div class="filter-rows">
        <div class="filter-row filter-row--primary">
          <div class="filter-item filter-pos-r1c1">
            <label>楼层：</label>
            <select v-model.number="floorFilter" @change="handleFilterChange">
              <option :value="0">全部楼层</option>
              <option :value="1">1</option>
              <option :value="2">2</option>
            </select>
          </div>
          <div class="filter-item filter-pos-r1c2">
            <label>区域类型：</label>
            <select v-model="zoneFilter" @change="handleFilterChange">
              <option value="">全部区域</option>
              <option value="安静区">安静区</option>
              <option value="自习区">自习区</option>
            </select>
          </div>
          <div class="filter-item filter-pos-r1c3">
            <label>状态：</label>
            <select v-model="statusFilter" @change="handleFilterChange">
              <option value="">全部状态</option>
              <option value="FREE">空闲</option>
              <option value="IDLE">闲置</option>
              <option value="RESERVED">已预约</option>
              <option value="OCCUPIED">使用中</option>
              <option value="BROKEN">故障</option>
              <option value="FAULT">维修</option>
            </select>
          </div>
        </div>
        <div class="filter-row filter-row--secondary">
          <div class="filter-item filter-pos-r2c1">
            <label>有电源：</label>
            <select v-model="hasPowerFilter" @change="handleFilterChange">
              <option value="">全部</option>
              <option value="true">是</option>
              <option value="false">否</option>
            </select>
          </div>
          <div class="filter-item filter-pos-r2c2">
            <label>靠窗：</label>
            <select v-model="isWindowFilter" @change="handleFilterChange">
              <option value="">全部</option>
              <option value="true">是</option>
              <option value="false">否</option>
            </select>
          </div>
          <div class="filter-item filter-item--query filter-pos-r2c3">
            <label>标签/备注：</label>
            <input
              type="text"
              v-model.trim="labelQuery"
              @input="handleLabelQuery"
              placeholder="输入标签或备注关键字"
              class="seat-id-input"
              autocomplete="off"
            />
            <button v-if="labelQuery" type="button" @click="clearLabelQuery" class="btn-clear">清除</button>
          </div>
          <div class="filter-item filter-reset filter-pos-r2c5">
            <button type="button" class="btn btn-secondary" @click="resetFilters">重置筛选</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 座位列表 -->
    <div class="table-container">
      <!-- 分页控制栏 -->
      <div class="pagination-controls">
        <div class="page-size-selector">
          <label>每页显示：</label>
          <select v-model.number="pageSize" @change="handlePageSizeChange">
            <option :value="10">10</option>
            <option :value="20">20</option>
            <option :value="50">50</option>
            <option :value="100">100</option>
          </select>
          <span class="total-info">{{ totalInfoText }}</span>
        </div>
      </div>
      
      <table>
        <thead>
          <tr>
            <th style="width: 44px;">
              <input
                type="checkbox"
                :checked="isAllCurrentPageSelected"
                :indeterminate="isSomeCurrentPageSelected"
                @change="toggleSelectAllCurrentPage(($event.target as HTMLInputElement).checked)"
                title="全选/取消全选当前页"
              />
            </th>
            <th>ID</th>
            <th>标签</th>
            <th>楼栋</th>
            <th>楼层</th>
            <th>区域类型</th>
            <th>有电源</th>
            <th>靠窗</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="seat in filteredSeats" :key="seat.id">
            <td>
              <input
                type="checkbox"
                :checked="selectedIds.includes(seat.id)"
                @change="toggleSelectOne(seat.id, ($event.target as HTMLInputElement).checked)"
              />
            </td>
            <td>{{ seat.id }}</td>
            <td>{{ seat.label || '-' }}</td>
            <td>{{ seat.building || '-' }}</td>
            <td>{{ seat.floor || '-' }}</td>
            <td>{{ seat.zone || '-' }}</td>
            <td>{{ seat.hasPower ? '是' : '否' }}</td>
            <td>{{ seat.isWindow ? '是' : '否' }}</td>
            <td>
              <span :class="['status-badge', `status-${seat.status?.toLowerCase()}`]">
                {{ getStatusText(seat.status) }}
              </span>
            </td>
            <td>
              <button @click="editSeat(seat)" class="btn btn-small btn-primary">编辑</button>
              <button @click="deleteSeat(seat.id)" class="btn btn-small btn-danger">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      
      <!-- 分页导航 -->
      <div class="pagination">
        <button 
          class="pagination-btn" 
          :disabled="currentPage === 1" 
          @click="goToPage(1)">
          首页
        </button>
        <button 
          class="pagination-btn" 
          :disabled="currentPage === 1" 
          @click="goToPage(currentPage - 1)">
          上一页
        </button>
        
        <span class="page-info">
          第 
          <input 
            type="number" 
            v-model.number="inputPage" 
            @keyup.enter="goToInputPage"
            @blur="goToInputPage"
            class="page-input"
            :min="1" 
            :max="totalPages" 
          />
          页 / 共 {{ totalPages }} 页
        </span>
        
        <button 
          class="pagination-btn" 
          :disabled="currentPage === totalPages" 
          @click="goToPage(currentPage + 1)">
          下一页
        </button>
        <button 
          class="pagination-btn" 
          :disabled="currentPage === totalPages" 
          @click="goToPage(totalPages)">
          末页
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import http from '../../lib/http'

const seats = ref<any[]>([])
const selectedIds = ref<number[]>([])
const isAllCurrentPageSelected = computed(() => {
  if (!seats.value.length) return false
  return seats.value.every((s: any) => selectedIds.value.includes(s.id))
})
const isSomeCurrentPageSelected = computed(() => {
  if (!seats.value.length) return false
  const selectedOnPage = seats.value.filter((s: any) => selectedIds.value.includes(s.id)).length
  return selectedOnPage > 0 && selectedOnPage < seats.value.length
})
// 多条件筛选（服务端）
const floorFilter = ref<number>(0)
const zoneFilter = ref('')
const statusFilter = ref('')
/** '' | 'true' | 'false' */
const hasPowerFilter = ref('')
const isWindowFilter = ref('')
const labelQuery = ref('')
const showCreateDialog = ref(false)
const editingSeat = ref<any>(null)
const showBatchImportDialog = ref(false)
const importError = ref('')
const importing = ref(false)
const exporting = ref(false)
const selectedExcelFile = ref<File | null>(null)
const excelFileInput = ref<HTMLInputElement | null>(null)
const formData = ref({
  label: '',
  building: 'A楼',
  floor: null as number | null,
  zone: '',
  rowNum: null as number | null,
  colNum: null as number | null,
  hasPower: null as boolean | null,
  isWindow: null as boolean | null,
  status: 'FREE',
  note: ''
})

const ALLOWED_STATUS = new Set(['FREE', 'IDLE', 'RESERVED', 'OCCUPIED', 'BROKEN', 'FAULT'])
const ALLOWED_ZONE = new Set(['安静区', '自习区'])
const ALLOWED_FLOOR = new Set([1, 2])
const LABEL_PATTERN = /^([A-Za-z0-9]+)(\d+)-(\d{3})$/

function extractBuildingPrefix(building: string): string {
  const raw = (building || '').trim().replace(/楼$/g, '')
  const m = raw.match(/^([A-Za-z0-9]+)/)
  return m ? m[1] : ''
}

function validateSeatForm(): string | null {
  const v = formData.value
  const label = (v.label || '').trim()
  if (!label) return '座位标签不能为空'
  if (!v.building || v.building.trim() !== 'A楼') return '楼栋仅允许填写 A楼'
  if (v.floor == null) return '请选择楼层（仅允许 1 或 2）'
  if (!ALLOWED_FLOOR.has(v.floor)) return '楼层仅允许 1 或 2'
  if (!v.zone || !ALLOWED_ZONE.has(v.zone)) return '区域类型必填，且仅允许：安静区 / 自习区'
  if (v.rowNum == null || !Number.isInteger(v.rowNum) || v.rowNum <= 0) return '行号必填，且必须为正整数'
  if (v.colNum == null || !Number.isInteger(v.colNum) || v.colNum <= 0) return '列号必填，且必须为正整数'
  if (v.hasPower == null) return '请选择“有电源”：是 / 否'
  if (v.isWindow == null) return '请选择“靠窗”：是 / 否'
  const status = (v.status || '').toUpperCase()
  if (!status || !ALLOWED_STATUS.has(status)) return `状态必填，且仅允许：${Array.from(ALLOWED_STATUS).join('/')}`

  const m = label.match(LABEL_PATTERN)
  if (!m) return '标签格式不合法，应为 A1-001 这种格式（例：A1-019）'
  const buildingPrefix = extractBuildingPrefix(v.building)
  if (!buildingPrefix) return '楼栋格式不支持，无法校验标签（建议：A楼）'
  const expectedPrefix = `${buildingPrefix}${v.floor}`
  const labelPrefix = `${m[1]}${m[2]}`
  if (labelPrefix.toUpperCase() !== expectedPrefix.toUpperCase()) {
    return `标签与楼栋/楼层不一致：按楼栋/楼层应为 ${expectedPrefix}-001（例：${expectedPrefix}-019）`
  }
  return null
}

// 分页相关
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const totalAll = ref<number | null>(null)
const totalPages = ref(0)
const inputPage = ref(1)
let searchTimer: ReturnType<typeof setTimeout> | null = null

const totalInfoText = computed(() => {
  const hasFilter =
    (floorFilter.value && floorFilter.value !== 0) ||
    !!zoneFilter.value.trim() ||
    !!statusFilter.value.trim() ||
    !!hasPowerFilter.value ||
    !!isWindowFilter.value ||
    !!labelQuery.value.trim()
  if (!hasFilter) return `共 ${total.value} 条记录`
  return totalAll.value != null
    ? `查到 ${total.value} 条记录 / 共 ${totalAll.value} 条`
    : `查到 ${total.value} 条记录`
})

function getStatusText(status: string): string {
  const map: Record<string, string> = {
    'FREE': '空闲',
    'IDLE': '闲置',
    'RESERVED': '已预约',
    'OCCUPIED': '使用中',
    'BROKEN': '故障',
    'FAULT': '维修'
  }
  return map[status] || status
}

// 列表展示数据：服务端已筛选，这里直接使用 seats
const filteredSeats = computed(() => seats.value)

async function loadSeats() {
  try {
    // 管理员使用管理端API获取座位列表（带分页）
    const res = await http.get('/api/admin/seats', {
      params: {
        current: currentPage.value,
        size: pageSize.value,
        floor: floorFilter.value && floorFilter.value !== 0 ? floorFilter.value : undefined,
        zone: zoneFilter.value.trim() || undefined,
        status: statusFilter.value.trim() || undefined,
        hasPower:
          hasPowerFilter.value === 'true' ? true : hasPowerFilter.value === 'false' ? false : undefined,
        isWindow:
          isWindowFilter.value === 'true' ? true : isWindowFilter.value === 'false' ? false : undefined,
        labelQuery: labelQuery.value.trim() || undefined
      }
    })
    console.log('座位管理API响应:', res)
    console.log('响应数据:', res.data)
    
    // 处理响应数据
    if (res.data && typeof res.data === 'object') {
      const records = res.data.records || []
      seats.value = records
      // 翻页/刷新时：清理不在当前页的选择（避免误删）
      const currentPageIds = new Set(records.map((r: any) => r.id))
      selectedIds.value = selectedIds.value.filter((id) => currentPageIds.has(id))
      total.value = res.data.total || 0
      totalAll.value = typeof res.data.totalAll === 'number' ? res.data.totalAll : null
      totalPages.value = res.data.pages || 1
      currentPage.value = res.data.current || 1
      inputPage.value = currentPage.value
      console.log('解析后的数据:', {
        seats: seats.value.length,
        total: total.value,
        pages: totalPages.value,
        current: currentPage.value
      })
    } else {
      // 如果返回的不是分页格式，可能是旧格式的列表
      console.warn('API返回的不是分页格式，尝试作为列表处理')
      const records = Array.isArray(res.data) ? res.data : []
      seats.value = records
      const currentPageIds = new Set(records.map((r: any) => r.id))
      selectedIds.value = selectedIds.value.filter((id) => currentPageIds.has(id))
      total.value = seats.value.length
      totalAll.value = null
      totalPages.value = Math.max(1, Math.ceil(total.value / pageSize.value))
    }
  } catch (e: any) {
    console.error('加载座位失败', e)
    console.error('错误详情:', e.response)
    alert(e?.response?.data?.message || '加载座位失败')
  }
}

function toggleSelectAllCurrentPage(checked: boolean) {
  const idsOnPage = seats.value.map((s: any) => s.id)
  if (checked) {
    const set = new Set(selectedIds.value)
    idsOnPage.forEach((id: number) => set.add(id))
    selectedIds.value = Array.from(set)
  } else {
    const idsSet = new Set(idsOnPage)
    selectedIds.value = selectedIds.value.filter((id) => !idsSet.has(id))
  }
}

function toggleSelectOne(id: number, checked: boolean) {
  if (checked) {
    if (!selectedIds.value.includes(id)) selectedIds.value.push(id)
  } else {
    selectedIds.value = selectedIds.value.filter((x) => x !== id)
  }
}

async function batchDeleteSeats() {
  if (selectedIds.value.length === 0) return
  if (!confirm(`确认批量删除选中的 ${selectedIds.value.length} 个座位吗？此操作不可恢复。`)) return
  try {
    const res = await http.post('/api/admin/seats/batch-delete', {
      ids: selectedIds.value
    })
    const data = res.data || {}
    let msg = `批量删除完成：成功 ${data.success ?? 0} / ${data.total ?? selectedIds.value.length}`
    if (Array.isArray(data.errors) && data.errors.length > 0) {
      msg += `\n\n失败详情：\n${data.errors.slice(0, 10).join('\n')}`
      if (data.errors.length > 10) msg += `\n... 还有 ${data.errors.length - 10} 条`
    }
    alert(msg)
    selectedIds.value = []
    await loadSeats()
  } catch (e: any) {
    alert(e?.response?.data?.message || '批量删除失败')
  }
}

function handlePageSizeChange() {
  currentPage.value = 1
  loadSeats()
}

function handleFilterChange() {
  currentPage.value = 1
  loadSeats()
}

function handleLabelQuery() {
  if (searchTimer) clearTimeout(searchTimer)
  if (!labelQuery.value.trim()) {
    handleFilterChange()
    return
  }
  searchTimer = setTimeout(() => {
    currentPage.value = 1
    loadSeats()
  }, 320)
}

function clearLabelQuery() {
  labelQuery.value = ''
  handleFilterChange()
}

/** 恢复全部条件为默认并回到第 1 页 */
function resetFilters() {
  floorFilter.value = 0
  zoneFilter.value = ''
  statusFilter.value = ''
  hasPowerFilter.value = ''
  isWindowFilter.value = ''
  labelQuery.value = ''
  if (searchTimer) {
    clearTimeout(searchTimer)
    searchTimer = null
  }
  currentPage.value = 1
  loadSeats()
}

function goToPage(page: number) {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
  loadSeats()
}

async function exportExcel() {
  try {
    exporting.value = true
    const res = await http.get('/api/admin/seats/export', { responseType: 'blob' })
    const blob = new Blob([res.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    })
    const link = document.createElement('a')
    const url = URL.createObjectURL(blob)
    const today = new Date().toISOString().split('T')[0]
    link.href = url
    link.download = `座位导出-${today}.xlsx`
    link.style.visibility = 'hidden'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    alert('导出成功')
  } catch (e: any) {
    console.error('导出Excel失败:', e)
    alert(e?.response?.data?.message || '导出失败，请稍后重试')
  } finally {
    exporting.value = false
  }
}

async function downloadImportTemplate() {
  try {
    const res = await http.get('/api/admin/seats/import-template', {
      responseType: 'blob'
    })
    const blob = new Blob([res.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    })
    const link = document.createElement('a')
    const url = URL.createObjectURL(blob)
    link.href = url
    link.download = '座位导入模板.xlsx'
    link.style.visibility = 'hidden'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  } catch (e: any) {
    console.error('下载导入模板失败:', e)
    alert(e?.response?.data?.message || '下载失败，请稍后重试')
  }
}

function goToInputPage() {
  const page = parseInt(String(inputPage.value))
  if (isNaN(page) || page < 1) {
    inputPage.value = currentPage.value
    return
  }
  if (page > totalPages.value) {
    inputPage.value = totalPages.value
    goToPage(totalPages.value)
    return
  }
  goToPage(page)
}

function editSeat(seat: any) {
  editingSeat.value = seat
  formData.value = {
    label: seat.label || '',
    building: seat.building || 'A楼',
    floor: seat.floor || null,
    zone: seat.zone || '',
    rowNum: seat.rowNum || seat.row || null,
    colNum: seat.colNum || seat.col || null,
    hasPower: typeof seat.hasPower === 'boolean' ? seat.hasPower : (seat.hasPower ? true : null),
    isWindow: typeof seat.isWindow === 'boolean' ? seat.isWindow : (seat.isWindow ? true : null),
    status: seat.status || 'FREE',
    note: seat.note || ''
  }
  showCreateDialog.value = true
}

function closeDialog() {
  showCreateDialog.value = false
  editingSeat.value = null
  formData.value = {
    label: '',
    building: 'A楼',
    floor: null,
    zone: '',
    rowNum: null,
    colNum: null,
    hasPower: null,
    isWindow: null,
    status: 'FREE',
    note: ''
  }
}

async function saveSeat() {
  const err = validateSeatForm()
  if (err) {
    alert(err)
    return
  }
  
  try {
    // 提交前规范化：trim + status 大写
    const payload = {
      ...formData.value,
      label: formData.value.label.trim(),
      building: formData.value.building.trim(),
      zone: formData.value.zone.trim(),
      status: String(formData.value.status || '').toUpperCase(),
      note: (formData.value.note || '').trim()
    }
    if (editingSeat.value) {
      // 更新
      await http.put(`/api/admin/seats/${editingSeat.value.id}`, payload)
      alert('更新成功')
    } else {
      // 创建
      await http.post('/api/admin/seats', payload)
      alert('创建成功')
    }
    closeDialog()
    loadSeats()
  } catch (e: any) {
    const status = e?.response?.status
    const data = e?.response?.data
    const msg =
      (typeof data === 'string' && data.trim()) ||
      data?.message ||
      e?.message ||
      (status ? `操作失败（HTTP ${status}）` : '操作失败')
    alert(String(msg))
  }
}

async function deleteSeat(id: number) {
  if (!confirm('确定删除这个座位吗？')) return
  try {
    await http.delete(`/api/admin/seats/${id}`)
    alert('删除成功')
    loadSeats()
  } catch (e: any) {
    alert(e.response?.data?.message || '删除失败')
  }
}

// 批量导入相关函数
function closeBatchImportDialog() {
  if (importing.value) return
  showBatchImportDialog.value = false
  importError.value = ''
  selectedExcelFile.value = null
  if (excelFileInput.value) {
    excelFileInput.value.value = ''
  }
}

function handleExcelFileSelect(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) {
    selectedExcelFile.value = file
    importError.value = ''
  }
}

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

async function importExcel() {
  if (!selectedExcelFile.value) {
    importError.value = '请先选择 Excel 文件'
    return
  }

  if (!confirm(`确认导入 Excel 文件 "${selectedExcelFile.value.name}" 吗？`)) {
    return
  }

  importing.value = true
  importError.value = ''

  try {
    const formData = new FormData()
    formData.append('file', selectedExcelFile.value)

    const res = await http.post('/api/admin/seats/batch-excel', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })

    const data = res.data
    let message = `批量导入成功！共导入 ${data.success || data.total || 0} 个座位`
    
    if (data.errors && data.errors.length > 0) {
      message += `\n\n部分记录导入失败：\n${data.errors.slice(0, 10).join('\n')}`
      if (data.errors.length > 10) {
        message += `\n... 还有 ${data.errors.length - 10} 个错误`
      }
    }

    alert(message)
    closeBatchImportDialog()
    // 重新加载座位列表
    currentPage.value = 1
    await loadSeats()
  } catch (e: any) {
    console.error('Excel 导入失败', e)
    const errorMsg = e?.response?.data?.message || e?.message || '未知错误'
    const errors = e?.response?.data?.errors
    if (errors && Array.isArray(errors)) {
      importError.value = `导入失败：${errorMsg}\n\n错误详情：\n${errors.slice(0, 10).join('\n')}`
      if (errors.length > 10) {
        importError.value += `\n... 还有 ${errors.length - 10} 个错误`
      }
    } else {
      importError.value = '导入失败：' + errorMsg
    }
  } finally {
    importing.value = false
  }
}


onMounted(() => {
  loadSeats()
})
</script>

<style scoped src="../../styles/views/admin/SeatsAdmin.css"></style>
