import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { useAuthStore } from './auth'

type AnyMsg = any
type MsgHandler = (msg: AnyMsg) => void

function buildWsUrl(path: string) {
  if (import.meta.env.DEV) {
    return `ws://${window.location.host}${path}`
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}${path}`
}

export const useRealtimeStore = defineStore('realtime', () => {
  const auth = useAuthStore()

  const seatWs = ref<WebSocket | null>(null)
  const feedbackWs = ref<WebSocket | null>(null)
  const seatConnected = ref(false)
  const feedbackConnected = ref(false)

  const seatHandlers = new Set<MsgHandler>()
  const feedbackHandlers = new Set<MsgHandler>()

  let seatCloseIntentional = false
  let feedbackCloseIntentional = false
  let seatReconnectTimer: number | null = null
  let feedbackReconnectTimer: number | null = null

  const isAuthenticated = computed(() => !!auth.token)
  const isAdmin = computed(() => auth.isAdmin)

  function connectSeatWs() {
    if (!isAuthenticated.value) return
    if (seatWs.value && (seatWs.value.readyState === WebSocket.OPEN || seatWs.value.readyState === WebSocket.CONNECTING)) {
      return
    }
    seatCloseIntentional = false
    const wsUrl = buildWsUrl('/ws/seat-status')
    try {
      seatWs.value = new WebSocket(wsUrl)
      seatWs.value.onopen = () => {
        seatConnected.value = true
      }
      seatWs.value.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data)
          seatHandlers.forEach((h) => h(data))
        } catch (e) {
          console.error('解析 seat WebSocket 消息失败', e)
        }
      }
      seatWs.value.onerror = (e) => {
        seatConnected.value = false
        console.error('seat WebSocket 错误', e)
      }
      seatWs.value.onclose = () => {
        seatConnected.value = false
        if (seatCloseIntentional) return
        if (seatReconnectTimer != null) window.clearTimeout(seatReconnectTimer)
        seatReconnectTimer = window.setTimeout(() => {
          if (!seatCloseIntentional) connectSeatWs()
        }, 3000)
      }
    } catch (e) {
      seatConnected.value = false
      console.error('创建 seat WebSocket 失败', e)
    }
  }

  function connectFeedbackWs() {
    if (!isAuthenticated.value || !isAdmin.value) return
    if (feedbackWs.value && (feedbackWs.value.readyState === WebSocket.OPEN || feedbackWs.value.readyState === WebSocket.CONNECTING)) {
      return
    }
    feedbackCloseIntentional = false
    const wsUrl = buildWsUrl('/ws/feedback')
    try {
      feedbackWs.value = new WebSocket(wsUrl)
      feedbackWs.value.onopen = () => {
        feedbackConnected.value = true
      }
      feedbackWs.value.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data)
          feedbackHandlers.forEach((h) => h(data))
        } catch (e) {
          console.error('解析 feedback WebSocket 消息失败', e)
        }
      }
      feedbackWs.value.onerror = (e) => {
        feedbackConnected.value = false
        console.error('feedback WebSocket 错误', e)
      }
      feedbackWs.value.onclose = () => {
        feedbackConnected.value = false
        if (feedbackCloseIntentional) return
        if (feedbackReconnectTimer != null) window.clearTimeout(feedbackReconnectTimer)
        feedbackReconnectTimer = window.setTimeout(() => {
          if (!feedbackCloseIntentional) connectFeedbackWs()
        }, 3000)
      }
    } catch (e) {
      feedbackConnected.value = false
      console.error('创建 feedback WebSocket 失败', e)
    }
  }

  function disconnectAll() {
    seatCloseIntentional = true
    feedbackCloseIntentional = true
    if (seatReconnectTimer != null) window.clearTimeout(seatReconnectTimer)
    if (feedbackReconnectTimer != null) window.clearTimeout(feedbackReconnectTimer)
    seatReconnectTimer = null
    feedbackReconnectTimer = null

    if (seatWs.value) {
      try { seatWs.value.close() } catch {}
      seatWs.value = null
    }
    if (feedbackWs.value) {
      try { feedbackWs.value.close() } catch {}
      feedbackWs.value = null
    }
    seatConnected.value = false
    feedbackConnected.value = false
  }

  /** 在 App 里调用：跟随登录态启动/停止全局连接 */
  function syncConnections() {
    if (!isAuthenticated.value) {
      disconnectAll()
      return
    }
    connectSeatWs()
    if (isAdmin.value) {
      connectFeedbackWs()
    } else {
      // 普通用户：关闭管理端 feedback ws（避免占用）
      feedbackCloseIntentional = true
      if (feedbackWs.value) {
        try { feedbackWs.value.close() } catch {}
        feedbackWs.value = null
      }
      feedbackConnected.value = false
    }
  }

  function onSeatMessage(handler: MsgHandler) {
    seatHandlers.add(handler)
    return () => seatHandlers.delete(handler)
  }

  function onFeedbackMessage(handler: MsgHandler) {
    feedbackHandlers.add(handler)
    return () => feedbackHandlers.delete(handler)
  }

  return {
    seatConnected,
    feedbackConnected,
    syncConnections,
    disconnectAll,
    onSeatMessage,
    onFeedbackMessage
  }
})

