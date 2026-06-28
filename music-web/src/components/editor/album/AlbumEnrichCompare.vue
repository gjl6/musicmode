<template>
  <div class="aec-root" :class="{ 'aec-compact': compact }">
    <n-spin :show="loading">
      <div v-if="loading" class="aec-loading">
        <p>正在查询各标签源…</p>
      </div>

      <!-- ═══ Compact 模式：纵向表格（行=数据源，列=字段） ═══ -->
      <div v-else-if="compact && results.length > 0">
        <div class="aec-v-mode">
          <n-radio-group v-model:value="writeMode" size="small">
            <n-radio-button value="fill">只填空</n-radio-button>
            <n-radio-button value="overwrite">全覆盖</n-radio-button>
          </n-radio-group>
          <n-button size="small" @click="$emit('refreshNeeded')">重新查询</n-button>
        </div>

        <div class="aec-v-table-wrap">
          <table class="aec-v-table">
            <thead>
              <tr>
                <th class="aec-v-th-src">来源</th>
                <th class="aec-v-th-cover">封面</th>
                <th class="aec-v-th-intro">简介</th>
                <th class="aec-v-th-type">类型</th>
                <th class="aec-v-th-year">年份</th>
                <th class="aec-v-th-company">公司</th>
                <th class="aec-v-th-lang">语言</th>
                <th class="aec-v-th-action">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="r in results"
                :key="r.source"
                class="aec-v-row"
              >
                <td class="aec-v-td-src">
                  <div class="aec-v-src-name">{{ sourceLabel(r.source) }}</div>
                  <n-tag
                    :type="r.score >= 80 ? 'success' : r.score >= 60 ? 'warning' : 'default'"
                    size="tiny"
                  >{{ r.score }}分</n-tag>
                </td>
                <td
                  class="aec-clickable aec-v-td-cover"
                  @click="handleCellClick('coverUrl', r)"
                >
                  <img v-if="r.info.coverUrl" :src="r.info.coverUrl" class="aec-cover-thumb" />
                  <span v-else class="aec-empty">—</span>
                </td>
                <td
                  class="aec-clickable aec-v-td-intro"
                  :class="{ 'aec-different': isDifferent('introduction', r.info) }"
                  :title="r.info.introduction || ''"
                  @click="handleCellClick('introduction', r)"
                >
                  <span class="aec-ellipsis">{{ r.info.introduction || '—' }}</span>
                </td>
                <td
                  class="aec-clickable aec-v-td-type"
                  :class="{ 'aec-different': isDifferent('albumType', r.info) }"
                  @click="handleCellClick('albumType', r)"
                >{{ r.info.albumType || '—' }}</td>
                <td
                  class="aec-clickable aec-v-td-year"
                  :class="{ 'aec-different': isDifferent('albumYear', r.info) }"
                  @click="handleCellClick('albumYear', r)"
                >{{ r.info.albumYear || '—' }}</td>
                <td
                  class="aec-clickable aec-v-td-company"
                  :class="{ 'aec-different': isDifferent('company', r.info) }"
                  :title="r.info.company || ''"
                  @click="handleCellClick('company', r)"
                >{{ r.info.company || '—' }}</td>
                <td
                  class="aec-clickable aec-v-td-lang"
                  :class="{ 'aec-different': isDifferent('language', r.info) }"
                  @click="handleCellClick('language', r)"
                >{{ r.info.language || '—' }}</td>
                <td class="aec-v-td-action">
                  <n-button size="tiny" type="primary" @click="handleFillAll(r)">应用</n-button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- ═══ 非 Compact 模式：横向对比表格（批量工具用） ═══ -->
      <div v-else-if="!compact && results.length > 0">
        <n-table :single-line="false" size="small" class="aec-table">
          <thead>
            <tr>
              <th style="width:80px">字段</th>
              <th style="min-width:110px">
                <n-tag type="info" size="small">当前 DB</n-tag>
              </th>
              <th
                v-for="r in results"
                :key="r.source"
                :style="{ minWidth: '140px', background: selectedSource === r.source ? 'var(--ct-bg-hover)' : '' }"
              >
                <div class="aec-source-header">
                  <span>{{ sourceLabel(r.source) }}</span>
                  <n-tag :type="r.score >= 80 ? 'success' : r.score >= 60 ? 'warning' : 'default'" size="small">
                    {{ r.score }}分
                  </n-tag>
                </div>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="field in FIELDS" :key="field.key">
              <td class="aec-field-label">{{ field.label }}</td>
              <td class="aec-field-current">
                <span :class="{ 'aec-empty': !currentFieldValue(field.key) }">
                  {{ formatField(field.key, currentFieldValue(field.key)) }}
                </span>
              </td>
              <td
                v-for="r in results"
                :key="`${r.source}-${field.key}`"
                :class="['aec-field-value', {
                  'aec-selected': selectedSource === r.source,
                  'aec-different': isDifferent(field.key, r.info)
                }]"
                @click="selectedSource = r.source"
              >
                <span :class="{ 'aec-empty': !r.info[field.key] }">
                  {{ formatField(field.key, r.info[field.key]) }}
                </span>
              </td>
            </tr>
          </tbody>
        </n-table>

        <div class="aec-actions">
          <div v-if="selectedSource" class="aec-selected-hint">
            已选择：<n-tag type="success">{{ sourceLabel(selectedSource) }}</n-tag>
          </div>
          <div class="aec-mode">
            <n-radio-group v-model:value="writeMode" size="small">
              <n-radio-button value="fill">只填空</n-radio-button>
              <n-radio-button value="overwrite">全覆盖</n-radio-button>
            </n-radio-group>
            <n-button
              type="primary"
              size="small"
              :loading="applying"
              :disabled="!selectedSource"
              @click="doApply"
            >
              应用选中源
            </n-button>
            <n-button size="small" @click="$emit('refreshNeeded')">重新查询</n-button>
          </div>
        </div>
      </div>

      <!-- 无结果 -->
      <n-empty v-else-if="!loading" description="无 Provider 返回数据">
        <template #extra>
          <n-button size="small" @click="$emit('refreshNeeded')">重新查询</n-button>
        </template>
      </n-empty>
    </n-spin>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { searchAlbumProviders, applyAlbumEnrich } from '@/api/editor/album-manage.js'

