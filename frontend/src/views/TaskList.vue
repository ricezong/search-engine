<template>
  <div class="task-list-page">
    <!-- Page Header -->
    <div class="page-header">
      <div class="header-left">
        <div class="header-badge">
          <svg class="badge-icon" viewBox="0 0 20 20" fill="currentColor">
            <path d="M10 2L13 7H18L14 11L15.5 17L10 13.5L4.5 17L6 11L2 7H7L10 2Z"/>
          </svg>
          <span>{{ tasks.length }} 个任务</span>
        </div>
        <h1 class="page-title">任务管理中心</h1>
        <p class="page-subtitle">创建、启动和管理你的网页爬取任务</p>
      </div>
      <button class="btn-create" @click="openCreate">
        <svg class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 5v14M5 12h14"/>
        </svg>
        <span>新建任务</span>
      </button>
    </div>

    <!-- Empty State -->
    <div v-if="tasks.length === 0" class="empty-state">
      <div class="empty-graphic">
        <div class="empty-circle"></div>
        <div class="empty-plus">+</div>
      </div>
      <h3>开始你的第一个任务</h3>
      <p>创建任务后，系统将自动爬取并索引网页内容</p>
      <button class="btn-create-empty" @click="openCreate">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 5v14M5 12h14"/>
        </svg>
        <span>创建任务</span>
      </button>
    </div>

    <!-- Task Grid -->
    <div v-else class="task-grid">
      <div
          v-for="task in tasks"
          :key="task.taskId"
          class="task-card"
          :class="{ 'is-running': task.status === 'RUNNING' }"
      >
        <!-- Card Header -->
        <div class="card-header">
          <div class="card-icon" :style="{ background: getStatusColor(task.status) }">
            <svg v-if="task.status === 'RUNNING'" class="icon-spin" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/>
            </svg>
            <svg v-else-if="task.status === 'COMPLETED'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20 6L9 17l-5-5"/>
            </svg>
            <svg v-else-if="task.status === 'FAILED'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M15 9l-6 6M9 9l6 6"/>
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M12 6v6l4 2"/>
            </svg>
          </div>
          <div class="card-title-area">
            <h3 class="card-title">{{ task.taskName }}</h3>
            <span class="card-id">{{ task.taskId.slice(0, 12) }}...</span>
          </div>
        </div>

        <!-- Status Badge -->
        <div class="status-row">
          <span class="status-badge" :class="'status-' + task.status.toLowerCase()">
            {{ statusLabel(task.status) }}
          </span>
          <span v-if="task.status === 'RUNNING'" class="live-indicator">
            <span class="live-dot"></span>
            实时监控中
          </span>
        </div>

        <!-- Stats Row -->
        <div class="stats-row">
          <div class="stat-item">
            <span class="stat-value">{{ task.crawledCount }}</span>
            <span class="stat-label">已爬取</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-value">{{ task.analyzedCount }}</span>
            <span class="stat-label">已分析</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-value">{{ task.indexedTermCount }}</span>
            <span class="stat-label">索引词</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-value">{{ task.maxPages }}</span>
            <span class="stat-label">目标</span>
          </div>
        </div>

        <!-- Progress Bar (for running tasks) -->
        <div v-if="task.status === 'RUNNING'" class="progress-section">
          <div class="progress-header">
            <span>爬取进度</span>
            <span class="progress-percent">{{ getProgress(task) }}%</span>
          </div>
          <div class="progress-track">
            <div class="progress-fill" :style="{ width: getProgress(task) + '%' }"></div>
          </div>
        </div>

        <!-- Seed URLs -->
        <div class="urls-section" v-if="task.seedUrls && task.seedUrls.length > 0">
          <span class="urls-label">种子链接</span>
          <div class="urls-list">
            <span v-for="url in task.seedUrls.slice(0, 2)" :key="url" class="url-tag">
              {{ formatUrl(url) }}
            </span>
            <span v-if="task.seedUrls.length > 2" class="url-more">
              +{{ task.seedUrls.length - 2 }}
            </span>
          </div>
        </div>

        <!-- Error Message -->
        <div v-if="task.errorMessage" class="error-alert">
          <svg class="error-icon" viewBox="0 0 20 20" fill="currentColor">
            <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd"/>
          </svg>
          <span>{{ task.errorMessage }}</span>
        </div>

        <!-- Card Footer -->
        <div class="card-footer">
          <span class="time-info">
            <svg viewBox="0 0 16 16" fill="currentColor">
              <path d="M8 3.5a.5.5 0 01.5.5v4h2.5a.5.5 0 010 1h-3a.5.5 0 01-.5-.5V4a.5.5 0 01.5-.5z"/>
              <path d="M8 16A8 8 0 118 0a8 8 0 010 16zm7-8A7 7 0 101 8a7 7 0 0014 0z"/>
            </svg>
            {{ formatTime(task.createTime) }}
          </span>
          <div class="card-actions">
            <button
                class="action-btn primary"
                :disabled="task.status === 'RUNNING'"
                @click="doAction(task)"
                title="启动任务"
            >
              <svg viewBox="0 0 24 24" fill="currentColor">
                <path d="M8 5v14l11-7z"/>
              </svg>
            </button>
            <button
                class="action-btn"
                @click="router.push(`/tasks/${task.taskId}`)"
                title="查看详情"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                <circle cx="12" cy="12" r="3"/>
              </svg>
            </button>
            <button
                class="action-btn"
                @click="router.push(`/search?taskId=${task.taskId}`)"
                title="搜索"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="8"/>
                <path d="M21 21l-4.35-4.35"/>
              </svg>
            </button>
            <button
                class="action-btn"
                :disabled="task.status === 'RUNNING'"
                @click="openEdit(task)"
                title="编辑"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/>
                <path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/>
              </svg>
            </button>
            <button
                class="action-btn danger"
                :disabled="task.status === 'RUNNING'"
                @click="doDelete(task.taskId)"
                title="删除"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M3 6h18M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/>
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Create/Edit Dialog -->
    <el-dialog
        v-model="showDialog"
        :title="isEdit ? '编辑任务' : '新建任务'"
        width="520px"
        class="task-dialog"
        :close-on-click-modal="false"
    >
      <el-form :model="form" label-width="100px" class="task-form">
        <el-form-item label="任务名称" required>
          <el-input v-model="form.taskName" placeholder="给任务起个名字" />
        </el-form-item>
        <el-form-item label="种子URL">
          <el-input
              v-model="form.seedUrlsText"
              type="textarea"
              :rows="3"
              placeholder="每行一个URL，留空使用默认种子"
          />
        </el-form-item>
        <el-form-item label="最大页面数">
          <el-input-number v-model="form.maxPages" :min="1" :max="100000" />
        </el-form-item>
        <el-divider content-position="left">
          <span class="divider-text">代理配置</span>
        </el-divider>
        <el-form-item label="启用代理">
          <el-switch v-model="form.useProxy" />
        </el-form-item>
        <el-form-item v-if="form.useProxy" label="代理地址">
          <div class="proxy-inputs">
            <el-input v-model="form.proxyHost" placeholder="127.0.0.1" />
            <span class="proxy-sep">:</span>
            <el-input-number v-model="form.proxyPort" :min="1" :max="65535" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <button class="btn-cancel" @click="showDialog = false">取消</button>
          <button class="btn-submit" :disabled="submitting" @click="doSubmit">
            <span v-if="submitting" class="btn-spinner"></span>
            <span v-else>{{ isEdit ? '保存' : '创建' }}</span>
          </button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '../api'

