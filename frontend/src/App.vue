<template>
  <div class="app-container">
    <!-- Floating geometric shapes background -->
    <div class="bg-geometry">
      <div class="geo-shape geo-1"></div>
      <div class="geo-shape geo-2"></div>
      <div class="geo-shape geo-3"></div>
      <div class="geo-shape geo-4"></div>
    </div>

    <!-- Top Navigation -->
    <header class="nav-header">
      <div class="nav-content">
        <router-link to="/" class="brand">
          <div class="brand-icon">
            <svg viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="8" stroke="currentColor" stroke-width="2"/>
              <path d="M18 18L22 22" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </div>
          <span class="brand-text">Nexus</span>
        </router-link>

        <nav class="nav-links">
          <router-link to="/" class="nav-item" :class="{ active: $route.path === '/' }">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <rect x="3" y="3" width="7" height="7" rx="1.5"/>
              <rect x="14" y="3" width="7" height="7" rx="1.5"/>
              <rect x="3" y="14" width="7" height="7" rx="1.5"/>
              <rect x="14" y="14" width="7" height="7" rx="1.5"/>
            </svg>
            <span>任务</span>
          </router-link>
          <router-link to="/search" class="nav-item" :class="{ active: $route.path === '/search' }">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <circle cx="11" cy="11" r="6"/>
              <path d="M16 16L20 20" stroke-linecap="round"/>
            </svg>
            <span>搜索</span>
          </router-link>
        </nav>

        <div class="nav-status">
          <div class="status-indicator"></div>
          <span class="status-text">系统就绪</span>
        </div>
      </div>
    </header>

    <!-- Main Content -->
    <main class="main-content">
      <router-view v-slot="{ Component }">
        <transition name="fade-slide" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
</script>

<style>
/* ============ CSS Variables ============ */
:root {
  /* Base Colors - Soft Warm Palette */
  --bg-primary: #f8f7f4;
  --bg-secondary: #ffffff;
  --bg-tertiary: #f0eeeb;

  /* Accent - Warm Coral/Sage */
  --accent-primary: #e07a5f;
  --accent-secondary: #81b29a;
  --accent-tertiary: #f2cc8f;

  /* Text Colors */
  --text-primary: #2d3436;
  --text-secondary: #636e72;
  --text-muted: #b2bec3;

  /* Borders & Shadows */
  --border-light: rgba(0, 0, 0, 0.06);
  --border-medium: rgba(0, 0, 0, 0.1);
  --shadow-soft: 0 2px 8px rgba(0, 0, 0, 0.04);
  --shadow-medium: 0 4px 20px rgba(0, 0, 0, 0.06);
  --shadow-strong: 0 8px 40px rgba(0, 0, 0, 0.08);

  /* Spacing */
  --radius-sm: 8px;
  --radius-md: 12px;
  --radius-lg: 20px;
  --radius-xl: 28px;

  /* Typography */
  --font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
  --font-mono: 'JetBrains Mono', 'SF Mono', monospace;
}

/* ============ Global Reset ============ */
*,
*::before,
*::after {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html {
  font-size: 16px;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  scroll-behavior: smooth;
}

body {
  font-family: var(--font-family);
  background: var(--bg-primary);
  color: var(--text-primary);
  min-height: 100vh;
  line-height: 1.6;
}

/* Custom Scrollbar */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.15);
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.25);
}

/* ============ App Container ============ */
.app-container {
  min-height: 100vh;
  position: relative;
  overflow-x: hidden;
}

/* ============ Background Geometry ============ */
.bg-geometry {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}

.geo-shape {
  position: absolute;
  border-radius: 50%;
  opacity: 0.5;
}

.geo-1 {
  width: 400px;
  height: 400px;
  background: linear-gradient(135deg, rgba(224, 122, 95, 0.1), rgba(242, 204, 143, 0.1));
  top: -100px;
  right: -100px;
  animation: float1 25s ease-in-out infinite;
}

.geo-2 {
  width: 300px;
  height: 300px;
  background: linear-gradient(135deg, rgba(129, 178, 154, 0.1), rgba(224, 122, 95, 0.08));
  bottom: 10%;
  left: -80px;
  animation: float2 20s ease-in-out infinite;
}

.geo-3 {
  width: 200px;
  height: 200px;
  background: radial-gradient(circle, rgba(242, 204, 143, 0.15), transparent 70%);
  top: 40%;
  right: 10%;
  animation: float3 18s ease-in-out infinite;
}

.geo-4 {
  width: 150px;
  height: 150px;
  background: linear-gradient(135deg, rgba(129, 178, 154, 0.12), transparent);
  bottom: 30%;
  right: 20%;
  animation: float4 22s ease-in-out infinite;
}

@keyframes float1 {
  0%, 100% { transform: translate(0, 0) rotate(0deg); }
  33% { transform: translate(-20px, 30px) rotate(5deg); }
  66% { transform: translate(30px, -20px) rotate(-3deg); }
}

@keyframes float2 {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(40px, -30px); }
}

@keyframes float3 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(-30px, 20px) scale(1.1); }
}

