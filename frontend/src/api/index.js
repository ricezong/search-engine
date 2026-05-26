import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 任务管理
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

// 任务操作
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

// 搜索
export function search(taskId, query, page = 1) {
  return api.get(`/tasks/${taskId}/search`, { params: { query, page } })
}

// 热门关键词
export function getKeywords(taskId, limit = 20) {
  return api.get(`/tasks/${taskId}/keywords`, { params: { limit } })
}