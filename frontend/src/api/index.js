import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

api.interceptors.response.use(
    (response) => response,
    (error) => {
      const message = error.response?.data?.message || error.message || '请求失败'
      console.error('API Error:', message)
      ElMessage.error(message)
      return Promise.reject(error)
    }
)

export function listTasks() {
  return api.get('/tasks')
}

export function getTask(taskId) {
  return api.get(`/tasks/${taskId}`)
}

export function createTask(data) {
  return api.post('/tasks', data)
}

export function updateTask(taskId, data) {
  return api.put(`/tasks/${taskId}`, data)
}

export function deleteTask(taskId) {
  return api.delete(`/tasks/${taskId}`)
}

export function startCrawl(taskId) {
  return api.post(`/tasks/${taskId}/crawl`)
}

export function startAnalyze(taskId) {
  return api.post(`/tasks/${taskId}/analyze`)
}

export function startIndex(taskId) {
  return api.post(`/tasks/${taskId}/index`)
}

export function startFull(taskId) {
  return api.post(`/tasks/${taskId}/full`)
}

export function stopTask(taskId) {
  return api.post(`/tasks/${taskId}/stop`)
}

export function getTaskStats(taskId) {
  return api.get(`/tasks/${taskId}/stats`)
}

export function getTaskLogs(taskId) {
  return api.get(`/tasks/${taskId}/logs`)
}

export function search(taskId, query, page = 1) {
  return api.get(`/tasks/${taskId}/search`, { params: { query, page } })
}

export function getKeywords(taskId, limit = 20) {
  return api.get(`/tasks/${taskId}/keywords`, { params: { limit } })
}

export default api