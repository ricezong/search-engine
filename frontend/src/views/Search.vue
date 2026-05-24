<template>
  <div class="search-page">
    <div class="search-header">
      <h2>搜索</h2>
      <div class="search-bar">
        <el-select v-model="selectedTaskId" placeholder="选择任务" style="width: 200px" @change="onTaskChange">
          <el-option v-for="task in tasks" :key="task.taskId" :label="task.taskName" :value="task.taskId" />
        </el-select>
        <el-input
          v-model="query"
          placeholder="输入搜索关键词..."
          clearable
          @keyup.enter="doSearch"
          style="flex: 1"
        >
          <template #append>
            <el-button @click="doSearch" :loading="loading">搜索</el-button>
          </template>
        </el-input>
      </div>
    </div>

    <div v-if="searched && results.length === 0 && !loading" class="no-results">
      <el-empty description="未找到匹配结果" />
    </div>

    <div v-if="results.length > 0" class="results-section">
      <div class="results-info">
        共找到 <strong>{{ totalCount }}</strong> 条结果，第 {{ currentPage }} 页
      </div>
      <div v-for="(item, index) in results" :key="index" class="result-item">
        <a :href="item.url" target="_blank" rel="noopener noreferrer" class="result-title">
          {{ item.title || item.url }}
        </a>
        <div class="result-url">{{ item.url }}</div>
        <div class="result-snippet">{{ item.snippet }}</div>
        <div class="result-meta">
          <el-tag size="small" type="info">匹配次数: {{ item.matchCount }}</el-tag>
          <el-tag size="small">docId: {{ item.docId }}</el-tag>
        </div>
      </div>
      <div class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="10"
          :total="totalCount"
          layout="prev, pager, next"
          @current-change="doSearch"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as api from '../api'

const route = useRoute()
const tasks = ref([])
const selectedTaskId = ref('')
const query = ref('')
const results = ref([])
const totalCount = ref(0)
const currentPage = ref(1)
const loading = ref(false)
const searched = ref(false)

async function loadTasks() {
  try {
    const res = await api.listTasks()
    if (res.data.status === 'success') {
      tasks.value = res.data.tasks || []
      // 如果URL中有taskId参数，自动选中
      const taskIdParam = route.query.taskId
      if (taskIdParam && tasks.value.some(t => t.taskId === taskIdParam)) {
        selectedTaskId.value = taskIdParam
      }
    }
  } catch (e) {
    console.error('加载任务列表失败', e)
  }
}

function onTaskChange() {
  results.value = []
  totalCount.value = 0
  searched.value = false
}

async function doSearch() {
  if (!selectedTaskId.value) {
    ElMessage.warning('请先选择一个任务')
    return
  }
  if (!query.value.trim()) {
    ElMessage.warning('请输入搜索关键词')
    return
  }

  loading.value = true
  searched.value = true
  try {
    const res = await api.search(selectedTaskId.value, query.value.trim(), currentPage.value)
    if (res.data.status === 'success') {
      results.value = res.data.results || []
      totalCount.value = res.data.totalCount || 0
    } else {
      ElMessage.error(res.data.message || '搜索失败')
      results.value = []
      totalCount.value = 0
    }
  } catch (e) {
    ElMessage.error('搜索失败: ' + (e.response?.data?.message || e.message))
    results.value = []
    totalCount.value = 0
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadTasks()
})
</script>

<style scoped>
.search-header {
  margin-bottom: 24px;
}

.search-header h2 {
  margin-bottom: 16px;
}

.search-bar {
  display: flex;
  gap: 12px;
}

.results-info {
  color: #909399;
  margin-bottom: 16px;
  font-size: 14px;
}

.result-item {
  background: white;
  padding: 16px 20px;
  border-radius: 8px;
  margin-bottom: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  transition: box-shadow 0.2s;
}

.result-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
}

.result-title {
  font-size: 18px;
  color: #409eff;
  text-decoration: none;
  font-weight: 500;
  display: block;
  margin-bottom: 4px;
}

.result-title:hover {
  text-decoration: underline;
}

.result-url {
  color: #67c23a;
  font-size: 13px;
  margin-bottom: 8px;
  word-break: break-all;
}

.result-snippet {
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
  margin-bottom: 8px;
}

.result-meta {
  display: flex;
  gap: 8px;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}

.no-results {
  padding: 40px 0;
}
</style>