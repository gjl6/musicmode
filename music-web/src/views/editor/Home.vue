<template>
  <div class="home">
    <div class="hero">
      <h1 class="title">Music Pipeline</h1>
      <p class="subtitle">{{ t('home.subtitle') }}</p>
      <n-button type="primary" size="large" @click="$router.push('/player')">
        {{ t('home.cta') }}
      </n-button>
    </div>

    <div class="features">
      <div v-for="(f, i) in featureList" :key="i" class="feature-card">
        <div class="feature-icon">
          <n-icon :size="28">{{ f.icon }}</n-icon>
        </div>
        <h3>{{ f.title }}</h3>
        <p>{{ f.desc }}</p>
      </div>
    </div>

    <div class="lang-switch">
      <n-button-group>
        <n-button
          :type="locale === 'zh-CN' ? 'primary' : 'default'"
          @click="switchLang('zh-CN')"
        >
          中文
        </n-button>
        <n-button
          :type="locale === 'en' ? 'primary' : 'default'"
          @click="switchLang('en')"
        >
          English
        </n-button>
      </n-button-group>
      <n-button
        style="margin-left: 8px"
        @click="app.toggleTheme()"
      >
        <n-icon :size="16">
          <SunnyOutline v-if="app.theme === 'dark'" />
          <MoonOutline v-else />
        </n-icon>
      </n-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '@/store/editor/app.js'
import {
  MusicalNotesOutline,
  DocumentTextOutline,
  LayersOutline,
  SunnyOutline,
  MoonOutline,
} from '@vicons/ionicons5'

const { t, locale } = useI18n()
const app = useAppStore()

const featureList = computed(() => [
  {
    icon: MusicalNotesOutline,
    title: t('home.features.formats'),
    desc: t('home.features.formatsDesc'),
  },
  {
    icon: DocumentTextOutline,
    title: t('home.features.metadata'),
    desc: t('home.features.metadataDesc'),
  },
  {
    icon: LayersOutline,
    title: t('home.features.batch'),
    desc: t('home.features.batchDesc'),
  },
])

function switchLang(lang) {
  app.setLocale(lang)
  locale.value = lang
}
</script>

<style scoped>
.home {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 64px;
  padding: 48px 24px;
}

.hero {
  text-align: center;
  max-width: 520px;
}

.title {
  font-size: 48px;
  font-weight: 700;
  letter-spacing: -1.5px;
  margin: 0 0 16px;
}

.subtitle {
  font-size: 16px;
  color: var(--ct-text-2);
  margin: 0 0 32px;
  line-height: 1.6;
}

.features {
  display: flex;
  gap: 24px;
  max-width: 720px;
}

.feature-card {
  flex: 1;
  text-align: center;
  padding: 28px 20px;
  border-radius: var(--radius-xl);
  background: var(--gradient-card, var(--color-surface));
  border: var(--border-width-strong) solid var(--color-border);
  box-shadow: var(--shadow-md);
  transition: all var(--transition-base);
}

.feature-card:hover {
  background: var(--gradient-card-hover, var(--color-surface-hover));
  box-shadow: var(--shadow-lg);
  transform: var(--transform-card-hover);
}

.feature-icon {
  margin-bottom: 12px;
  color: var(--ct-accent);
}

.feature-card h3 {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 8px;
}

.feature-card p {
  font-size: 12px;
  color: var(--ct-text-3);
  margin: 0;
  line-height: 1.5;
}

.lang-switch {
  position: fixed;
  bottom: 24px;
  right: 24px;
}
</style>
