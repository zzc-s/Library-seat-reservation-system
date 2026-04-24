import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: '0.0.0.0', // 允许外部访问，这样手机可以通过局域网IP访问
    strictPort: true, // 如果端口被占用，报错而不是自动切换
    //第10-14行：配置开发服务器代理规则
    proxy: {
      '/api': {//匹配所有以 /api 开头的请求
        target: 'http://localhost:8080',//转发到后端地址 http://localhost:8080
        changeOrigin: true,//修改请求头的Origin字段，避免跨域问题
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
        changeOrigin: true,
      },
    },
  },
})