const router = useRouter()
const tasks = ref([])
const showDialog = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const editTaskId = ref('')
let refreshTimer = null

const form = ref({
  taskName: '',
  seedUrlsText: '',
  maxPages: 100,
  proxyHost: '127.0.0.1',
  proxyPort: 7890,
  useProxy: true
})

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

function getStatusColor(status) {
  const colors = {
    PENDING: 'linear-gradient(135deg, #a0a0a0, #c0c0c0)',
    RUNNING: 'linear-gradient(135deg, #e07a5f, #f2cc8f)',
    COMPLETED: 'linear-gradient(135deg, #81b29a, #a8d5ba)',
    FAILED: 'linear-gradient(135deg, #e57373, #ef9a9a)',
    STOPPED: 'linear-gradient(135deg, #ffb74d, #ffe082)'
  }
  return colors[status] || colors.PENDING
}

function getProgress(task) {
  if (!task.maxPages) return 0
  return Math.min(100, Math.round((task.crawledCount / task.maxPages) * 100))
}

function formatTime(ts) {
  if (!ts) return '-'
  return new Date(ts).toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function formatUrl(url) {
  try {
    const u = new URL(url)
    return u.hostname
  } catch {
    return url.substring(0, 30)
  }
}

function openCreate() {
  isEdit.value = false
  editTaskId.value = ''
  form.value = {
    taskName: '',
    seedUrlsText: '',
    maxPages: 100,
    proxyHost: '127.0.0.1',
    proxyPort: 7890,
    useProxy: true
  }
  showDialog.value = true
}

function openEdit(task) {
  isEdit.value = true
  editTaskId.value = task.taskId
  form.value = {
    taskName: task.taskName,
    seedUrlsText: (task.seedUrls || []).join('\n'),
    maxPages: task.maxPages,
    proxyHost: task.proxyHost || '127.0.0.1',
    proxyPort: task.proxyPort || 7890,
    useProxy: task.useProxy !== false
  }
  showDialog.value = true
}

async function loadTasks() {
  try {
    const res = await api.listTasks()
    if (res.data.status === 'success') {
      tasks.value = res.data.tasks || []
    }
  } catch (e) {
    console.error('加载任务列表失败', e)
  }
}

async function doSubmit() {
  submitting.value = true
  try {
    const seedUrls = form.value.seedUrlsText
        .split('\n')
        .map(s => s.trim())
        .filter(s => s.length > 0)

    const data = {
      taskName: form.value.taskName || '未命名任务',
      seedUrls,
      maxPages: form.value.maxPages,
      proxyHost: form.value.proxyHost || '127.0.0.1',
      proxyPort: form.value.proxyPort || 7890,
      useProxy: form.value.useProxy
    }

    let res
    if (isEdit.value) {
      res = await api.updateTask(editTaskId.value, data)
    } else {
      res = await api.createTask(data)
    }

    if (res.data.status === 'success') {
      ElMessage.success(isEdit.value ? '任务已更新' : '任务创建成功')
      showDialog.value = false
      loadTasks()
    } else {
      ElMessage.error(res.data.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('操作失败: ' + (e.response?.data?.message || e.message))
  } finally {
    submitting.value = false
  }
}

async function doAction(task) {
  try {
    const res = await api.startFull(task.taskId)
    if (res.data.status === 'started') {
      ElMessage.success('开始挖掘')
      router.push(`/tasks/${task.taskId}`)
    } else {
      ElMessage.error(res.data.message || '启动失败')
    }
  } catch (e) {
    ElMessage.error('启动失败: ' + (e.response?.data?.message || e.message))
  }
}

async function doDelete(taskId) {
  try {
    await ElMessageBox.confirm('确定要删除该任务及其所有数据吗？此操作不可恢复。', '确认删除', {
      type: 'warning'
    })
    const res = await api.deleteTask(taskId)
    if (res.data.status === 'success') {
      ElMessage.success('任务已删除')
      loadTasks()
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

onMounted(() => {
  loadTasks()
  refreshTimer = setInterval(loadTasks, 5000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style scoped>
/* ============ Page Header ============ */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 40px;
  gap: 24px;
}

.header-left {
  flex: 1;
}

.header-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: rgba(224, 122, 95, 0.1);
  border-radius: var(--radius-full);
  margin-bottom: 12px;
}

.badge-icon {
  width: 14px;
  height: 14px;
  color: var(--accent-primary);
}

.header-badge span {
  font-size: 12px;
  font-weight: 600;
  color: var(--accent-primary);
}

.page-title {
  font-size: 32px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.5px;
  margin-bottom: 8px;
}

.page-subtitle {
  font-size: 15px;
  color: var(--text-secondary);
}

/* ============ Create Button ============ */
.btn-create {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 24px;
  background: var(--accent-primary);
  color: white;
  border: none;
  border-radius: var(--radius-lg);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s ease;
  box-shadow: 0 4px 16px rgba(224, 122, 95, 0.3);
}

.btn-create:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(224, 122, 95, 0.4);
}

.btn-icon {
  width: 18px;
  height: 18px;
}

/* ============ Empty State ============ */
.empty-state {
  text-align: center;
  padding: 80px 20px;
}

.empty-graphic {
  position: relative;
  width: 120px;
  height: 120px;
  margin: 0 auto 32px;
}

.empty-circle {
  width: 100%;
  height: 100%;
  border: 3px dashed var(--border-medium);
  border-radius: 50%;
  animation: rotate 30s linear infinite;
}

@keyframes rotate {
  to { transform: rotate(360deg); }
}

.empty-plus {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 50px;
  height: 50px;
  background: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  color: var(--text-muted);
  box-shadow: var(--shadow-medium);
}

.empty-state h3 {
  font-size: 24px;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.empty-state p {
  color: var(--text-secondary);
  margin-bottom: 32px;
}

.btn-create-empty {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  background: var(--accent-primary);
  color: white;
  border: none;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s ease;
}

.btn-create-empty:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(224, 122, 95, 0.3);
}

.btn-create-empty svg {
  width: 18px;
  height: 18px;
}

/* ============ Task Grid ============ */
.task-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 24px;
}

/* ============ Task Card ============ */
.task-card {
  background: white;
  border-radius: var(--radius-lg);
  padding: 24px;
  box-shadow: var(--shadow-soft);
  transition: all 0.3s ease;
  border: 1px solid transparent;
  position: relative;
  overflow: hidden;
}

.task-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, var(--accent-primary), var(--accent-tertiary));
  opacity: 0;
  transition: opacity 0.3s ease;
}

.task-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-strong);
  border-color: var(--border-light);
}

.task-card:hover::before {
  opacity: 1;
}

.task-card.is-running {
  border-color: var(--accent-primary);
  box-shadow: 0 0 0 1px rgba(224, 122, 95, 0.2), var(--shadow-medium);
}

.task-card.is-running::before {
  opacity: 1;
  animation: shimmer 2s linear infinite;
  background: linear-gradient(90deg, var(--accent-primary), var(--accent-secondary), var(--accent-tertiary), var(--accent-primary));
  background-size: 200% 100%;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* Card Header */
.card-header {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 16px;
}

.card-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: white;
}

.card-icon svg {
  width: 20px;
  height: 20px;
}

.icon-spin {
  animation: spin 2s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.card-title-area {
  flex: 1;
  min-width: 0;
}

.card-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-id {
  font-size: 12px;
  color: var(--text-muted);
  font-family: var(--font-mono);
}

/* Status Row */
.status-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.status-badge {
  padding: 4px 12px;
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 600;
}

.status-pending {
  background: rgba(0, 0, 0, 0.05);
  color: var(--text-secondary);
}

.status-running {
  background: rgba(224, 122, 95, 0.1);
  color: var(--accent-primary);
}

.status-completed {
  background: rgba(129, 178, 154, 0.15);
  color: var(--accent-secondary);
}

.status-failed {
  background: rgba(229, 115, 115, 0.1);
  color: #e57373;
}

.status-stopped {
  background: rgba(255, 183, 77, 0.15);
  color: #ff9800;
}

.live-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--accent-primary);
}

