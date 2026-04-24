<template>
  <div class="checkin-page">
    <div class="checkin-container">
      <div v-if="loading" class="loading-state">
        <div class="spinner"></div>
        <p>正在处理签到...</p>
      </div>
      
      <div v-else-if="error" class="error-state">
        <div class="error-icon">❌</div>
        <h2>签到失败</h2>
        <p class="error-message">{{ error }}</p>
        <div class="error-actions">
          <button @click="retry" class="btn-retry">重试</button>
          <button @click="goHome" class="btn-home">返回首页</button>
        </div>
      </div>
      
      <div v-else-if="success" class="success-state">
        <div class="success-icon">✅</div>
        <h2>签到成功！</h2>
        <div class="success-info" v-if="checkInData">
          <p v-if="checkInData.reservationId"><strong>预约ID：</strong>#{{ checkInData.reservationId }}</p>
          <p v-if="checkInData.seatId"><strong>座位ID：</strong>{{ checkInData.seatId }}</p>
          <p><strong>签到时间：</strong>{{ formatDateTime(checkInData.checkInTime) }}</p>
        </div>
        <button @click="goHome" class="btn-home">返回首页</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import http from '../lib/http'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const error = ref('')
const success = ref(false)
const checkInData = ref<any>(null)

function formatDateTime(dateTime: string | null | undefined): string {
  if (!dateTime) return '-'
  try {
    const date = new Date(dateTime)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    })
  } catch {
    return dateTime
  }
}

async function performCheckIn() {
  loading.value = true
  error.value = ''
  success.value = false
  
  try {
    // 从URL参数获取二维码内容
    let qr = route.query.qr as string
    if (!qr) {
      error.value = '缺少二维码参数，请重新扫描二维码'
      return
    }
    
    // 如果qr是完整URL，提取seat:xxx部分
    // 例如：http://localhost:5173/checkin?qr=seat:1 -> seat:1
    if (qr.includes('seat:')) {
      const match = qr.match(/seat:\d+/)
      if (match) {
        qr = match[0]
      }
    }
    
    // 检查是否已登录
    const auth = (await import('../stores/auth')).useAuthStore()
    if (!auth.isAuthenticated) {
      // 未登录，跳转到登录页，登录后返回
      error.value = '请先登录'
      setTimeout(() => {
        router.push({ 
          name: 'login', 
          query: { redirect: route.fullPath } 
        })
      }, 2000)
      return
    }
    
    // 调用签到接口
    const res = await http.post('/api/attendance/checkin', {
      qrContent: qr,
      note: '微信扫码签到'
    })
    
    success.value = true
    // 从响应中获取预约ID和座位ID
    checkInData.value = {
      reservationId: res.data?.reservationId || null,
      seatId: res.data?.seatId || extractSeatIdFromQr(qr),
      checkInTime: res.data?.occurredAt || new Date().toISOString()
    }
    
    // 3秒后自动关闭当前页面或返回首页
    setTimeout(() => {
      closePageOrGoHome()
    }, 3000)
  } catch (e: any) {
    console.error('签到失败:', e)
    
    // 处理网络错误或响应为空的情况
    if (!e?.response) {
      error.value = '网络连接失败，请检查网络设置或稍后重试'
      loading.value = false
      return
    }
    
    const errorMsg = e?.response?.data?.message || e?.message || '签到失败，请稍后重试'
    
    // 如果是401未登录错误，跳转到登录页
    if (e?.response?.status === 401) {
      error.value = '登录已过期，请重新登录'
      setTimeout(() => {
        router.push({ 
          name: 'login', 
          query: { redirect: route.fullPath } 
        })
      }, 2000)
    } else {
      error.value = errorMsg
    }
  } finally {
    loading.value = false
  }
}

// 从二维码内容中提取座位ID
function extractSeatIdFromQr(qr: string): number | null {
  try {
    // 格式：seat:1 或 http://.../checkin?qr=seat:1
    const match = qr.match(/seat:(\d+)/)
    if (match && match[1]) {
      return parseInt(match[1])
    }
  } catch (e) {
    console.error('提取座位ID失败:', e)
  }
  return null
}

function retry() {
  performCheckIn()
}

function goHome() {
  router.push({ name: 'home' })
}

// 尝试关闭当前浏览器页面；如果关闭失败则返回首页
function closePageOrGoHome() {
  try {
    // 兼容微信内置浏览器
    const w: any = window
    if (typeof w.WeixinJSBridge !== 'undefined' && typeof w.WeixinJSBridge.call === 'function') {
      w.WeixinJSBridge.call('closeWindow')
      return
    }
    
    // 普通浏览器尝试关闭当前标签页
    window.close()
  } catch (e) {
    console.warn('关闭页面失败:', e)
  }
  
  // 如果浏览器阻止关闭窗口，则回退到返回首页
  goHome()
}

onMounted(() => {
  performCheckIn()
})
</script>

<style scoped src="../styles/views/CheckIn.css"></style>
