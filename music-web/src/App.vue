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

if (app.locale !== locale.value) {
  app.setLocale(locale.value)
}

const { message: globalMsg } = createDiscreteApi(['message'])
window.$message = globalMsg

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
