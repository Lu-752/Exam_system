import axios from 'axios'
import { ElMessage } from 'element-plus'

const TOKEN_KEY = 'exam_token'

export const http = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (res) => {
    const body = res.data
    // 统一响应体：{ code, message, data }
    if (body && typeof body.code === 'number') {
      if (body.code !== 0) {
        return Promise.reject(new Error(body.message || '请求失败'))
      }
      return body.data
    }
    return body
  },
  (err) => {
    const status = err.response?.status
    const message = err.response?.data?.message || err.message || '网络异常'

    if (status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem('exam_user')
      // 避免在登录页重复跳转与重复提示
      if (!location.hash.includes('/login')) {
        ElMessage.warning('登录状态已失效，请重新登录')
        location.hash = '#/login'
      }
      return Promise.reject(new Error(message))
    }

    if (status === 403) {
      // 交给调用方统一提示，这里只把文案说得更人话
      const msg = message === '没有操作权限' ? '当前账号没有该操作权限' : message
      return Promise.reject(new Error(msg))
    }

    if (!err.response) {
      ElMessage.error('网络异常，请确认服务已启动后重试')
      return Promise.reject(new Error('网络异常'))
    }

    return Promise.reject(new Error(message))
  }
)