.live-dot {
  width: 6px;
  height: 6px;
  background: var(--accent-primary);
  border-radius: 50%;
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.8); }
}

/* Stats Row */
.stats-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 0;
  margin-bottom: 16px;
  border-top: 1px solid var(--border-light);
  border-bottom: 1px solid var(--border-light);
}

.stat-item {
  text-align: center;
  flex: 1;
}

.stat-value {
  display: block;
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.2;
}

.stat-label {
  font-size: 11px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.stat-divider {
  width: 1px;
  height: 32px;
  background: var(--border-light);
}

/* Progress Section */
.progress-section {
  margin-bottom: 16px;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}

.progress-percent {
  font-weight: 600;
  color: var(--accent-primary);
}

.progress-track {
  height: 6px;
  background: var(--bg-tertiary);
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--accent-primary), var(--accent-tertiary));
  border-radius: 3px;
  transition: width 0.5s ease;
}

/* URLs Section */
.urls-section {
  margin-bottom: 16px;
}

.urls-label {
  display: block;
  font-size: 11px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}

.urls-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.url-tag {
  display: inline-block;
  padding: 4px 10px;
  background: var(--bg-tertiary);
  border-radius: var(--radius-sm);
  font-size: 12px;
  color: var(--text-secondary);
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.url-more {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  background: transparent;
  font-size: 12px;
  color: var(--text-muted);
}

/* Error Alert */
.error-alert {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  background: rgba(229, 115, 115, 0.08);
  border-radius: var(--radius-sm);
  margin-bottom: 16px;
}

.error-icon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  color: #e57373;
}

