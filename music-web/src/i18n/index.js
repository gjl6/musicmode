import { createI18n } from 'vue-i18n'
import { en as enCommon, zhCN as zhCommon } from './common.js'
import { en as enEditor, zhCN as zhEditor } from './editor.js'
import { en as enPlayback, zhCN as zhPlayback } from './playback.js'

function getDefaultLocale() {
  const stored = localStorage.getItem('app-locale')
  if (stored && ['zh-CN', 'en'].includes(stored)) return stored
  const browser = navigator.language
  if (browser.startsWith('zh')) return 'zh-CN'
  return 'en'
}

const en = { ...enCommon, ...enEditor, ...enPlayback }
const zhCN = { ...zhCommon, ...zhEditor, ...zhPlayback }

const i18n = createI18n({
  legacy: false,
  locale: getDefaultLocale(),
  fallbackLocale: 'zh-CN',
  messages: { 'zh-CN': zhCN, en },
})

export default i18n
