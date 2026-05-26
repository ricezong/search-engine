<template>
  <div class="search-page">
    <!-- Hero Search Section -->
    <div class="search-hero" :class="{ compact: hasSearched }">
      <!-- Floating Elements -->
      <div class="hero-particles">
        <div class="particle" v-for="i in 6" :key="i" :style="getParticleStyle(i)"></div>
      </div>

      <!-- Header -->
      <div class="hero-header">
        <h1 class="hero-title">
          <span class="title-bracket">[</span>
          智能搜索
          <span class="title-bracket">]</span>
        </h1>
        <p class="hero-subtitle">在索引内容中快速找到你需要的信息</p>
      </div>

      <!-- Search Box -->
      <div class="search-container">
        <!-- Task Selector -->
        <div class="task-selector">
          <label class="selector-label">
            <svg viewBox="0 0 20 20" fill="currentColor">
              <path d="M2 6a2 2 0 012-2h5l2 2h5a2 2 0 012 2v6a2 2 0 01-2 2H4a2 2 0 01-2-2V6z"/>
            </svg>
            <span>选择任务</span>
          </label>
          <el-select
              v-model="selectedTaskId"
              placeholder="选择一个任务开始搜索"
              class="task-select"
              @change="onTaskChange"
              size="large"
              filterable
          >
            <template #prefix>
              <svg viewBox="0 0 20 20" fill="currentColor" class="select-icon">
                <path d="M2 6a2 2 0 012-2h5l2 2h5a2 2 0 012 2v6a2 2 0 01-2 2H4a2 2 0 01-2-2V6z"/>
              </svg>
            </template>
            <el-option
                v-for="task in tasks"
                :key="task.taskId"
                :label="task.taskName"
                :value="task.taskId"
            >
              <div class="task-option">
                <span class="task-name">{{ task.taskName }}</span>
                <span class="task-count">{{ task.indexedTermCount }} 词</span>
              </div>
            </el-option>
          </el-select>
        </div>

        <!-- Search Input -->
        <div class="search-box" :class="{ focused: isFocused, 'has-results': hasSearched }">
          <div class="search-input-wrapper">
            <svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="8"/>
              <path d="M21 21l-4.35-4.35"/>
            </svg>
            <input
                v-model="query"
                type="text"
                class="search-input"
                placeholder="输入搜索关键词..."
                @focus="isFocused = true"
                @blur="isFocused = false"
                @keyup.enter="doSearch"
            />
            <button class="search-button" @click="doSearch" :disabled="loading || !selectedTaskId">
              <span v-if="loading" class="btn-spinner"></span>
              <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M5 12h14M12 5l7 7-7 7"/>
              </svg>
            </button>
          </div>
        </div>

        <!-- Keywords Section -->
        <div v-if="keywords.length > 0" class="keywords-section">
          <div class="keywords-header">
            <svg viewBox="0 0 20 20" fill="currentColor">
              <path d="M11.3 1.046A1 1 0 0112 2v5h4a1 1 0 01.82 1.573l-7 10A1 1 0 018 18v-5H4a1 1 0 01-.82-1.573l7-10a1 1 0 011.12-.38z"/>
            </svg>
            <span>热门搜索</span>
          </div>
          <div class="keywords-list">
            <button
                v-for="word in keywords"
                :key="word"
                class="keyword-tag"
                @click="searchKeyword(word)"
            >
              {{ word }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- No Results State -->
    <div v-if="hasSearched && results.length === 0 && !loading" class="no-results">
      <div class="no-results-icon">
        <svg viewBox="0 0 80 80" fill="none" stroke="currentColor" stroke-width="1.5">
          <circle cx="35" cy="35" r="25"/>
          <path d="M52 52l18 18"/>
          <path d="M28 35h14M35 28v14"/>
        </svg>
      </div>
      <h3 class="no-results-title">未找到匹配结果</h3>
      <p class="no-results-text">试试其他关键词，或者更换任务后再试</p>
      <button class="btn-clear" @click="clearSearch">
        <svg viewBox="0 0 20 20" fill="currentColor">
          <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
        </svg>
        <span>清除搜索</span>
      </button>
    </div>

    <!-- Search Results -->
    <div v-if="results.length > 0" class="results-section">
      <!-- Results Header -->
      <div class="results-header">
        <div class="results-info">
          <span class="results-count">
            找到 <strong>{{ totalCount }}</strong> 条相关结果
          </span>
          <span class="results-time">
            <svg viewBox="0 0 16 16" fill="currentColor">
              <path d="M8 3.5a.5.5 0 01.5.5v4h2.5a.5.5 0 010 1h-3a.5.5 0 01-.5-.5V4a.5.5 0 01.5-.5z"/>
              <path d="M8 16A8 8 0 118 0a8 8 0 010 16zm7-8A7 7 0 101 8a7 7 0 0014 0z"/>
            </svg>
            耗时 {{ searchTime }}ms
          </span>
        </div>
        <div class="results-pagination">
          <button
              class="page-btn"
              :disabled="currentPage <= 1"
              @click="currentPage--; doSearch()"
          >
            <svg viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M12.707 5.293a1 1 0 010 1.414L9.414 10l3.293 3.293a1 1 0 01-1.414 1.414l-4-4a1 1 0 010-1.414l4-4a1 1 0 011.414 0z" clip-rule="evenodd"/>
            </svg>
            上一页
          </button>
          <span class="page-indicator">第 {{ currentPage }} 页</span>
          <button
              class="page-btn"
              :disabled="results.length < 10"
              @click="currentPage++; doSearch()"
          >
            下一页
            <svg viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clip-rule="evenodd"/>
            </svg>
          </button>
        </div>
      </div>

      <!-- Results List -->
      <div class="results-list">
        <div
            v-for="(item, index) in results"
            :key="index"
            class="result-card"
            :style="{ animationDelay: index * 0.05 + 's' }"
        >
          <!-- Result Index -->
          <div class="result-index">{{ index + 1 + (currentPage - 1) * 10 }}</div>

          <!-- Result Content -->
          <div class="result-content">
            <!-- URL -->
            <div class="result-url">
              <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M6 8l4 4M9.5 5.5a3.5 3.5 0 01-5 5l1.5 1.5a3.5 3.5 0 015-5"/>
                <path d="M6.5 10.5a3.5 3.5 0 015-5l-1.5-1.5a3.5 3.5 0 01-5 5"/>
              </svg>
              <a :href="item.url" target="_blank" rel="noopener noreferrer" class="url-text">
                {{ item.url }}
              </a>
            </div>

            <!-- Title -->
            <a :href="item.url" target="_blank" rel="noopener noreferrer" class="result-title">
              {{ item.title || item.url }}
            </a>

            <!-- Snippet -->
            <p class="result-snippet">{{ item.snippet }}</p>

            <!-- Meta Tags -->
            <div class="result-meta">
              <span class="meta-tag highlight">
                <svg viewBox="0 0 16 16" fill="currentColor">
                  <path d="M8 1.5l-1.5 3-3.5.5 2.5 2.5-1 3.5 3-1.5 3 1.5-1-3.5 2.5-2.5-3.5-.5z"/>
                </svg>
                匹配 {{ item.matchCount }} 次
              </span>
              <span class="meta-tag">
                <svg viewBox="0 0 16 16" fill="currentColor">
                  <path d="M4 4a2 2 0 012-2h4a2 2 0 012 2v2H4V4z"/>
                  <path d="M4 8h8v5a1 1 0 01-1 1H5a1 1 0 01-1-1V8z"/>
                </svg>
                docId: {{ item.docId }}
              </span>
            </div>
          </div>

          <!-- Result Action -->
          <div class="result-action">
            <a :href="item.url" target="_blank" class="action-open" title="在新窗口打开">
              <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M11 3a1 1 0 100 2h2.586l-6.293 6.293a1 1 0 101.414 1.414L15 6.414V9a1 1 0 102 0V4a1 1 0 00-1-1h-5z"/>
                <path d="M5 5a2 2 0 00-2 2v8a2 2 0 002 2h8a2 2 0 002-2v-3a1 1 0 10-2 0v3H5V7h3a1 1 0 000-2H5z"/>
              </svg>
            </a>
          </div>
        </div>
      </div>

      <!-- Bottom Pagination -->
      <div class="results-footer">
        <button
            class="page-btn"
            :disabled="currentPage <= 1"
            @click="currentPage--; doSearch()"
        >
          <svg viewBox="0 0 20 20" fill="currentColor">
            <path fill-rule="evenodd" d="M12.707 5.293a1 1 0 010 1.414L9.414 10l3.293 3.293a1 1 0 01-1.414 1.414l-4-4a1 1 0 010-1.414l4-4a1 1 0 011.414 0z" clip-rule="evenodd"/>
          </svg>
          上一页
        </button>
        <span class="page-indicator">{{ currentPage }} / {{ Math.ceil(totalCount / 10) }}</span>
        <button
            class="page-btn"
            :disabled="results.length < 10"
            @click="currentPage++; doSearch()"
        >
          下一页
          <svg viewBox="0 0 20 20" fill="currentColor">
            <path fill-rule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clip-rule="evenodd"/>
          </svg>
        </button>
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
const hasSearched = ref(false)
const keywords = ref([])
const isFocused = ref(false)
const searchTime = ref(0)

function getParticleStyle(index) {
  const positions = [
    { left: '10%', top: '20%', delay: '0s' },
    { left: '85%', top: '15%', delay: '-2s' },
    { left: '20%', top: '70%', delay: '-4s' },
    { left: '80%', top: '75%', delay: '-6s' },
    { left: '50%', top: '10%', delay: '-8s' },
    { left: '70%', top: '50%', delay: '-10s' }
  ]
  return {
    left: positions[index - 1].left,
    top: positions[index - 1].top,
    animationDelay: positions[index - 1].delay
  }
}

async function loadTasks() {
  try {
    const res = await api.listTasks()
    if (res.data.status === 'success') {
      tasks.value = res.data.tasks || []
      const taskIdParam = route.query.taskId
      if (taskIdParam && tasks.value.some(t => t.taskId === taskIdParam)) {
        selectedTaskId.value = taskIdParam
        loadKeywords()
      }
    }
  } catch (e) {
    console.error('加载任务列表失败', e)
  }
}

async function loadKeywords() {
  if (!selectedTaskId.value) {
    keywords.value = []
    return
  }
  try {
    const res = await api.getKeywords(selectedTaskId.value, 20)
    if (res.data.status === 'success') {
      keywords.value = res.data.keywords || []
    }
  } catch (e) {
    console.error('加载关键词失败', e)
    keywords.value = []
  }
}

function onTaskChange() {
  results.value = []
  totalCount.value = 0
  hasSearched.value = false
  currentPage.value = 1
  loadKeywords()
}

function searchKeyword(word) {
  query.value = word
  doSearch()
}

function clearSearch() {
  query.value = ''
  results.value = []
  totalCount.value = 0
  hasSearched.value = false
  currentPage.value = 1
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
  hasSearched.value = true
  const startTime = Date.now()

  try {
    const res = await api.search(selectedTaskId.value, query.value.trim(), currentPage.value)
    if (res.data.status === 'success') {
      results.value = res.data.results || []
      totalCount.value = res.data.totalCount || 0
      searchTime.value = Date.now() - startTime
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
/* ============ Page Container ============ */
.search-page {
  min-height: calc(100vh - 128px);
}

/* ============ Hero Section ============ */
.search-hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 20px 48px;
  position: relative;
  transition: all 0.5s ease;
}

.search-hero.compact {
  padding: 32px 20px;
}

/* Floating Particles */
.hero-particles {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.particle {
  position: absolute;
  width: 8px;
  height: 8px;
  background: var(--accent-primary);
  border-radius: 50%;
  opacity: 0.3;
  animation: float 15s ease-in-out infinite;
}

@keyframes float {
  0%, 100% { transform: translateY(0) scale(1); opacity: 0.3; }
  50% { transform: translateY(-30px) scale(1.2); opacity: 0.5; }
}

/* Hero Header */
.hero-header {
  text-align: center;
  margin-bottom: 40px;
  position: relative;
  z-index: 1;
}

.hero-title {
  font-size: 42px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -1px;
  margin-bottom: 12px;
}

.title-bracket {
  color: var(--accent-primary);
}

.compact .hero-title {
  font-size: 28px;
  margin-bottom: 8px;
}

.hero-subtitle {
  font-size: 16px;
  color: var(--text-secondary);
}

.compact .hero-subtitle {
  font-size: 14px;
}

/* ============ Search Container ============ */
.search-container {
  width: 100%;
  max-width: 680px;
  position: relative;
  z-index: 1;
}

/* Task Selector */
.task-selector {
  margin-bottom: 16px;
}

.selector-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.selector-label svg {
  width: 14px;
  height: 14px;
}

.task-select {
  width: 100%;
}

.task-select :deep(.el-input__wrapper) {
  background: white !important;
  border-radius: var(--radius-md) !important;
  box-shadow: var(--shadow-soft) !important;
  padding: 12px 16px !important;
}

.task-select :deep(.el-input__inner) {
  font-size: 15px !important;
}

.select-icon {
  width: 18px;
  height: 18px;
  color: var(--text-muted);
  margin-right: 8px;
}

.task-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
}

.task-name {
  font-weight: 500;
}

.task-count {
  font-size: 12px;
  color: var(--text-muted);
}

/* Search Box */
.search-box {
  background: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  overflow: hidden;
  transition: all 0.3s ease;
  border: 2px solid transparent;
}

.search-box.focused {
  box-shadow: var(--shadow-medium);
  border-color: var(--accent-primary);
}

.search-input-wrapper {
  display: flex;
  align-items: center;
  padding: 4px;
}

.search-icon {
  width: 20px;
  height: 20px;
  margin-left: 16px;
  flex-shrink: 0;
  color: var(--text-muted);
  transition: color 0.2s ease;
}

.search-box.focused .search-icon {
  color: var(--accent-primary);
}

.search-input {
  flex: 1;
  padding: 16px 12px;
  border: none;
  outline: none;
  font-size: 16px;
  color: var(--text-primary);
  background: transparent;
}

.search-input::placeholder {
  color: var(--text-muted);
}

.search-button {
  width: 48px;
  height: 48px;
  margin: 4px;
  background: var(--accent-primary);
  border: none;
  border-radius: var(--radius-md);
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.25s ease;
  flex-shrink: 0;
}

.search-button:hover:not(:disabled) {
  background: #c96a51;
  transform: scale(1.05);
}

.search-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.search-button svg {
  width: 20px;
  height: 20px;
}

.btn-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Keywords Section */
.keywords-section {
  margin-top: 28px;
  text-align: center;
}

.keywords-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 14px;
  font-size: 13px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.keywords-header svg {
  width: 16px;
  height: 16px;
}

.keywords-list {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}

.keyword-tag {
  padding: 8px 18px;
  background: white;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-full);
  color: var(--text-secondary);
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.25s ease;
  box-shadow: var(--shadow-soft);
}

.keyword-tag:hover {
  background: var(--accent-primary);
  border-color: var(--accent-primary);
  color: white;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(224, 122, 95, 0.3);
}

/* ============ No Results ============ */
.no-results {
  text-align: center;
  padding: 60px 20px;
}

.no-results-icon {
  width: 100px;
  height: 100px;
  margin: 0 auto 28px;
  color: var(--text-muted);
  opacity: 0.5;
}

.no-results-icon svg {
  width: 100%;
  height: 100%;
}

.no-results-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.no-results-text {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 28px;
}

.btn-clear {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-medium);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.25s ease;
}

.btn-clear:hover {
  background: white;
  border-color: var(--accent-primary);
  color: var(--accent-primary);
}

.btn-clear svg {
  width: 16px;
  height: 16px;
}

/* ============ Results Section ============ */
.results-section {
  max-width: 780px;
  margin: 0 auto;
}

.results-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-light);
}

