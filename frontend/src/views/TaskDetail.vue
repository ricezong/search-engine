<template>
  <div class="task-detail-page">
    <!-- Back Navigation -->
    <div class="back-nav">
      <button class="btn-back" @click="router.push('/')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M19 12H5M12 19l-7-7 7-7"/>
        </svg>
        <span>返回任务列表</span>
      </button>
    </div>

    <!-- Loading Skeleton -->
    <div v-if="!task" class="loading-skeleton">
      <div class="skeleton-block" v-for="i in 3" :key="i">
        <div class="skeleton-line w-60 h-24"></div>
        <div class="skeleton-grid">
          <div class="skeleton-box" v-for="j in 4" :key="j"></div>
        </div>
      </div>
    </div>

    <template v-else>
      <!-- Page Header -->
      <div class="detail-header">
        <div class="header-content">
          <div class="header-icon" :style="{ background: getStatusGradient(task.status) }">
            <svg v-if="task.status === 'RUNNING'" class="icon-spin" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/>
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M12 6v6l4 2"/>
            </svg>
          </div>
          <div class="header-info">
            <h1 class="detail-title">{{ task.taskName }}</h1>
            <div class="detail-meta">
              <span class="meta-item">
                <svg viewBox="0 0 16 16" fill="currentColor">
                  <path d="M4 4a2 2 0 012-2h4a2 2 0 012 2v2H4V4zm0 4h8v5a1 1 0 01-1 1H5a1 1 0 01-1-1V8z"/>
                </svg>
                {{ task.taskId.slice(0, 16) }}...
              </span>
              <span class="meta-sep">|</span>
              <span class="meta-item">
                <svg viewBox="0 0 16 16" fill="currentColor">
                  <path d="M8 3.5a.5.5 0 01.5.5v4h2.5a.5.5 0 010 1h-3a.5.5 0 01-.5-.5V4a.5.5 0 01.5-.5z"/>
                  <path d="M8 16A8 8 0 118 0a8 8 0 010 16zm7-8A7 7 0 101 8a7 7 0 0014 0z"/>
                </svg>
                {{ formatTime(task.createTime) }}
              </span>
            </div>
          </div>
        </div>
        <div class="header-status">
          <span class="status-badge-lg" :class="'status-' + task.status.toLowerCase()">
            <span class="status-dot"></span>
            {{ statusLabel(task.status) }}
          </span>
        </div>
      </div>

      <!-- Stats Dashboard -->
      <div class="stats-dashboard">
        <div class="stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #e07a5f, #f2cc8f)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
              <line x1="16" y1="13" x2="8" y2="13"/>
              <line x1="16" y1="17" x2="8" y2="17"/>
              <polyline points="10 9 9 9 8 9"/>
            </svg>
          </div>
          <div class="stat-content">
            <span class="stat-value">{{ task.crawledCount }}</span>
            <span class="stat-label">已爬取页面</span>
          </div>
          <div class="stat-trend up">
            <svg viewBox="0 0 16 16" fill="currentColor">
              <path d="M8 4l4 4H4l4-4z"/>
            </svg>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #81b29a, #a8d5ba)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="8"/>
              <path d="M21 21l-4.35-4.35"/>
            </svg>
          </div>
          <div class="stat-content">
            <span class="stat-value">{{ task.analyzedCount }}</span>
            <span class="stat-label">已分析内容</span>
          </div>
          <div class="stat-trend up">
            <svg viewBox="0 0 16 16" fill="currentColor">
              <path d="M8 4l4 4H4l4-4z"/>
            </svg>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #f2cc8f, #e07a5f)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
            </svg>
          </div>
          <div class="stat-content">
            <span class="stat-value">{{ task.indexedTermCount }}</span>
            <span class="stat-label">索引词数</span>
          </div>
          <div class="stat-trend up">
            <svg viewBox="0 0 16 16" fill="currentColor">
              <path d="M8 4l4 4H4l4-4z"/>
            </svg>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #636e72, #b2bec3)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M12 6v6l4 2"/>
            </svg>
          </div>
          <div class="stat-content">
            <span class="stat-value">{{ task.maxPages }}</span>
            <span class="stat-label">目标页面</span>
          </div>
          <div class="stat-progress">
            <div class="progress-ring">
              <svg viewBox="0 0 36 36">
                <path class="ring-bg" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"/>
                <path class="ring-fill" :stroke-dasharray="getProgress(task) + ', 100'" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"/>
              </svg>
              <span class="ring-text">{{ getProgress(task) }}%</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Progress Section (Running) -->
      <div v-if="task.status === 'RUNNING'" class="progress-panel">
        <div class="progress-header">
          <div class="progress-info">
            <span class="progress-title">实时进度</span>
            <span class="progress-rate">{{ getProgress(task) }}%</span>
          </div>
          <div class="progress-time">
            <svg viewBox="0 0 16 16" fill="currentColor">
              <path d="M8 3.5a.5.5 0 01.5.5v4h2.5a.5.5 0 010 1h-3a.5.5 0 01-.5-.5V4a.5.5 0 01.5-.5z"/>
              <path d="M8 16A8 8 0 118 0a8 8 0 010 16zm7-8A7 7 0 101 8a7 7 0 0014 0z"/>
            </svg>
            <span>{{ formatDuration(task.startTime) }}</span>
          </div>
        </div>
        <div class="progress-track">
          <div class="progress-glow"></div>
          <div class="progress-fill" :style="{ width: getProgress(task) + '%' }"></div>
        </div>
        <div class="progress-stats">
          <div class="progress-stat">
            <span class="stat-num">{{ task.crawledCount }}</span>
            <span class="stat-name">爬取中</span>
          </div>
          <div class="progress-stat">
            <span class="stat-num">{{ task.analyzedCount }}</span>
            <span class="stat-name">分析中</span>
          </div>
          <div class="progress-stat">
            <span class="stat-num">{{ task.indexedTermCount }}</span>
            <span class="stat-name">已索引</span>
          </div>
        </div>
      </div>

      <!-- Info Cards Grid -->
      <div class="info-grid">
        <!-- Basic Info -->
        <div class="info-card">
          <div class="card-header">
            <svg class="card-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M12 16v-4M12 8h.01"/>
            </svg>
            <span class="card-title">基本信息</span>
          </div>
          <div class="card-content">
            <div class="info-row">
              <span class="info-label">任务ID</span>
              <span class="info-value mono">{{ task.taskId }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">创建时间</span>
              <span class="info-value">{{ formatTime(task.createTime) }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">更新时间</span>
              <span class="info-value">{{ formatTime(task.updateTime) }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">最大页面</span>
              <span class="info-value">{{ task.maxPages }}</span>
            </div>
          </div>
        </div>

        <!-- Seed URLs -->
        <div class="info-card">
          <div class="card-header">
            <svg class="card-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M10 13a5 5 0 007.54.54l3-3a5 5 0 00-7.07-7.07l-1.72 1.71"/>
              <path d="M14 11a5 5 0 00-7.54-.54l-3 3a5 5 0 007.07 7.07l1.71-1.71"/>
            </svg>
            <span class="card-title">种子URL</span>
          </div>
          <div class="card-content">
            <div v-if="task.seedUrls && task.seedUrls.length > 0" class="url-list">
              <a v-for="url in task.seedUrls" :key="url" :href="url" target="_blank" class="url-item">
                <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">
                  <path d="M6 8l4 4M10.5 5.5a3.5 3.5 0 01-5 5l1.5 1.5a3.5 3.5 0 015-5"/>
                  <path d="M5.5 10.5a3.5 3.5 0 015-5l-1.5-1.5a3.5 3.5 0 01-5 5"/>
                </svg>
                <span>{{ url }}</span>
              </a>
            </div>
            <div v-else class="empty-text">暂无种子URL</div>
          </div>
        </div>

        <!-- Detailed Stats -->
        <div v-if="stats" class="info-card full-width">
          <div class="card-header">
            <svg class="card-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="20" x2="18" y2="10"/>
              <line x1="12" y1="20" x2="12" y2="4"/>
              <line x1="6" y1="20" x2="6" y2="14"/>
            </svg>
            <span class="card-title">组件状态</span>
          </div>
          <div class="card-content">
            <div class="component-grid">
              <div class="component-item">
                <div class="component-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21 12a9 9 0 01-9 9m9-9a9 9 0 00-9-9m9 9H3m9 9a9 9 0 01-9-9m9 9c1.657 0 3-4.03 3-9s-1.343-9-3-9m0 18c-1.657 0-3-4.03-3-9s1.343-9 3-9m-9 9a9 9 0 019-9"/>
                  </svg>
                </div>
                <div class="component-info">
                  <span class="component-name">爬虫模块</span>
                  <span class="component-status" :class="'status-' + stats.crawler?.toLowerCase()">{{ stats.crawler }}</span>
                </div>
              </div>
              <div class="component-item">
                <div class="component-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="11" cy="11" r="8"/>
                    <path d="M21 21l-4.35-4.35"/>
                  </svg>
                </div>
                <div class="component-info">
                  <span class="component-name">分析模块</span>
                  <span class="component-status" :class="'status-' + stats.analyzer?.toLowerCase()">{{ stats.analyzer }}</span>
                </div>
              </div>
              <div class="component-item">
                <div class="component-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polygon points="12 2 2 7 12 12 22 7 12 2"/>
                    <polyline points="2 17 12 22 22 17"/>
                    <polyline points="2 12 12 17 22 12"/>
                  </svg>
                </div>
                <div class="component-info">
                  <span class="component-name">索引模块</span>
                  <span class="component-status" :class="'status-' + stats.indexer?.toLowerCase()">{{ stats.indexer }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Error Banner -->
      <div v-if="task.errorMessage" class="error-banner">
        <svg class="error-icon" viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
        </svg>
        <div class="error-content">
          <span class="error-title">任务错误</span>
          <span class="error-message">{{ task.errorMessage }}</span>
        </div>
      </div>

      <!-- Terminal Log -->
      <div class="terminal-section">
        <div class="terminal-header">
          <div class="terminal-dots">
            <span class="dot dot-1"></span>
            <span class="dot dot-2"></span>
            <span class="dot dot-3"></span>
          </div>
          <span class="terminal-title">执行日志</span>
          <button class="terminal-refresh" @click="loadLogs">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M23 4v6h-6M1 20v-6h6"/>
              <path d="M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"/>
            </svg>
            <span>刷新</span>
          </button>
        </div>
        <div class="terminal-body">
          <div v-if="logs.length === 0" class="terminal-empty">
            <span class="cursor">_</span> 等待日志输出...
          </div>
          <div v-else class="log-entries">
            <div v-for="(log, index) in logs" :key="index" class="log-entry" :class="'level-' + log.level?.toLowerCase()">
              <span class="log-num">{{ String(index + 1).padStart(3, '0') }}</span>
              <span class="log-time">{{ log.time }}</span>
              <span class="log-badge" :class="'badge-' + log.level?.toLowerCase()">{{ log.level }}</span>
              <span class="log-msg">{{ log.message }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Action Buttons -->
      <div class="action-section">
        <button
            class="action-btn primary"
            :disabled="task.status === 'RUNNING'"
            @click="doAction('full')"
        >
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M8 5v14l11-7z"/>
          </svg>
          <span>开始挖掘</span>
        </button>
        <button
            v-if="task.status === 'RUNNING'"
            class="action-btn warning"
            @click="doStop"
        >
          <svg viewBox="0 0 24 24" fill="currentColor">
            <rect x="6" y="6" width="12" height="12" rx="2"/>
          </svg>
          <span>停止任务</span>
        </button>
        <button
            class="action-btn"
            @click="router.push(`/search?taskId=${task.taskId}`)"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"/>
            <path d="M21 21l-4.35-4.35"/>
          </svg>
          <span>搜索内容</span>
        </button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as api from '../api'

const route = useRoute()
const router = useRouter()
const taskId = route.params.id
const task = ref(null)
const stats = ref(null)
const logs = ref([])
let refreshTimer = null

const getProgress = (task) => {
  if (!task.maxPages) return 0
  return Math.min(100, Math.round((task.crawledCount / task.maxPages) * 100))
}

function statusLabel(status) {
  const map = {
    PENDING: '待执行',
    RUNNING: '运行中',
    COMPLETED: '已完成',
    FAILED: '失败',
    STOPPED: '已停止'
  }
  return map[status] || status
}

function getStatusGradient(status) {
  const gradients = {
    PENDING: 'linear-gradient(135deg, #a0a0a0, #c0c0c0)',
    RUNNING: 'linear-gradient(135deg, #e07a5f, #f2cc8f)',
    COMPLETED: 'linear-gradient(135deg, #81b29a, #a8d5ba)',
    FAILED: 'linear-gradient(135deg, #e57373, #ef9a9a)',
    STOPPED: 'linear-gradient(135deg, #ffb74d, #ffe082)'
  }
  return gradients[status] || gradients.PENDING
}

function formatTime(ts) {
  if (!ts) return '-'
  return new Date(ts).toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function formatDuration(startTime) {
  if (!startTime) return '00:00:00'
  const now = Date.now()
  const start = new Date(startTime).getTime()
  const diff = Math.max(0, Math.floor((now - start) / 1000))
  const h = Math.floor(diff / 3600).toString().padStart(2, '0')
  const m = Math.floor((diff % 3600) / 60).toString().padStart(2, '0')
  const s = (diff % 60).toString().padStart(2, '0')
  return `${h}:${m}:${s}`
}

async function loadTask() {
  try {
    const res = await api.getTask(taskId)
    if (res.data.status === 'success') {
      task.value = res.data.task
    }
  } catch (e) {
    console.error('加载任务详情失败', e)
  }
}

async function loadStats() {
  try {
    const res = await api.getTaskStats(taskId)
    if (res.data.status === 'success') {
      stats.value = res.data
    }
  } catch (e) {
    // Stats may not be available
  }
}

async function loadLogs() {
  try {
    const res = await api.getTaskLogs(taskId)
    if (res.data.status === 'success') {
      logs.value = res.data.logs || []
    }
  } catch (e) {
    // Logs may not be available
  }
}

async function doAction(action) {
  try {
    const actionMap = {
      crawl: api.startCrawl,
      analyze: api.startAnalyze,
      index: api.startIndex,
      full: api.startFull
    }
    const fn = actionMap[action]
    if (!fn) return
    const res = await fn(taskId)
    if (res.data.status === 'started') {
      ElMessage.success(res.data.message)
      setTimeout(() => { loadTask(); loadStats(); loadLogs() }, 500)
    } else {
      ElMessage.error(res.data.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('操作失败: ' + (e.response?.data?.message || e.message))
  }
}

async function doStop() {
  try {
    const res = await api.stopTask(taskId)
    if (res.data.status === 'success') {
      ElMessage.success('任务已停止')
      loadTask()
    }
  } catch (e) {
    ElMessage.error('停止失败')
  }
}

onMounted(() => {
  loadTask()
  loadStats()
  loadLogs()
  refreshTimer = setInterval(() => {
    loadTask()
    loadStats()
    loadLogs()
  }, 3000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style scoped>
/* ============ Back Navigation ============ */
.back-nav {
  margin-bottom: 32px;
}

.btn-back {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  background: white;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.25s ease;
  box-shadow: var(--shadow-soft);
}

.btn-back:hover {
  transform: translateX(-4px);
  border-color: var(--accent-primary);
  color: var(--accent-primary);
}

.btn-back svg {
  width: 18px;
  height: 18px;
}

/* ============ Loading Skeleton ============ */
.loading-skeleton {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.skeleton-block {
  background: white;
  border-radius: var(--radius-lg);
  padding: 24px;
}

.skeleton-line {
  background: linear-gradient(90deg, var(--bg-tertiary) 25%, var(--bg-secondary) 50%, var(--bg-tertiary) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: var(--radius-sm);
}

.skeleton-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-top: 24px;
}

.skeleton-box {
  height: 80px;
  background: linear-gradient(90deg, var(--bg-tertiary) 25%, var(--bg-secondary) 50%, var(--bg-tertiary) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: var(--radius-sm);
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* ============ Detail Header ============ */
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 32px;
  background: white;
  border-radius: var(--radius-xl);
  padding: 28px;
  box-shadow: var(--shadow-soft);
}

.header-content {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.header-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
}

.header-icon svg {
  width: 28px;
  height: 28px;
}

.icon-spin {
  animation: spin 2s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.header-info {
  flex: 1;
}

.detail-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
  letter-spacing: -0.5px;
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-muted);
}

.meta-item svg {
  width: 14px;
  height: 14px;
}

.meta-sep {
  color: var(--border-medium);
}

.header-status {
  flex-shrink: 0;
}

.status-badge-lg {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: var(--radius-full);
  font-size: 13px;
  font-weight: 600;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-pending {
  background: rgba(0, 0, 0, 0.05);
  color: var(--text-secondary);
}
.status-pending .status-dot { background: var(--text-secondary); }

.status-running {
  background: rgba(224, 122, 95, 0.1);
  color: var(--accent-primary);
}
.status-running .status-dot { background: var(--accent-primary); animation: pulse 1.5s ease-in-out infinite; }

.status-completed {
  background: rgba(129, 178, 154, 0.15);
  color: var(--accent-secondary);
}
.status-completed .status-dot { background: var(--accent-secondary); }

.status-failed {
  background: rgba(229, 115, 115, 0.1);
  color: #e57373;
}
.status-failed .status-dot { background: #e57373; }

.status-stopped {
  background: rgba(255, 183, 77, 0.15);
  color: #ff9800;
}
.status-stopped .status-dot { background: #ff9800; }

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

/* ============ Stats Dashboard ============ */
.stats-dashboard {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 28px;
}

.stat-card {
  background: white;
  border-radius: var(--radius-lg);
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: var(--shadow-soft);
  transition: all 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-medium);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: white;
}

.stat-icon svg {
  width: 22px;
  height: 22px;
}

.stat-content {
  flex: 1;
}

.stat-value {
  display: block;
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.2;
}

.stat-label {
  font-size: 12px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.stat-trend {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-trend.up {
  background: rgba(129, 178, 154, 0.15);
  color: var(--accent-secondary);
}

.stat-trend svg {
  width: 12px;
  height: 12px;
}

.stat-progress {
  flex-shrink: 0;
}

.progress-ring {
  position: relative;
  width: 48px;
  height: 48px;
}

.progress-ring svg {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.ring-bg {
  fill: none;
  stroke: var(--bg-tertiary);
  stroke-width: 3;
}

.ring-fill {
  fill: none;
  stroke: var(--accent-primary);
  stroke-width: 3;
  stroke-linecap: round;
  transition: stroke-dasharray 0.5s ease;
}

.ring-text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 600;
  color: var(--text-primary);
}

/* ============ Progress Panel ============ */
.progress-panel {
  background: white;
  border-radius: var(--radius-xl);
  padding: 24px;
  margin-bottom: 28px;
  box-shadow: var(--shadow-soft);
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.progress-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.progress-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.progress-rate {
  font-size: 32px;
  font-weight: 700;
  background: linear-gradient(135deg, var(--accent-primary), var(--accent-tertiary));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.progress-time {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: var(--text-secondary);
  padding: 6px 12px;
  background: var(--bg-tertiary);
  border-radius: var(--radius-full);
}

.progress-time svg {
  width: 14px;
  height: 14px;
}

.progress-track {
  height: 12px;
  background: var(--bg-tertiary);
  border-radius: 6px;
  overflow: hidden;
  position: relative;
  margin-bottom: 20px;
}

.progress-glow {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.4), transparent);
  animation: shine 2s ease-in-out infinite;
}

@keyframes shine {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(100%); }
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--accent-primary), var(--accent-tertiary));
  border-radius: 6px;
  transition: width 0.5s ease;
  position: relative;
}

.progress-stats {
  display: flex;
  justify-content: space-around;
}

.progress-stat {
  text-align: center;
}

.stat-num {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}

.stat-name {
  font-size: 12px;
  color: var(--text-muted);
}

/* ============ Info Grid ============ */
.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  margin-bottom: 28px;
}

.info-card {
  background: white;
  border-radius: var(--radius-lg);
  padding: 20px;
  box-shadow: var(--shadow-soft);
}

.info-card.full-width {
  grid-column: 1 / -1;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-light);
}

.card-icon {
  width: 20px;
  height: 20px;
  color: var(--accent-primary);
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.card-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.info-label {
  font-size: 13px;
  color: var(--text-muted);
  flex-shrink: 0;
}

.info-value {
  font-size: 13px;
  color: var(--text-primary);
  text-align: right;
  word-break: break-all;
}

.info-value.mono {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-secondary);
}

.url-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.url-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--bg-tertiary);
  border-radius: var(--radius-sm);
  text-decoration: none;
  color: var(--text-primary);
  font-size: 13px;
  transition: all 0.2s ease;
}

.url-item:hover {
  background: rgba(224, 122, 95, 0.1);
  color: var(--accent-primary);
}

.url-item svg {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
  color: var(--text-muted);
}

.empty-text {
  color: var(--text-muted);
  font-size: 13px;
  text-align: center;
  padding: 20px;
}

/* Component Grid */
.component-grid {
  display: flex;
  gap: 16px;
}

.component-item {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--bg-tertiary);
  border-radius: var(--radius-md);
}

.component-icon {
  width: 36px;
  height: 36px;
  background: white;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent-primary);
}

.component-icon svg {
  width: 18px;
  height: 18px;
}

.component-info {
  flex: 1;
}

.component-name {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 2px;
}

.component-status {
  font-size: 12px;
  font-weight: 500;
}

.component-status.status-idle { color: var(--text-muted); }
.component-status.status-running { color: var(--accent-primary); }
.component-status.status-completed { color: var(--accent-secondary); }
.component-status.status-failed { color: #e57373; }

/* ============ Error Banner ============ */
.error-banner {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 16px 20px;
  background: rgba(229, 115, 115, 0.08);
  border: 1px solid rgba(229, 115, 115, 0.2);
  border-radius: var(--radius-lg);
  margin-bottom: 28px;
}

.error-icon {
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  color: #e57373;
}

.error-content {
  flex: 1;
}

.error-title {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #e57373;
  margin-bottom: 4px;
}

.error-message {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
}

/* ============ Terminal Section ============ */
.terminal-section {
  background: #1e1e2e;
  border-radius: var(--radius-xl);
  overflow: hidden;
  margin-bottom: 28px;
}

.terminal-header {
  display: flex;
  align-items: center;
  padding: 14px 18px;
  background: rgba(255, 255, 255, 0.05);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.terminal-dots {
  display: flex;
  gap: 8px;
  margin-right: 16px;
}

.dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.dot-1 { background: #ff5f57; }
.dot-2 { background: #febc2e; }
.dot-3 { background: #28c840; }

.terminal-title {
  flex: 1;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.5);
  font-family: var(--font-mono);
}

.terminal-refresh {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: rgba(255, 255, 255, 0.1);
  border: none;
  border-radius: var(--radius-sm);
  color: rgba(255, 255, 255, 0.6);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.terminal-refresh:hover {
  background: rgba(255, 255, 255, 0.15);
  color: white;
}

.terminal-refresh svg {
  width: 14px;
  height: 14px;
}

.terminal-body {
  max-height: 320px;
  overflow-y: auto;
  padding: 16px;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.6;
}

.terminal-empty {
  color: rgba(255, 255, 255, 0.4);
  text-align: center;
  padding: 32px;
}

.cursor {
  animation: blink 1s step-end infinite;
  color: var(--accent-secondary);
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.log-entries {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.log-entry {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 6px 8px;
  border-radius: var(--radius-sm);
  transition: background 0.15s ease;
}

.log-entry:hover {
  background: rgba(255, 255, 255, 0.05);
}

.log-num {
  color: rgba(255, 255, 255, 0.2);
  min-width: 28px;
}

.log-time {
  color: rgba(106, 153, 85, 0.6);
  white-space: nowrap;
  min-width: 70px;
}

.log-badge {
  padding: 0 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  min-width: 50px;
  text-align: center;
}

.badge-info {
  background: rgba(86, 156, 214, 0.2);
  color: #569cd6;
}

.badge-warn {
  background: rgba(220, 220, 170, 0.2);
  color: #dcdcaa;
}

.badge-error {
  background: rgba(244, 71, 71, 0.2);
  color: #f44747;
}

.level-error .log-msg {
  color: #f44747;
}

.log-msg {
  flex: 1;
  color: rgba(255, 255, 255, 0.6);
  word-break: break-all;
}

/* ============ Action Section ============ */
.action-section {
  display: flex;
  gap: 12px;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 28px;
  border-radius: var(--radius-md);
  border: none;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s ease;
}

.action-btn svg {
  width: 18px;
  height: 18px;
}

.action-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
  transform: none !important;
}

.action-btn.primary {
  background: linear-gradient(135deg, var(--accent-primary), #c96a51);
  color: white;
  box-shadow: 0 4px 16px rgba(224, 122, 95, 0.3);
}

.action-btn.primary:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(224, 122, 95, 0.4);
}

.action-btn.warning {
  background: linear-gradient(135deg, #f2cc8f, #e07a5f);
  color: white;
  box-shadow: 0 4px 16px rgba(242, 204, 143, 0.3);
}

.action-btn.warning:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(242, 204, 143, 0.4);
}

.action-btn {
  background: white;
  border: 1px solid var(--border-medium);
  color: var(--text-primary);
  box-shadow: var(--shadow-soft);
}

.action-btn:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-medium);
  border-color: var(--accent-primary);
  color: var(--accent-primary);
}

/* ============ Responsive ============ */
@media (max-width: 1024px) {
  .stats-dashboard {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .detail-header {
    flex-direction: column;
    gap: 20px;
  }

  .detail-title {
    font-size: 24px;
  }

  .stats-dashboard {
    grid-template-columns: 1fr;
  }

  .stat-card {
    padding: 16px;
  }

  .stat-value {
    font-size: 24px;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }

  .info-card.full-width {
    grid-column: 1;
  }

  .component-grid {
    flex-direction: column;
  }

  .action-section {
    flex-direction: column;
  }

  .action-btn {
    justify-content: center;
  }

  .skeleton-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 480px) {
  .back-nav {
    margin-bottom: 20px;
  }

  .btn-back {
    width: 100%;
    justify-content: center;
  }

  .detail-header {
    padding: 20px;
  }

  .header-icon {
    width: 48px;
    height: 48px;
  }

  .header-icon svg {
    width: 24px;
    height: 24px;
  }

  .progress-rate {
    font-size: 28px;
  }

  .progress-stats {
    gap: 16px;
  }
}
</style>