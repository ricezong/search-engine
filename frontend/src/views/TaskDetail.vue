<template>
  <div class="task-detail-page">
    <div class="page-header">
      <el-button @click="router.push('/')" text>← 返回任务列表</el-button>
      <h2>{{ task?.taskName || '任务详情' }}</h2>
    </div>

    <div v-if="!task" class="loading">
      <el-skeleton :rows="6" animated />
    </div>

    <template v-else>
      <!-- 状态卡片 -->
      <el-card class="info-card" shadow="hover">
        <div class="status-header">
          <el-tag :type="statusType(task.status)" size="large">{{ statusLabel(task.status) }}</el-tag>
          <span class="task-id">#{{ task.taskId }}</span>
        </div>

        <!-- 进度条 -->
        <div v-if="task.status === 'RUNNING'" class="progress-section">
          <el-progress :percentage="progressPercent" :stroke-width="20" :text-inside="true" />
          <div class="progress-detail">
            爬取: {{ task.crawledCount }}/{{ task.maxPages }} |
            分析: {{ task.analyzedCount }} |
            索引: {{ task.indexedTermCount }} 词
          </div>
        </div>

        <!-- 统计数据 -->
        <div class="stats-grid">
          <div class="stat-item">
            <div class="stat-value">{{ task.crawledCount }}</div>
            <div class="stat-label">已爬取</div>
          </div>
          <div class="stat-item">
            <div class="stat-value">{{ task.analyzedCount }}</div>
            <div class="stat-label">已分析</div>
          </div>
          <div class="stat-item">
            <div class="stat-value">{{ task.indexedTermCount }}</div>
            <div class="stat-label">索引词数</div>
          </div>
          <div class="stat-item">
            <div class="stat-value">{{ task.maxPages }}</div>
            <div class="stat-label">目标页数</div>
          </div>
        </div>

        <!-- 错误信息 -->
        <div v-if="task.errorMessage" class="task-error">
          <el-alert :title="task.errorMessage" type="error" :closable="false" show-icon />
        </div>
      </el-card>

      <!-- 基本信息 -->
      <el-card class="info-card" shadow="hover">
        <template #header><span>基本信息</span></template>
        <el-descriptions :column="responsiveColumns" border>
          <el-descriptions-item label="任务名称">{{ task.taskName }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(task.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatTime(task.updateTime) }}</el-descriptions-item>
          <el-descriptions-item label="种子URL" :span="responsiveColumns">
            <el-tag v-for="url in (task.seedUrls || [])" :key="url" size="small" type="info" class="url-tag">{{ url }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 详细统计 -->
      <el-card v-if="stats" class="info-card" shadow="hover">
        <template #header><span>详细统计</span></template>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="爬虫状态">{{ stats.crawler }}</el-descriptions-item>
          <el-descriptions-item label="分析状态">{{ stats.analyzer }}</el-descriptions-item>
          <el-descriptions-item label="索引状态">{{ stats.indexer }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 执行日志 -->
      <el-card class="info-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <span>执行日志</span>
            <el-button size="small" @click="loadLogs">刷新</el-button>
          </div>
        </template>
        <div class="log-container">
          <div v-if="logs.length === 0" class="log-empty">暂无日志</div>
          <div v-else class="log-list">
            <div v-for="(log, index) in logs" :key="index" class="log-item" :class="logClass(log)">
              <span class="log-time">{{ log.time }}</span>
              <span class="log-level">{{ log.level }}</span>
              <span class="log-message">{{ log.message }}</span>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 操作按钮 -->
      <el-card class="info-card" shadow="hover">
        <template #header><span>操作</span></template>
        <div class="action-buttons">
          <el-button type="success" :disabled="task.status === 'RUNNING'" @click="doAction('full')">
            <el-icon><VideoPlay /></el-icon> 开始挖掘
          </el-button>
          <el-button v-if="task.status === 'RUNNING'" type="warning" @click="doStop">
            <el-icon><VideoPause /></el-icon> 停止
          </el-button>
          <el-button type="info" @click="router.push(`/search?taskId=${task.taskId}`)">
            <el-icon><Search /></el-icon> 去搜索
          </el-button>
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { VideoPlay, VideoPause, Search } from '@element-plus/icons-vue'
import * as api from '../api'

const route = useRoute()
const router = useRouter()
const taskId = route.params.id
const task = ref(null)
const stats = ref(null)
const logs = ref([])
let refreshTimer = null

// 响应式列数
const responsiveColumns = computed(() => {
  return window.innerWidth < 768 ? 1 : 2
})

// 进度百分比
const progressPercent = computed(() => {
  if (!task.value || task.value.maxPages === 0) return 0
  return Math.min(100, Math.round((task.value.crawledCount / task.value.maxPages) * 100))
})

function statusType(status) {
  const map = { PENDING: 'info', RUNNING: 'primary', COMPLETED: 'success', FAILED: 'danger', STOPPED: 'warning' }
  return map[status] || 'info'
}

function statusLabel(status) {
  const map = { PENDING: '待执行', RUNNING: '运行中', COMPLETED: '已完成', FAILED: '失败', STOPPED: '已停止' }
  return map[status] || status
}

function formatTime(ts) {
  if (!ts) return '-'
  return new Date(ts).toLocaleString('zh-CN')
}

function logClass(log) {
  if (log.level === 'ERROR') return 'log-error'
  if (log.level === 'WARN') return 'log-warn'
  return 'log-info'
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
    // 统计信息可能不可用
  }
}

async function loadLogs() {
  try {
    const res = await api.getTaskLogs(taskId)
    if (res.data.status === 'success') {
      logs.value = res.data.logs || []
    }
  } catch (e) {
    // 日志可能不可用
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
.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin-top: 8px;
}

.info-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.status-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.task-id {
  color: #909399;
  font-size: 14px;
}

.progress-section {
  margin-bottom: 20px;
}

.progress-detail {
  margin-top: 8px;
  color: #606266;
  font-size: 14px;
  text-align: center;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  padding: 16px 0;
}

.stat-item {
  text-align: center;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #409eff;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}

.task-error {
  margin-top: 12px;
}

.url-tag {
  margin: 2px 4px;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.log-container {
  max-height: 400px;
  overflow-y: auto;
  background: #1e1e1e;
  border-radius: 8px;
  padding: 12px;
}

.log-empty {
  color: #909399;
  text-align: center;
  padding: 20px;
}

.log-list {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
}

.log-item {
  padding: 4px 0;
  border-bottom: 1px solid #333;
  display: flex;
  gap: 8px;
}

.log-time {
  color: #6a9955;
  white-space: nowrap;
}

.log-level {
  white-space: nowrap;
  min-width: 50px;
}

.log-message {
  flex: 1;
  word-break: break-all;
}

.log-info .log-level {
  color: #569cd6;
}

.log-warn .log-level {
  color: #dcdcaa;
}

.log-error .log-level {
  color: #f44747;
}

.log-error .log-message {
  color: #f44747;
}

.action-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

/* 手机端适配 */
@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .stat-value {
    font-size: 20px;
  }

  .url-tag {
    max-width: 100%;
    display: block;
    margin: 4px 0;
  }

  .log-container {
    max-height: 300px;
  }

  .log-item {
    flex-direction: column;
    gap: 2px;
  }

  .action-buttons {
    justify-content: center;
  }
}
</style>
