import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '../stores/auth'
import router from '../router'

const http = axios.create({ 
  baseURL: '/',
  timeout: 30000 // 30秒超时（增加超时时间，避免处理时间过长导致失败）
})

// 请求拦截器：自动添加 JWT token
http.interceptors.request.use(//注册请求拦截器，每次发请求前自动执行
  (config: InternalAxiosRequestConfig) => {
    // 对于不需要认证的接口，不添加 token
    const url = config.url || ''
    const publicPaths = [
      '/api/auth/login', 
      '/api/auth/register', 
      '/api/auth/register-code',
      '/api/auth/forgot', 
      '/api/auth/reset',
      '/api/notices/public',  // 公开公告允许匿名访问
      '/api/feedbacks/public',  // 公开反馈允许匿名访问
      '/api/seat/query',  // 可视化选座查询允许匿名访问
      '/api/seats'  // 座位列表查询允许匿名访问（用于加载选项）
    ]
    const isPublicPath = publicPaths.some(path => url.startsWith(path))
    //从Pinia状态管理获取当前用户的token
    const auth = useAuthStore()
    // 对于非公开接口，必须要有 token
    if (!isPublicPath) {
      if (!auth.token) {
        // 如果没有token，阻止请求并跳转到登录页
        console.warn('请求需要认证但没有 token，跳转到登录页:', url)
        if (router.currentRoute.value.name !== 'login') {
          const redirectPath = router.currentRoute.value.fullPath
          router.replace({
            name: 'login',
            query: { redirect: redirectPath }
          })
        }
        return Promise.reject(new Error('未登录，请先登录'))
      }
      // 添加 token 到请求头  Authorization: Bearer xxxxx，后端用这个验证身份
      config.headers = config.headers || {}
      config.headers['Authorization'] = `Bearer ${auth.token}`
      console.log('请求添加 Authorization 头:', url)
    }
     //返回修改后的配置，继续发送请求
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器：处理错误和认证失败
http.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const auth = useAuthStore()
    
    // 处理 401 未授权错误（token 过期、无效或被加入黑名单）
    if (error.response?.status === 401) {
      // 清除本地认证信息
      auth.clear()
      
      // 如果当前不在登录页，则跳转到登录页，并保存重定向路径
      if (router.currentRoute.value.name !== 'login') {
        const redirectPath = router.currentRoute.value.fullPath
        router.replace({
          name: 'login',
          query: { redirect: redirectPath }
        })
      }
      
      // 优先使用后端返回的 message（例如：该用户未注册）
      const message = (error.response.data as any)?.message || '未授权，请重新登录'
      return Promise.reject(new Error(message))
    }
    
    // 处理 403 禁止访问（权限不足）
    if (error.response?.status === 403) {
      const message = (error.response.data as any)?.message || '权限不足，无法访问该资源'
      return Promise.reject(new Error(message))
    }
    
    // 处理网络错误
    if (!error.response) {
      return Promise.reject(new Error('网络连接失败，请检查网络设置'))
    }
    
    // 对于409冲突错误，保留完整的响应数据，让业务代码处理
    if (error.response?.status === 409) {
      // 保留原始错误对象，以便业务代码可以访问完整的响应数据
      return Promise.reject(error)
    }
    
    // 处理其他 HTTP 错误
    const message = (error.response.data as any)?.message || `请求失败：${error.response.status}`
    return Promise.reject(new Error(message))
  }
)

export default http


