

import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

const VALID_THEMES = ['default-light', 'default-dark', 'clay-light', 'clay-dark']

export const useAppStore = defineStore('app', () => {
    const theme = ref(loadTheme())

  function loadTheme() {
    const stored = localStorage.getItem('app-theme')
    if (stored) {
            if (stored === 'dark') return 'default-dark'
      if (stored === 'light') return 'default-light'
      if (VALID_THEMES.includes(stored)) return stored
    }
    return window.matchMedia('(prefers-color-scheme: dark)').matches
      ? 'default-dark'
      : 'default-light'
  }

    const themeStyle = computed(() =>
    theme.value.startsWith('clay') ? 'clay' : 'default'
  )

    const colorScheme = computed(() =>
    theme.value.endsWith('dark') ? 'dark' : 'light'
  )

    const locale = ref(loadLocale())

    const sidebarCollapsed = ref(false)

    const drawerVisible = ref(false)

    const viewMode = ref('table')

    const enrichPanelVisible = ref(false)
  const splitPanelVisible = ref(false)
  const replacePanelVisible = ref(false)

  function loadLocale() {
    const stored = localStorage.getItem('app-locale')
    if (stored && ['zh-CN', 'en'].includes(stored)) return stored
    return navigator.language.startsWith('zh') ? 'zh-CN' : 'en'
  }


  function toggleTheme() {
    const style = themeStyle.value
    theme.value = colorScheme.value === 'dark'
      ? `${style}-light`
      : `${style}-dark`
    localStorage.setItem('app-theme', theme.value)
  }


  function toggleThemeStyle() {
    const scheme = colorScheme.value
    theme.value = themeStyle.value === 'default'
      ? `clay-${scheme}`
      : `default-${scheme}`
    localStorage.setItem('app-theme', theme.value)
  }


  function setLocale(loc) {
    if (!['zh-CN', 'en'].includes(loc)) return
    locale.value = loc
    localStorage.setItem('app-locale', loc)
  }


  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }


  function openDrawer() {
    drawerVisible.value = true
  }


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
