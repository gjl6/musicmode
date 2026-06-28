<template>
  <aside class="asb-root">
    <!-- 全局搜索 -->
    <div class="asb-search">
      <n-input
        v-model:value="searchText"
        :placeholder="t('albumManager.searchPlaceholder')"
        clearable
        size="small"
        @update:value="onSearchInput"
      >
        <template #prefix>
          <n-icon :size="15"><SearchOutline /></n-icon>
        </template>
      </n-input>
    </div>

    <div class="asb-divider"></div>

    <!-- 过滤面板 -->
    <div class="asb-filters">
      <!-- 首字母 -->
      <div class="asb-section-label">{{ t('albumManager.letter') }}</div>
      <div class="asb-letter-grid">
        <button
          class="asb-letter-chip"
          :class="{ active: letter === null }"
          @click="$emit('update:letter', null); $emit('query')"
        >{{ t('albumManager.mode_all') }}</button>
        <button
          v-for="ch in letterChips"
          :key="ch"
          class="asb-letter-chip"
          :class="{ active: letter === ch }"
          @click="$emit('update:letter', ch); $emit('query')"
        >{{ ch }}</button>
      </div>

      <!-- 快速模式 -->
      <div class="asb-section-label">{{ t('albumManager.quickMode') }}</div>
      <n-radio-group
        :value="selectMode"
        size="small"
        class="asb-mode-group"
        @update:value="onModeChange"
      >
        <n-radio
          v-for="m in quickModes"
          :key="m.value"
          :value="m.value"
          class="asb-mode-radio"
        >
          <span class="asb-mode-label">{{ m.label }}</span>
        </n-radio>
      </n-radio-group>

      <!-- 叠加过滤 -->
      <div class="asb-section-label">{{ t('albumManager.overlayFilters') }}</div>

      <div class="asb-section-label" style="font-size: 10px; opacity: 0.7;">{{ t('albumManager.filterArtist') }}</div>
      <n-input
        :value="filterArtistName"
        size="tiny"
        style="width:100%"
        :placeholder="t('albumManager.filterArtist')"
        @update:value="v => $emit('update:filterArtistName', v)"
      />

      <div class="asb-section-label" style="font-size: 10px; opacity: 0.7;">{{ t('albumManager.filterMinSongs') }} ~ {{ t('albumManager.filterMaxSongs') }}</div>
      <div class="asb-range-row">
        <n-input-number
          :value="filterMinSongs"
          size="tiny"
          style="width:90px"
          :min="0"
          placeholder="Min"
          @update:value="v => $emit('update:filterMinSongs', v)"
        />
        <span class="asb-range-sep">~</span>
        <n-input-number
          :value="filterMaxSongs"
          size="tiny"
          style="width:90px"
          :min="0"
          placeholder="Max"
          @update:value="v => $emit('update:filterMaxSongs', v)"
        />
      </div>

      <!-- 查询按钮 + 结果数 -->
      <div class="asb-query-row">
        <n-button type="primary" size="small" :loading="loading" block @click="$emit('query')">
          <template #icon><n-icon :size="16"><SearchOutline /></n-icon></template>
          {{ t('albumManager.query') }}
        </n-button>
        <span v-if="total >= 0" class="asb-result">
          {{ t('albumManager.queryResult', { n: total }) }}
        </span>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { ref, computed } from 'vue'
import { SearchOutline } from '@vicons/ionicons5'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

// ── 字母索引 ──
const letterChips = (() => {
  const chips = []
  for (let c = 65; c <= 90; c++) chips.push(String.fromCharCode(c))
  chips.push('0-9', '#')
  return chips
})()

const quickModes = computed(() => [
  { value: 'all',          label: t('albumManager.mode_all') },
  { value: 'incomplete',   label: t('albumManager.mode_incomplete') },
  { value: 'unenriched',   label: t('albumManager.mode_unenriched') },
  { value: 'naked',        label: t('albumManager.mode_naked') },
  { value: 'duplicates',   label: t('albumManager.mode_duplicates') },
])

defineProps({
  letter: { type: String, default: null },
  selectMode: { type: String, default: 'all' },
  filterArtistName: { type: String, default: '' },
  filterMinSongs: { type: Number, default: null },
  filterMaxSongs: { type: Number, default: null },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: -1 },
})

const emit = defineEmits([
  'update:letter', 'update:selectMode',
  'update:filterArtistName',
  'update:filterMinSongs', 'update:filterMaxSongs',
  'search', 'query',
])

// ── 全局搜索（独立，防抖 350ms） ──
const searchText = ref('')
let searchTimer = null
function onSearchInput(val) {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => emit('search', val || ''), 350)
}

// ── 快速模式切换 ──
function onModeChange(mode) {
  emit('update:selectMode', mode)
  emit('query')
}
</script>

<style scoped>
.asb-root {
  display: flex;
  flex-direction: column;
  width: 270px;
  flex-shrink: 0;
  height: 100%;
  background: var(--ct-bg-secondary);
  border-right: 1px solid var(--ct-border);
}

.asb-search {
  padding: 12px 12px 8px;
  flex-shrink: 0;
}

.asb-divider {
  height: 1px;
  margin: 0 12px;
  background: var(--ct-border);
  flex-shrink: 0;
}

.asb-filters {
  flex: 1;
  overflow-y: auto;
  padding: 10px 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.asb-filters::-webkit-scrollbar { width: 4px; }
.asb-filters::-webkit-scrollbar-track { background: transparent; }
.asb-filters::-webkit-scrollbar-thumb { background: transparent; border-radius: 10px; }
.asb-filters:hover::-webkit-scrollbar-thumb { background: var(--sb-scrollbar); }

.asb-section-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--ct-text-2);
  letter-spacing: 0.03em;
}

.asb-letter-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 3px;
}

.asb-letter-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 28px;
  border-radius: 5px;
  border: 1px solid var(--ct-border);
  background: transparent;
  color: var(--ct-text-2);
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  font-family: inherit;
}

.asb-letter-chip:hover {
  border-color: var(--ct-accent);
  color: var(--ct-accent);
  background: rgb(var(--ct-accent-rgb) / 0.06);
}

.asb-letter-chip.active {
  background: var(--ct-accent);
  border-color: var(--ct-accent);
  color: #fff;
  font-weight: 600;
}

.asb-mode-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.asb-mode-radio {
  display: flex;
  align-items: center;
  min-height: 28px;
  padding: 0 4px;
  font-size: 12px;
}

.asb-mode-label {
  flex-shrink: 0;
}

.asb-range-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.asb-range-sep {
  font-size: 11px;
  color: var(--ct-text-3);
}

.asb-query-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 6px;
  border-top: 1px solid var(--ct-border);
}

.asb-result {
  font-size: 11px;
  color: var(--ct-text-3);
  text-align: center;
}
</style>
