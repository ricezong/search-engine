<template>
  <el-container class="app-container">
    <el-header class="app-header">
      <div class="header-content">
        <h1 class="logo" @click="router.push('/')">🔍 Mini Search Engine</h1>
        <el-menu mode="horizontal" :router="true" :default-active="activeMenu" class="nav-menu">
          <el-menu-item index="/">任务管理</el-menu-item>
          <el-menu-item index="/search">搜索</el-menu-item>
        </el-menu>
      </div>
    </el-header>
    <el-main class="app-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const activeMenu = computed(() => route.path)
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
  background-color: #f5f7fa;
  overflow-x: hidden;
}

.app-container {
  min-height: 100vh;
}

.app-header {
  background: linear-gradient(135deg, #409eff, #337ecc);
  display: flex;
  align-items: center;
  padding: 0 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  height: auto !important;
  min-height: 60px;
}

.header-content {
  display: flex;
  align-items: center;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
}

.logo {
  color: white;
  font-size: 20px;
  cursor: pointer;
  margin-right: 40px;
  white-space: nowrap;
}

.nav-menu {
  background: transparent;
  border: none;
}

.nav-menu .el-menu-item {
  color: rgba(255, 255, 255, 0.8) !important;
  border-bottom: none !important;
}

.nav-menu .el-menu-item:hover,
.nav-menu .el-menu-item.is-active {
  color: white !important;
  background: rgba(255, 255, 255, 0.15) !important;
}

.app-main {
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
  padding: 20px;
}

/* 全局响应式 */
@media (max-width: 768px) {
  .app-header {
    padding: 0 12px;
  }

  .header-content {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
    padding: 8px 0;
  }

  .logo {
    font-size: 16px;
    margin-right: 0;
  }

  .nav-menu {
    width: 100%;
  }

  .nav-menu .el-menu-item {
    padding: 0 12px !important;
    font-size: 14px;
  }

  .app-main {
    padding: 12px;
  }

  /* Element Plus 响应式 */
  .el-dialog {
    width: 90% !important;
    margin: 16px auto !important;
  }

  .el-descriptions {
    font-size: 13px;
  }

  .el-descriptions__label {
    width: 80px !important;
  }

  .el-table {
    font-size: 13px;
  }

  .el-card__body {
    padding: 12px !important;
  }

  .el-form-item__label {
    font-size: 14px;
  }

  .el-input-number {
    width: 100% !important;
  }
}
</style>