.results-info {
  display: flex;
  align-items: center;
  gap: 20px;
}

.results-count {
  font-size: 14px;
  color: var(--text-secondary);
}

.results-count strong {
  color: var(--accent-primary);
  font-weight: 600;
}

.results-time {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted);
}

.results-time svg {
  width: 14px;
  height: 14px;
}

.results-pagination {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-indicator {
  font-size: 13px;
  color: var(--text-muted);
  min-width: 60px;
  text-align: center;
}

/* Results List */
.results-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.result-card {
  display: flex;
  background: white;
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-soft);
  transition: all 0.3s ease;
  animation: fadeInUp 0.4s ease forwards;
  opacity: 0;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.result-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-medium);
}

.result-index {
  width: 48px;
  background: var(--bg-tertiary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-muted);
  flex-shrink: 0;
}

.result-content {
  flex: 1;
  padding: 20px;
  min-width: 0;
}

.result-url {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.result-url svg {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
  color: var(--text-muted);
}

.url-text {
  font-size: 12px;
  color: var(--accent-secondary);
  text-decoration: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.2s ease;
}

.url-text:hover {
  color: var(--accent-primary);
  text-decoration: underline;
}

.result-title {
  display: block;
  font-size: 17px;
  font-weight: 600;
  color: var(--text-primary);
  text-decoration: none;
  margin-bottom: 8px;
  line-height: 1.4;
  transition: color 0.2s ease;
}

.result-title:hover {
  color: var(--accent-primary);
}

.result-snippet {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.7;
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.result-meta {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.meta-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  background: var(--bg-tertiary);
  border-radius: var(--radius-full);
  font-size: 12px;
  color: var(--text-muted);
}

.meta-tag svg {
  width: 12px;
  height: 12px;
}

.meta-tag.highlight {
  background: rgba(224, 122, 95, 0.1);
  color: var(--accent-primary);
}

.result-action {
  display: flex;
  align-items: flex-start;
  padding: 16px;
  flex-shrink: 0;
}

.action-open {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  background: var(--bg-tertiary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  text-decoration: none;
  transition: all 0.2s ease;
}

.action-open:hover {
  background: var(--accent-primary);
  color: white;
}

.action-open svg {
  width: 16px;
  height: 16px;
}

/* Results Footer */
.results-footer {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid var(--border-light);
}

.page-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  background: white;
  border: 1px solid var(--border-medium);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.page-btn:hover:not(:disabled) {
  background: var(--accent-primary);
  border-color: var(--accent-primary);
  color: white;
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-btn svg {
  width: 16px;
  height: 16px;
}

/* ============ Responsive ============ */
@media (max-width: 768px) {
  .search-hero {
    padding: 40px 16px 32px;
  }

  .search-hero.compact {
    padding: 20px 16px;
  }

  .hero-title {
    font-size: 32px;
  }

  .compact .hero-title {
    font-size: 22px;
  }

  .search-input {
    font-size: 15px;
    padding: 14px 10px;
  }

  .search-icon {
    margin-left: 12px;
  }

  .search-button {
    width: 44px;
    height: 44px;
  }

  .keyword-tag {
    padding: 6px 14px;
    font-size: 12px;
  }

  .results-header {
    flex-direction: column;
    gap: 16px;
    align-items: flex-start;
  }

  .results-pagination {
    width: 100%;
    justify-content: space-between;
  }

  .result-card {
    flex-direction: column;
  }

  .result-index {
    width: 100%;
    height: 32px;
  }

  .result-action {
    padding: 0 16px 16px;
    justify-content: flex-end;
  }
}

@media (max-width: 480px) {
  .hero-title {
    font-size: 26px;
  }

  .hero-subtitle {
    font-size: 13px;
  }

  .task-selector {
    margin-bottom: 12px;
  }

  .search-container {
    max-width: 100%;
  }

  .results-info {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .url-text {
    max-width: 180px;
  }

  .result-title {
    font-size: 15px;
  }

  .result-snippet {
    font-size: 13px;
  }
}
</style>