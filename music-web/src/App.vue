<template>
  <n-config-provider
    :theme="app.colorScheme === 'dark' ? darkTheme : lightTheme"
    :locale="naiveLocale"
    :date-locale="naiveDateLocale"
  >
    <n-message-provider>
      <n-dialog-provider>
        <TopNavBar />
        <router-view />
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<script setup>
import { computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { darkTheme, lightTheme, zhCN, enUS, dateZhCN, dateEnUS, createDiscreteApi } from 'naive-ui'
import { useAppStore } from '@/store/editor/app.js'
import { useAuthStore } from '@/store/auth.js'
import TopNavBar from '@/components/editor/layout/TopNavBar.vue'

const app = useAppStore()
const auth = useAuthStore()
const { locale } = useI18n()

// 初始同步 i18n locale 到 store（处理 localStorage/browser 检测的值）
if (app.locale !== locale.value) {
  app.setLocale(locale.value)
}

// 全局 message（独立于 provider 树，axios 拦截器中使用）
const { message: globalMsg } = createDiscreteApi(['message'])
window.$message = globalMsg

// 初始化认证状态：如果已有 token，拉取用户信息和权限
onMounted(async () => {
  if (auth.isAuthenticated && !auth.user) {
    await auth.fetchMe()
  }
})

const naiveLocale = computed(() =>
  app.locale === 'zh-CN' ? zhCN : enUS
)

const naiveDateLocale = computed(() =>
  app.locale === 'zh-CN' ? dateZhCN : dateEnUS
)

// store → i18n 联动
watch(() => app.locale, (val) => {
  locale.value = val
})

watch(
  () => app.theme,
  (val) => {
    const html = document.documentElement
    const isDark = val.endsWith('dark')
    html.classList.toggle('dark', isDark)
    html.classList.toggle('clay-light', val === 'clay-light')
    html.classList.toggle('clay-dark', val === 'clay-dark')
  },
  { immediate: true },
)
</script>