.error-alert span {
  font-size: 13px;
  color: #e57373;
  line-height: 1.5;
}

/* Card Footer */
.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
}

.time-info {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted);
}

.time-info svg {
  width: 14px;
  height: 14px;
}

.card-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-light);
  background: var(--bg-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  color: var(--text-secondary);
}

.action-btn svg {
  width: 16px;
  height: 16px;
}

.action-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  border-color: var(--accent-primary);
  color: var(--accent-primary);
}

.action-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.action-btn.primary {
  background: var(--accent-primary);
  border-color: var(--accent-primary);
  color: white;
}

.action-btn.primary:hover:not(:disabled) {
  background: #c96a51;
  box-shadow: 0 4px 12px rgba(224, 122, 95, 0.3);
}

.action-btn.danger:hover:not(:disabled) {
  border-color: #e57373;
  color: #e57373;
  background: rgba(229, 115, 115, 0.08);
}

/* Dialog Form */
.task-form .el-form-item {
  margin-bottom: 20px;
}

.divider-text {
  color: var(--text-secondary);
  font-size: 13px;
}

.proxy-inputs {
  display: flex;
  align-items: center;
  gap: 8px;
}

.proxy-sep {
  color: var(--text-muted);
}

/* Dialog Footer */
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.btn-cancel {
  padding: 10px 24px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-medium);
  background: white;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-cancel:hover {
  background: var(--bg-tertiary);
  border-color: var(--text-muted);
}

