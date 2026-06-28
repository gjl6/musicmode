/*
 * useAppStore — 全局应用 UI 状态管理
 *
 * 职责：
 *   1. 主题模式：4 种组合 — default-light / default-dark / clay-light / clay-dark
 *   2. 工作台左侧栏折叠状态
 *   3. 编辑抽屉显隐控制
 *   4. 主内容区视图模式切换（table / card）
 */
import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

const VALID_THEMES = ['default-light', 'default-dark', 'clay-light', 'clay-dark']

export const useAppStore = defineStore('app', () => {
  // ---- 主题：4 种组合 ----
  const theme = ref(loadTheme())

  function loadTheme() {
    const stored = localStorage.getItem('app-theme')
    if (stored) {
      // 兼容旧值
      if (stored === 'dark') return 'default-dark'
      if (stored === 'light') return 'default-light'
      if (VALID_THEMES.includes(stored)) return stored
    }
    return window.matchMedia('(prefers-color-scheme: dark)').matches
      ? 'default-dark'
      : 'default-light'
  }

  // 派生：从 theme 解析风格类型
  const themeStyle = computed(() =>
    theme.value.startsWith('clay') ? 'clay' : 'default'
  )

  // 派生：从 theme 解析亮暗
  const colorScheme = computed(() =>
    theme.value.endsWith('dark') ? 'dark' : 'light'
  )

  // ---- 语言：'zh-CN' | 'en' ----
  const locale = ref(loadLocale())

  // ---- 左侧文件目录栏折叠状态 ----
  const sidebarCollapsed = ref(false)

  // ---- 右侧编辑抽屉显隐 ----
  const drawerVisible = ref(false)

  // ---- 主内容区视图模式：'table'（表格）| 'card'（卡片） ----
  const viewMode = ref('table')

  // ---- 增强数据面板 / 拆分元数据 / 替换文本 面板显隐 ----
  const enrichPanelVisible = ref(false)
  const splitPanelVisible = ref(false)
  const replacePanelVisible = ref(false)

  function loadLocale() {
    const stored = localStorage.getItem('app-locale')
    if (stored && ['zh-CN', 'en'].includes(stored)) return stored
    return navigator.language.startsWith('zh') ? 'zh-CN' : 'en'
  }

  // ---- 方法 ----

  /** 切换亮色/暗色（保持当前风格不变） */
  function toggleTheme() {
    const style = themeStyle.value
    theme.value = colorScheme.value === 'dark'
      ? `${style}-light`
      : `${style}-dark`
    localStorage.setItem('app-theme', theme.value)
  }

  /** 切换 Default / Clay 风格（保持亮暗不变） */
  function toggleThemeStyle() {
    const scheme = colorScheme.value
    theme.value = themeStyle.value === 'default'
      ? `clay-${scheme}`
      : `default-${scheme}`
    localStorage.setItem('app-theme', theme.value)
  }

  /** 设置语言并持久化 */
  function setLocale(loc) {
    if (!['zh-CN', 'en'].includes(loc)) return
    locale.value = loc
    localStorage.setItem('app-locale', loc)
  }

  /** 切换左侧栏折叠/展开 */
  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  /** 打开右侧编辑抽屉 */
  function openDrawer() {
    drawerVisible.value = true
  }

  /** 关闭右侧编辑抽屉 */
  function closeDrawer() {
    drawerVisible.value = false
    enrichPanelVisible.value = false
    splitPanelVisible.value = false
    replacePanelVisible.value = false
  }

  function toggleEnrichPanel() {
    enrichPanelVisible.value = !enrichPanelVisible.value
    if (enrichPanelVisible.value) {
      splitPanelVisible.value = false
      replacePanelVisible.value = false
    }
  }

  function toggleSplitPanel() {
    splitPanelVisible.value = !splitPanelVisible.value
    if (splitPanelVisible.value) {
      enrichPanelVisible.value = false
      replacePanelVisible.value = false
    }
  }

  function toggleReplacePanel() {
    replacePanelVisible.value = !replacePanelVisible.value
    if (replacePanelVisible.value) {
      enrichPanelVisible.value = false
      splitPanelVisible.value = false
    }
  }

  /** 切换主内容区视图模式 */
  function setViewMode(mode) {
    viewMode.value = mode
  }

  return {
    theme,
    themeStyle,
    colorScheme,
    locale,
    sidebarCollapsed,
    drawerVisible,
    viewMode,
    toggleTheme,
    toggleThemeStyle,
    setLocale,
    toggleSidebar,
    openDrawer,
    closeDrawer,
    enrichPanelVisible,
    toggleEnrichPanel,
    splitPanelVisible,
    toggleSplitPanel,
    replacePanelVisible,
    toggleReplacePanel,
    setViewMode,
  }
})
