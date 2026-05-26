<template>
  <div class="task-list-page">
    <div class="page-header">
      <h2>任务管理</h2>
      <el-button type="primary" @click="openCreate">
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
          <span class="task-id">#{{ task.taskId.slice(0, 8) }}</span>
        </div>

        <div class="task-info">
          <div class="info-item">
            <span class="info-label">最大页面</span>
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
            <span class="info-label">索引词</span>
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
          <el-button size="small" type="success" :disabled="task.status === 'RUNNING'" @click="doAction(task)">
            <el-icon><VideoPlay /></el-icon> 开始挖掘
          </el-button>
          <el-button size="small" type="primary" @click="router.push(`/tasks/${task.taskId}`)">
            <el-icon><View /></el-icon> 详情
          </el-button>
          <el-button size="small" type="info" @click="router.push(`/search?taskId=${task.taskId}`)">
            <el-icon><Search /></el-icon> 搜索
          </el-button>
          <el-button size="small" :disabled="task.status === 'RUNNING'" @click="openEdit(task)">
            <el-icon><Edit /></el-icon> 编辑
          </el-button>
          <el-button size="small" type="danger" :disabled="task.status === 'RUNNING'" @click="doDelete(task.taskId)">
            <el-icon><Delete /></el-icon> 删除
          </el-button>
        </div>

        <div class="task-time">
          创建于 {{ formatTime(task.createTime) }}
        </div>
      </el-card>
    </div>

    <!-- 新建/编辑任务对话框 -->
    <el-dialog v-model="showDialog" :title="isEdit ? '编辑任务' : '新建任务'" width="560px" class="task-dialog">
      <el-form :model="form" label-width="100px">
        <el-form-item label="任务名称">
          <el-input v-model="form.taskName" placeholder="输入任务名称" />
        </el-form-item>
        <el-form-item label="种子URL">
          <el-input
            v-model="form.seedUrlsText"
            type="textarea"
            :rows="4"
            placeholder="每行一个URL，留空使用默认种子"
          />
        </el-form-item>
        <el-form-item label="最大页面数">
          <el-input-number v-model="form.maxPages" :min="1" :max="100000" />
        </el-form-item>
        <el-divider content-position="left">代理配置</el-divider>
        <el-form-item label="启用代理">
          <el-switch v-model="form.useProxy" />
        </el-form-item>
        <el-form-item v-if="form.useProxy" label="代理地址">
          <el-input v-model="form.proxyHost" placeholder="127.0.0.1" style="width: 60%" />
          <span style="margin: 0 8px">:</span>
          <el-input-number v-model="form.proxyPort" :min="1" :max="65535" style="width: 35%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="doSubmit">{{ isEdit ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, VideoPlay, View, Search, Edit, Delete } from '@element-plus/icons-vue'
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

/* 手机端适配 */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }

  .page-header h2 {
    text-align: center;
  }

  .task-info {
    gap: 12px;
    flex-wrap: wrap;
    justify-content: space-around;
  }

  .info-value {
    font-size: 16px;
  }

  .task-actions {
    justify-content: center;
  }

  .task-urls {
    flex-direction: column;
    align-items: flex-start;
  }

  .url-tag {
    max-width: 100%;
  }
}
</style>
