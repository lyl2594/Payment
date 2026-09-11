import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 统一 axios 封装：
 * - 后台请求自动携带 JWT（localStorage token）
 * - 解包统一响应体 {code,message,data}，code===0 时直接返回 data
 * - HTTP 401 / 业务码 10401：清除会话并跳转登录页
 */
export const http = axios.create({
  baseURL: '',
  timeout: 10000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('admin_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    // 统一响应体解包
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data
      }
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || `业务错误 ${body.code}`))
    }
    return body
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      // 会话失效：清除 token，跳转登录
      localStorage.removeItem('admin_token')
      localStorage.removeItem('admin_user')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
      return Promise.reject(error)
    }
    const message = error.response?.data?.message || error.message || '网络异常'
    ElMessage.error(message)
    return Promise.reject(error)
  }
)
