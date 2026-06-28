/*
 * main.js — 应用入口文件
 *
 * 初始化顺序：
 *   1. createApp(Vue)        创建 Vue 应用实例
 *   2. createPinia()         注入 Pinia 状态管理
 *   3. router                注入 Vue Router 路由
 *   4. naive (组件注册)      注入 Naive UI 组件库
 *   5. mount('#app')         挂载到 index.html 的 #app 容器
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import naive from './plugins/naive-ui.js'
import router from './router/index.js'
import clickOutside from './directive/clickOutside.js'
import i18n from './i18n/index.js'
import './styles/tokens.css'
import './styles/theme-clay.css'
import './styles/naive-overrides.css'
import './style.css'
import App from './App.vue'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(i18n)
app.use(naive)
app.directive('click-outside', clickOutside)
app.mount('#app')