.btn-submit {
  padding: 10px 28px;
  border-radius: var(--radius-sm);
  border: none;
  background: var(--accent-primary);
  color: white;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 100px;
}

.btn-submit:hover:not(:disabled) {
  background: #c96a51;
}

.btn-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

/* ============ Responsive ============ */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: stretch;
    gap: 20px;
  }

  .page-title {
    font-size: 26px;
  }

  .btn-create {
    align-self: flex-end;
  }

  .task-grid {
    grid-template-columns: 1fr;
  }

  .stats-row {
    flex-wrap: wrap;
    gap: 12px;
  }

  .stat-divider {
    display: none;
  }

  .stat-item {
    min-width: calc(25% - 12px);
  }

  .stat-value {
    font-size: 18px;
  }

  .card-footer {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }

  .time-info {
    justify-content: center;
  }

  .card-actions {
    justify-content: center;
  }
}

@media (max-width: 480px) {
  .task-card {
    padding: 20px;
  }

  .card-header {
    gap: 12px;
  }

  .card-icon {
    width: 40px;
    height: 40px;
    border-radius: 10px;
  }

  .card-title {
    font-size: 16px;
  }

  .action-btn {
    width: 32px;
    height: 32px;
  }

  .action-btn svg {
    width: 14px;
    height: 14px;
  }
}
</style>