import { createRouter, createWebHistory } from 'vue-router'
import TaskList from '../views/TaskList.vue'
import TaskDetail from '../views/TaskDetail.vue'
import Search from '../views/Search.vue'

const routes = [
  { path: '/', name: 'TaskList', component: TaskList },
  { path: '/tasks/:id', name: 'TaskDetail', component: TaskDetail },
  { path: '/search', name: 'Search', component: Search }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router