const FIELDS = [
  { key: 'introduction', label: '简介' },
  { key: 'albumType', label: '类型' },
  { key: 'albumYear', label: '年份' },
  { key: 'company', label: '公司' },
  { key: 'language', label: '语言' },
  { key: 'coverUrl', label: '封面' },
]

const SOURCE_LABELS = {
  qqmusic: 'QQ音乐', netease: '网易云音乐', itunes: 'iTunes',
  musicbrainz: 'MusicBrainz', kugou: '酷狗音乐', kuwo: '酷我音乐', migu: '咪咕音乐',
}

const props = defineProps({
  albumId: { type: Number, required: true },
  compact: { type: Boolean, default: false },
})

const emit = defineEmits(['applied', 'refreshNeeded', 'fillField', 'fillAll'])

const message = useMessage()

const loading = ref(false)
const applying = ref(false)
const results = ref([])
const current = ref({})
const selectedSource = ref(null)
const writeMode = ref('fill')

defineExpose({ load })

watch(() => props.albumId, () => { load() }, { immediate: true })

async function load() {
  if (!props.albumId) return
  loading.value = true
  results.value = []
  current.value = {}
  selectedSource.value = null
  try {
    const res = await searchAlbumProviders(props.albumId)
    current.value = res.current || {}
    results.value = (res.results || []).map(r => ({
      source: r.source,
      score: r.score,
      info: {
        introduction: r.introduction,
        albumType: r.albumType,
        albumYear: r.albumYear,
        company: r.company,
        language: r.language,
        coverUrl: r.coverUrl,
      },
    }))
    if (results.value.length > 0) {
      selectedSource.value = results.value[0].source
    }
  } catch (e) {
    console.error('[AlbumEnrichCompare] 查询失败:', e)
    message.warning('查询失败: ' + (e?.response?.data?.error || e.message))
  } finally {
    loading.value = false
  }
}

function sourceLabel(source) {
  return SOURCE_LABELS[source] || source
}

function currentFieldValue(fieldKey) {
  if (fieldKey === 'coverUrl') return current.value.albumCover
  return current.value[fieldKey]
}

function formatField(key, val) {
  if (val === null || val === undefined || val === '') return '—'
  if (key === 'coverUrl') return val ? '有封面' : '—'
  return String(val)
}

function isDifferent(fieldKey, info) {
  if (fieldKey === 'coverUrl') return false
  const cur = currentFieldValue(fieldKey)
  const val = info[fieldKey]
  if (cur === null || cur === undefined || cur === '') return val !== null && val !== undefined && val !== ''
  return String(cur) !== String(val ?? '')
}

