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
      <!-- 基本信息 -->
      <el-card class="info-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <span>基本信息</span>
            <el-tag :type="statusType(task.status)" size="large">{{ statusLabel(task.status) }}</el-tag>
          </div>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="任务ID">{{ task.taskId }}</el-descriptions-item>
          <el-descriptions-item label="任务名称">{{ task.taskName }}</el-descriptions-item>
          <el-descriptions-item label="最大页面数">{{ task.maxPages }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(task.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatTime(task.updateTime) }}</el-descriptions-item>
          <el-descriptions-item label="错误信息">{{ task.errorMessage || '无' }}</el-descriptions-item>
          <el-descriptions-item label="种子URL" :span="2">
            <el-tag v-for="url in (task.seedUrls || [])" :key="url" size="small" type="info" style="margin: 2px 4px;">{{ url }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 统计数据 -->
      <el-card class="info-card" shadow="hover">
        <template #header><span>统计数据</span></template>
        <div class="stats-grid">
          <div class="stat-item">
            <div class="stat-value">{{ task.crawledCount }}</div>
            <div class="stat-label">已爬取页面</div>
          </div>
          <div class="stat-item">
            <div class="stat-value">{{ task.analyzedCount }}</div>
            <div class="stat-label">已分析文档</div>
          </div>
          <div class="stat-item">
            <div class="stat-value">{{ task.indexedTermCount }}</div>
            <div class="stat-label">索引词数</div>
          </div>
        </div>
      </el-card>

      <!-- 详细统计（从API获取） -->
      <el-card v-if="stats" class="info-card" shadow="hover">
        <template #header><span>详细统计</span></template>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="爬虫状态">{{ stats.crawler }}</el-descriptions-item>
          <el-descriptions-item label="分析状态">{{ stats.analyzer }}</el-descriptions-item>
          <el-descriptions-item label="索引状态">{{ stats.indexer }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 操作按钮 -->
      <el-card class="info-card" shadow="hover">
        <template #header><span>操作</span></template>
        <div class="action-buttons">
          <el-button type="success" :disabled="task.status === 'RUNNING'" @click="doAction('full')">全流程执行</el-button>
          <el-button :disabled="task.status === 'RUNNING'" @click="doAction('crawl')">爬取</el-button>
          <el-button :disabled="task.status === 'RUNNING'" @click="doAction('analyze')">分析</el-button>
          <el-button :disabled="task.status === 'RUNNING'" @click="doAction('index')">构建索引</el-button>
          <el-button v-if="task.status === 'RUNNING'" type="warning" @click="doStop">停止</el-button>
          <el-button type="info" @click="router.push(`/search?taskId=${task.taskId}`)">去搜索</el-button>
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as api from '../api'

const route = useRoute()
const router = useRouter()
const taskId = route.params.id
const task = ref(null)
const stats = ref(null)
let refreshTimer = null

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
    // 统计信息可能不可用（任务还未开始）
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
      setTimeout(() => { loadTask(); loadStats() }, 500)
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
  refreshTimer = setInterval(() => {
    loadTask()
    loadStats()
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

.stats-grid {
  display: flex;
  gap: 32px;
  justify-content: center;
  padding: 16px 0;
}

.stat-item {
  text-align: center;
}

.stat-value {
  font-size: 36px;
  font-weight: 700;
  color: #409eff;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 8px;
}

.action-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
</style>