

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
