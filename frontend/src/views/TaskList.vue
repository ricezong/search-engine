<template>
  <div class="task-list-page">
    <div class="page-header">
      <h2>任务管理</h2>
      <el-button type="primary" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon> 新建任务
      </el-button>
    </div>

    <!-- 任务列表 -->
    <div v-if="tasks.length === 0" class="empty-state">
      <el-empty description="暂无任务，点击上方按钮创建新任务" />
    </div>

    <div v-else class="task-cards">
      <el-card v-for="task in tasks" :key="task.taskId" class="task-card" shadow="hover">
        <div class="task-card-header">
          <div class="task-title">
            <span class="task-name">{{ task.taskName }}</span>
            <el-tag :type="statusType(task.status)" size="small">{{ statusLabel(task.status) }}</el-tag>
          </div>
          <span class="task-id">#{{ task.taskId }}</span>
        </div>

        <div class="task-info">
          <div class="info-item">
            <span class="info-label">最大页面数</span>
            <span class="info-value">{{ task.maxPages }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">已爬取</span>
            <span class="info-value">{{ task.crawledCount }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">已分析</span>
            <span class="info-value">{{ task.analyzedCount }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">索引词数</span>
            <span class="info-value">{{ task.indexedTermCount }}</span>
          </div>
        </div>

        <div class="task-urls">
          <span class="info-label">种子URL：</span>
          <el-tag v-for="url in (task.seedUrls || []).slice(0, 3)" :key="url" size="small" type="info" class="url-tag">
            {{ url }}
          </el-tag>
          <span v-if="(task.seedUrls || []).length > 3" class="more-urls">+{{ task.seedUrls.length - 3 }}个</span>
        </div>

        <div v-if="task.errorMessage" class="task-error">
          <el-alert :title="task.errorMessage" type="error" :closable="false" show-icon />
        </div>

        <div class="task-actions">
          <el-button size="small" type="success" :disabled="task.status === 'RUNNING'" @click="doAction(task.taskId, 'full')">全流程</el-button>
          <el-button size="small" :disabled="task.status === 'RUNNING'" @click="doAction(task.taskId, 'crawl')">爬取</el-button>
          <el-button size="small" :disabled="task.status === 'RUNNING'" @click="doAction(task.taskId, 'analyze')">分析</el-button>
          <el-button size="small" :disabled="task.status === 'RUNNING'" @click="doAction(task.taskId, 'index')">索引</el-button>
          <el-button v-if="task.status === 'RUNNING'" size="small" type="warning" @click="doStop(task.taskId)">停止</el-button>
          <el-button size="small" type="primary" @click="router.push(`/tasks/${task.taskId}`)">详情</el-button>
          <el-button size="small" type="info" @click="router.push(`/search?taskId=${task.taskId}`)">搜索</el-button>
          <el-button size="small" type="danger" :disabled="task.status === 'RUNNING'" @click="doDelete(task.taskId)">删除</el-button>
        </div>

        <div class="task-time">
          创建于 {{ formatTime(task.createTime) }}
        </div>
      </el-card>
    </div>

    <!-- 新建任务对话框 -->
    <el-dialog v-model="showCreateDialog" title="新建任务" width="560px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="任务名称">
          <el-input v-model="createForm.taskName" placeholder="输入任务名称" />
        </el-form-item>
        <el-form-item label="种子URL">
          <el-input
            v-model="createForm.seedUrlsText"
            type="textarea"
            :rows="4"
            placeholder="每行一个URL，留空使用默认种子"
          />
        </el-form-item>
        <el-form-item label="最大页面数">
          <el-input-number v-model="createForm.maxPages" :min="1" :max="100000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="doCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '../api'

const router = useRouter()
const tasks = ref([])
const showCreateDialog = ref(false)
const creating = ref(false)
let refreshTimer = null

const createForm = ref({
  taskName: '',
  seedUrlsText: '',
  maxPages: 100
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

async function doCreate() {
  creating.value = true
  try {
    const seedUrls = createForm.value.seedUrlsText
      .split('\n')
      .map(s => s.trim())
      .filter(s => s.length > 0)

    const res = await api.createTask({
      taskName: createForm.value.taskName || '未命名任务',
      seedUrls,
      maxPages: createForm.value.maxPages
    })

    if (res.data.status === 'success') {
      ElMessage.success('任务创建成功')
      showCreateDialog.value = false
      createForm.value = { taskName: '', seedUrlsText: '', maxPages: 100 }
      loadTasks()
    } else {
      ElMessage.error(res.data.message || '创建失败')
    }
  } catch (e) {
    ElMessage.error('创建任务失败')
  } finally {
    creating.value = false
  }
}

async function doAction(taskId, action) {
  try {
    const actionMap = {
      crawl: api.startCrawl,
      analyze: api.startAnalyze,
      index: api.startIndex,
      full: api.startFull
    }
    const fn = actionMap[action]
    if (!fn) {
      ElMessage.error('未知操作: ' + action)
      return
    }
    const res = await fn(taskId)
    if (res.data.status === 'started') {
      ElMessage.success(res.data.message)
      setTimeout(loadTasks, 500)
    } else {
      ElMessage.error(res.data.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('操作失败: ' + (e.response?.data?.message || e.message))
  }
}

async function doStop(taskId) {
  try {
    const res = await api.stopTask(taskId)
    if (res.data.status === 'success') {
      ElMessage.success('任务已停止')
      loadTasks()
    }
  } catch (e) {
    ElMessage.error('停止失败')
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
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.task-cards {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.task-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.task-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.task-name {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.task-id {
  color: #909399;
  font-size: 12px;
}

.task-info {
  display: flex;
  gap: 24px;
  margin-bottom: 12px;
  padding: 10px 0;
  border-top: 1px solid #ebeef5;
  border-bottom: 1px solid #ebeef5;
}

.info-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.info-label {
  color: #909399;
  font-size: 12px;
  margin-bottom: 4px;
}

.info-value {
  font-size: 20px;
  font-weight: 600;
  color: #409eff;
}

.task-urls {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.url-tag {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.more-urls {
  color: #909399;
  font-size: 12px;
}

.task-error {
  margin-bottom: 12px;
}

.task-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.task-time {
  color: #909399;
  font-size: 12px;
  text-align: right;
}

.empty-state {
  padding: 60px 0;
}
</style>