@keyframes float4 {
  0%, 100% { transform: translate(0, 0); }
  33% { transform: translate(20px, -15px); }
  66% { transform: translate(-15px, 25px); }
}

/* ============ Navigation Header ============ */
.nav-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-bottom: 1px solid var(--border-light);
}

.nav-content {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 24px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* Brand */
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  transition: transform 0.2s ease;
}

.brand:hover {
  transform: scale(1.02);
}

.brand-icon {
  width: 32px;
  height: 32px;
  background: var(--accent-primary);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.brand-icon svg {
  width: 18px;
  height: 18px;
}

.brand-text {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.5px;
}

/* Nav Links */
.nav-links {
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.25s ease;
}

.nav-item:hover {
  background: rgba(224, 122, 95, 0.08);
  color: var(--accent-primary);
}

.nav-item.active {
  background: var(--accent-primary);
  color: white;
}

.nav-icon {
  width: 18px;
  height: 18px;
}

/* Nav Status */
.nav-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  background: rgba(129, 178, 154, 0.12);
  border-radius: var(--radius-full);
}

.status-indicator {
  width: 8px;
  height: 8px;
  background: var(--accent-secondary);
  border-radius: 50%;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.6; transform: scale(0.9); }
}

.status-text {
  font-size: 12px;
  font-weight: 500;
  color: var(--accent-secondary);
}

/* ============ Main Content ============ */
.main-content {
  max-width: 1280px;
  margin: 0 auto;
  padding: 32px 24px;
  position: relative;
  z-index: 1;
}

/* ============ Page Transitions ============ */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(16px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* ============ Element Plus Overrides ============ */

/* Buttons */
.el-button--primary {
  background: var(--accent-primary) !important;
  border: none !important;
  font-weight: 600;
}

.el-button--primary:hover {
  background: #c96a51 !important;
  transform: translateY(-1px);
}

.el-button--success {
  background: var(--accent-secondary) !important;
  border: none !important;
}

.el-button--danger {
  background: #e57373 !important;
  border: none !important;
}

.el-button {
  border-radius: var(--radius-sm) !important;
  transition: all 0.25s ease !important;
}

/* Form Elements */
.el-input__wrapper,
.el-textarea__inner {
  background: white !important;
  border: 1px solid var(--border-medium) !important;
  border-radius: var(--radius-sm) !important;
  box-shadow: none !important;
}

.el-input__wrapper:hover,
.el-textarea__inner:hover {
  border-color: var(--accent-primary) !important;
}

.el-input__wrapper.is-focus,
.el-textarea__inner:focus {
  border-color: var(--accent-primary) !important;
  box-shadow: 0 0 0 3px rgba(224, 122, 95, 0.1) !important;
}

.el-input__inner {
  color: var(--text-primary) !important;
}

.el-input__inner::placeholder {
  color: var(--text-muted) !important;
}

/* Select */
.el-select-dropdown {
  background: white !important;
  border: 1px solid var(--border-light) !important;
  border-radius: var(--radius-md) !important;
  box-shadow: var(--shadow-medium) !important;
}

.el-select-dropdown__item {
  color: var(--text-primary) !important;
}

.el-select-dropdown__item:hover {
  background: rgba(224, 122, 95, 0.08) !important;
}

.el-select-dropdown__item.selected {
  color: var(--accent-primary) !important;
  font-weight: 600 !important;
}

/* Tags */
.el-tag {
  border-radius: var(--radius-full) !important;
  font-weight: 500 !important;
}

/* Dialog */
.el-dialog {
  border-radius: var(--radius-lg) !important;
  box-shadow: var(--shadow-strong) !important;
}

.el-dialog__header {
  padding: 20px 24px !important;
  border-bottom: 1px solid var(--border-light);
}

.el-dialog__title {
  font-weight: 600 !important;
  color: var(--text-primary) !important;
}

.el-dialog__body {
  padding: 24px !important;
}

.el-dialog__footer {
  padding: 16px 24px !important;
  border-top: 1px solid var(--border-light);
}

/* Form */
.el-form-item__label {
  color: var(--text-secondary) !important;
  font-weight: 500 !important;
}

/* Switch */
.el-switch.is-checked .el-switch__core {
  background: var(--accent-primary) !important;
  border-color: var(--accent-primary) !important;
}

/* ============ Responsive Design ============ */
@media (max-width: 768px) {
  .nav-content {
    padding: 0 16px;
    height: 56px;
  }

  .brand-text {
    font-size: 18px;
  }

  .nav-item {
    padding: 8px 12px;
    font-size: 13px;
  }

  .nav-item span {
    display: none;
  }

  .nav-item svg {
    width: 22px;
    height: 22px;
  }

  .nav-status {
    display: none;
  }

  .main-content {
    padding: 20px 16px;
  }

  .geo-1 { width: 250px; height: 250px; }
  .geo-2 { width: 200px; height: 200px; }
  .geo-3, .geo-4 { display: none; }
}

@media (max-width: 480px) {
  .main-content {
    padding: 16px 12px;
  }

  .nav-content {
    padding: 0 12px;
  }

  .brand-icon {
    width: 28px;
    height: 28px;
    border-radius: 8px;
  }

  .brand-text {
    font-size: 16px;
  }
}
</style>