// ── 单元格点击 → 填入表单 ──
function handleCellClick(fieldKey, result) {
  if (props.compact) {
    emit('fillField', fieldKey, result.info[fieldKey])
  }
}

// ── [应用] 按钮 → 填入整行 ──
function handleFillAll(result) {
  if (props.compact) {
    emit('fillAll', { ...result.info }, writeMode.value)
  }
}

// ── 直接写入 DB（非 compact 模式用） ──
async function doApply() {
  applying.value = true
  try {
    const res = await applyAlbumEnrich(props.albumId, selectedSource.value, writeMode.value)
    if (res?.success) {
      message.success(`已写入: ${(res.fieldsWritten || []).join(', ')}`)
      emit('applied')
    }
  } catch (e) {
    console.error('[AlbumEnrichCompare] 应用失败:', e)
    message.warning('应用失败: ' + (e?.response?.data?.error || e.message))
  } finally {
    applying.value = false
  }
}
</script>

<style scoped>
.aec-root { min-height: 200px; }
.aec-loading { text-align: center; padding: 32px 0; color: var(--ct-text-2); }

.aec-empty { color: var(--ct-text-disabled); font-style: italic; }
.aec-different { font-weight: 600; }

/* ═══ Compact 模式：纵向表格 ═══ */

.aec-v-mode {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.aec-v-table-wrap {
  max-height: 400px;
  overflow-y: auto;
  overflow-x: auto;
}

.aec-v-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  table-layout: fixed;
}

.aec-v-table th {
  position: sticky;
  top: 0;
  background: var(--ct-bg-secondary);
  padding: 6px 6px;
  text-align: left;
  font-weight: 600;
  border-bottom: 1px solid var(--ct-border);
  z-index: 1;
  font-size: 10px;
  letter-spacing: 0.02em;
  color: var(--ct-text-2);
}

.aec-v-table td {
  padding: 7px 6px;
  border-bottom: 1px solid var(--ct-border);
  vertical-align: middle;
}

.aec-v-table tbody tr:hover td {
  background: var(--ct-bg-secondary);
}

.aec-v-table tbody tr:hover td.aec-v-td-action {
  background: var(--ct-bg-secondary);
}

/* 列宽 */
.aec-v-th-src, .aec-v-td-src { width: 85px; }
.aec-v-th-cover, .aec-v-td-cover { width: 60px; text-align: center; }
.aec-v-th-intro, .aec-v-td-intro { width: 200px; }
.aec-v-th-type, .aec-v-td-type { width: 55px; text-align: center; }
.aec-v-th-year, .aec-v-td-year { width: 50px; text-align: center; }
.aec-v-th-company, .aec-v-td-company { width: 100px; }
.aec-v-th-lang, .aec-v-td-lang { width: 50px; text-align: center; }
.aec-v-th-action, .aec-v-td-action { width: 56px; text-align: center; }

.aec-v-td-src { vertical-align: middle; }
.aec-v-td-intro { vertical-align: middle; }

.aec-v-src-name {
  font-size: 12px;
  font-weight: 500;
  line-height: 1.3;
}

/* 可点击单元格 */
.aec-clickable {
  cursor: pointer;
  text-decoration: underline;
  text-decoration-color: var(--n-color-primary);
  text-underline-offset: 2px;
  text-decoration-thickness: 1px;
  transition: color 0.1s;
}

.aec-clickable:hover {
  color: var(--n-color-primary);
}

/* 文字溢出省略 */
.aec-ellipsis {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 封面缩略图 */
.aec-cover-thumb {
  width: 44px;
  height: 44px;
  border-radius: 4px;
  object-fit: cover;
  display: block;
  margin: 0 auto;
}

/* ═══ 非 Compact 模式：横向对比表格 ═══ */

.aec-table { margin-bottom: 12px; }

.aec-source-header {
  display: flex; align-items: center; justify-content: center;
  gap: 6px; flex-wrap: wrap;
}

.aec-field-label {
  font-weight: 600; color: var(--ct-text-2); white-space: nowrap;
}

.aec-field-current { color: var(--ct-text-3); }
.aec-field-value { cursor: pointer; transition: background 0.15s; }
.aec-field-value:hover { background: var(--ct-bg-hover); }
.aec-selected { outline: 2px solid var(--n-color-target); outline-offset: -2px; }

.aec-actions {
  display: flex; flex-direction: column; gap: 10px; margin-top: 12px;
}

.aec-selected-hint {
  display: flex; align-items: center; gap: 6px;
  font-size: 13px; color: var(--ct-text-2);
}

.aec-mode {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
}
</style>
