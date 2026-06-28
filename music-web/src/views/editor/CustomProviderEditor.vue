<template>
  <div class="editor-page">
    <!-- 页头 -->
    <div class="page-head">
      <n-button text size="small" @click="$router.push('/config')">
        <template #icon><n-icon :size="16"><ArrowBackOutline /></n-icon></template>
        {{ $t('common.back') }}
      </n-button>
      <h3>{{ isNew ? $t('customProvider.addTitle') : $t('customProvider.editTitle', { label: form.label }) }}</h3>
    </div>

    <n-spin :show="saving">
      <n-form label-placement="top" require-mark-placement="right">

        <!-- ═══════════ 基本信息 ═══════════ -->
        <n-card :title="$t('customProvider.basicInfo')" class="section">
          <div class="info-row">
            <n-form-item :label="$t('customProvider.label')" required class="info-name">
              <n-input v-model:value="form.label" :placeholder="$t('customProvider.labelPlaceholder')" />
            </n-form-item>
            <n-form-item :label="$t('customProvider.icon')" class="info-icon">
              <n-input v-model:value="form.icon" :placeholder="$t('customProvider.iconPlaceholder')" />
            </n-form-item>
          </div>
          <n-form-item :label="$t('customProvider.description')">
            <n-input v-model:value="form.description" type="textarea" :rows="2" :placeholder="$t('customProvider.descriptionPlaceholder')" />
          </n-form-item>
        </n-card>

        <!-- ═══════════ 接入模式 ═══════════ -->
        <n-card class="section">
          <template #header>
            <div class="card-header-row">
              <span class="card-title-text">{{ $t('customProvider.mode') }}</span>
              <n-radio-group v-model:value="form.mode" name="mode" size="small">
                <n-radio-button value="BRIDGE">
                  <span class="radio-inner">
                    <n-icon :size="15"><LinkOutline /></n-icon>
                    <span>{{ $t('customProvider.modeBridge') }}</span>
                  </span>
                </n-radio-button>
                <n-radio-button value="JAVA">
                  <span class="radio-inner">
                    <n-icon :size="15"><CodeOutline /></n-icon>
                    <span>{{ $t('customProvider.modeJava') }}</span>
                  </span>
                </n-radio-button>
              </n-radio-group>
            </div>
          </template>

          <!-- ── BRIDGE ── -->
          <div v-if="form.mode === 'BRIDGE'" class="mode-body">
            <n-form-item required>
              <template #label>
                <span class="field-label">{{ $t('customProvider.bridgeUrl') }}</span>
                <code class="field-key">bridgeUrl</code>
              </template>
              <n-input v-model:value="bridgeUrl" :placeholder="$t('customProvider.bridgeUrlPlaceholder')" />
            </n-form-item>

            <n-form-item>
              <template #label>
                <span class="field-label">{{ $t('customProvider.authHeader') }}</span>
                <code class="field-key">authHeader</code>
              </template>
              <n-input v-model:value="authHeader" :placeholder="$t('customProvider.authHeaderPlaceholder')" />
            </n-form-item>

            <div class="bridge-params-grid">
              <n-form-item>
                <template #label>
                  <span class="field-label">{{ $t('customProvider.timeout') }}</span>
                  <code class="field-key">timeoutSeconds</code>
                </template>
                <n-input-number v-model:value="bridgeTimeout" :min="5" :max="60" style="width:100%" />
              </n-form-item>
              <n-form-item>
                <template #label>
                  <span class="field-label">{{ $t('customProvider.rateLimitMs') }}</span>
                  <code class="field-key">rateLimitMs</code>
                </template>
                <n-input-number v-model:value="bridgeRateLimitMs" :min="50" :max="10000" style="width:100%" />
              </n-form-item>
              <n-form-item>
                <template #label>
                  <span class="field-label">{{ $t('customProvider.rateLimitRetries') }}</span>
                  <code class="field-key">rateLimitRetries</code>
                </template>
                <n-input-number v-model:value="bridgeRetries" :min="0" :max="10" style="width:100%" />
              </n-form-item>
              <n-form-item>
                <template #label>
                  <span class="field-label">{{ $t('customProvider.rateLimitBackoff') }}</span>
                  <code class="field-key">rateLimitBackoffMs</code>
                </template>
                <n-input-number v-model:value="bridgeBackoffMs" :min="1000" :max="120000" :step="1000" style="width:100%" />
              </n-form-item>
            </div>

            <n-alert type="info" class="api-hint">
              <template #header>{{ $t('customProvider.apiContract') }}</template>
              {{ $t('customProvider.apiContractDesc') }}
              <code>{"title":"...", "artist":"..."}</code>，
              {{ $t('customProvider.apiContractExpect') }}
              <code>{"results":[{...}]}</code>
            </n-alert>
          </div>

          <!-- ── JAVA ── -->
          <div v-if="form.mode === 'JAVA'" class="mode-body">
            <div class="java-toolbar">
              <n-button size="small" quaternary @click="downloadTemplateFile">
                <template #icon><n-icon :size="15"><DownloadOutline /></n-icon></template>
                {{ $t('customProvider.downloadTemplate') }}
              </n-button>
              <n-divider vertical />
              <input
                ref="fileInputRef"
                type="file"
                accept=".java,text/x-java-source,text/x-java"
                style="display:none"
                @change="onFileChange"
              />
              <n-button size="small" @click="$refs.fileInputRef.click()">
                <template #icon><n-icon :size="15"><CloudUploadOutline /></n-icon></template>
                {{ $t('customProvider.uploadSource') }}
              </n-button>
              <span v-if="sourceCode" class="upload-badge">
                <n-tag size="tiny" type="success" :bordered="false" round>
                  {{ $t('customProvider.uploaded', { size: sourceCode.length }) }}
                </n-tag>
              </span>
            </div>

            <!-- 参数列表 -->
            <div v-if="params.length > 0" class="param-section">
              <n-text depth="3" class="param-heading">
                {{ $t('customProvider.paramsHint') }}
              </n-text>
              <div class="param-grid">
                <div v-for="(p, i) in params" :key="i" class="param-field">
                  <div class="param-header">
                    <span class="param-label-text">{{ p.label }}</span>
                    <code class="param-field-name">{{ p.name }}</code>
                    <n-tag v-if="p.required" type="error" size="tiny" :bordered="false">{{ $t('customProvider.required') }}</n-tag>
                    <n-tag v-if="p.sensitive" type="warning" size="tiny" :bordered="false">{{ $t('customProvider.sensitive') }}</n-tag>
                  </div>
                  <div class="param-control">
                    <n-input
                      v-if="p.type === 'STRING' && !p.sensitive"
                      v-model:value="p.value"
                    />
                    <n-input
                      v-else-if="p.type === 'STRING' && p.sensitive"
                      v-model:value="p.value"
                      type="password"
                      :placeholder="$t('customProvider.sensitiveField')"
                    />
                    <n-input-number
                      v-else-if="p.type === 'INT'"
                      v-model:value="p.numValue"
                      style="width:100%"
                      @update:value="(v) => p.value = String(v ?? 0)"
                    />
                    <n-switch
                      v-else-if="p.type === 'BOOLEAN'"
                      :value="p.value === 'true'"
                      @update:value="(v) => p.value = String(v)"
                    />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </n-card>

        <!-- ═══════════ 操作 ═══════════ -->
        <div class="actions-bar">
          <n-button size="small" @click="doTest" :loading="testing" secondary>
            <template #icon><n-icon :size="15"><PulseOutline /></n-icon></template>
            {{ $t('customProvider.testSearch') }}
          </n-button>
          <div class="actions-right">
            <n-button size="small" quaternary @click="$router.push('/config')">{{ $t('common.cancel') }}</n-button>
            <n-button size="small" type="primary" @click="doSave" :loading="saving">{{ $t('customProvider.saveAndExit') }}</n-button>
          </div>
        </div>

      </n-form>
    </n-spin>

    <!-- 测试结果弹窗 -->
    <n-modal
      v-model:show="testModal"
      preset="card"
      :title="$t('customProvider.testResult', { label: form.label })"
      style="max-width:480px"
      :mask-closable="false"
    >
      <div class="test-result">
        <div class="test-status">
          <n-spin v-if="testing" :size="44" />
          <n-icon v-else-if="testResult?.success" :size="52" color="var(--color-success)"><CheckmarkCircleOutline /></n-icon>
          <n-icon v-else :size="52" color="var(--color-destructive)"><CloseCircleOutline /></n-icon>
        </div>
        <p class="test-msg">{{ testResult?.message || (testing ? $t('customProvider.testing') : '') }}</p>
        <p v-if="testResult?.detail" class="test-detail">{{ testResult.detail }}</p>
        <p v-if="testResult?.latencyMs" class="test-meta">{{ $t('customProvider.latency', { ms: testResult.latencyMs }) }}</p>
        <n-alert
          v-if="testResult && !testResult.success && testResult.detail"
          type="error"
          class="test-error-block"
        >
          {{ testResult.detail }}
        </n-alert>
      </div>
      <template #footer>
        <n-button size="small" @click="testModal = false">{{ $t('common.close') }}</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
// 1. Vue 核心
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

// 2. 第三方库
import { useI18n } from 'vue-i18n'
import { useMessage } from 'naive-ui'
import {
  ArrowBackOutline, CheckmarkCircleOutline, CloseCircleOutline,
  CloudUploadOutline, CodeOutline, DownloadOutline,
  LinkOutline, PulseOutline
} from '@vicons/ionicons5'

// 3. 项目内部 — API
import * as api from '@/api/editor/custom-provider.js'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const message = useMessage()

// ── 路由 ──
const id = computed(() => route.params.id)
const isNew = computed(() => !id.value || id.value === 'new')

// ── 状态 ──
const saving = ref(false)
const testing = ref(false)
const testModal = ref(false)
const testResult = ref(null)

// ── 表单数据 ──
const form = ref({ name: '', label: '', description: '', icon: '🔌', mode: 'BRIDGE' })
const bridgeUrl = ref('')
const authHeader = ref('')
const bridgeTimeout = ref(15)
const bridgeRateLimitMs = ref(240)
const bridgeRetries = ref(3)
const bridgeBackoffMs = ref(30000)
const sourceCode = ref('')
const params = ref([])
const fileInputRef = ref(null)
const uploadedParams = ref([])

// ── 计算属性 ──
const providerName = computed(() => {
  if (form.value.name) return form.value.name
  if (form.value.mode === 'JAVA' && uploadedParams.value.length > 0) return t('customProvider.autoDetect')
  return 'custom:' + (form.value.label || 'new').replace(/[^a-zA-Z0-9一-鿿_-]/g, '') || t('customProvider.autoGenerate')
})

// ── 生命周期 ──
onMounted(async () => {
  if (!isNew.value) {
    try {
      const data = await api.fetchOne(id.value)
      form.value = data
      const cfg = JSON.parse(data.configJson || '{}')
      bridgeUrl.value = cfg.bridgeUrl || ''
      authHeader.value = cfg.authHeader || ''
      bridgeTimeout.value = cfg.timeoutSeconds || 15
      bridgeRateLimitMs.value = cfg.rateLimitMs || 240
      bridgeRetries.value = cfg.rateLimitRetries || 3
      bridgeBackoffMs.value = cfg.rateLimitBackoffMs || 30000
      const dbParams = cfg.params || []
      if (dbParams.length > 0) {
        for (const p of dbParams) {
          if (p.type === 'INT') p.numValue = parseInt(p.value) || 0
        }
        params.value = dbParams
      }
      sourceCode.value = data.sourceCode || ''
    } catch { /* 加载失败静默处理 */ }
  }
})

// ── 模板下载 ──
async function downloadTemplateFile() {
  const res = await api.downloadTemplate('MyMusicProvider', form.value.label || 'my-provider')
  const blob = new Blob([res.sourceCode], { type: 'text/java' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = (res.className || 'MyMusicProvider') + '.java'
  a.click()
  URL.revokeObjectURL(url)
  message.success(t('customProvider.templateDownloaded'))
}

// ── 文件上传 ──
function onFileChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.java')) {
    message.error(t('customProvider.javaOnly'))
    e.target.value = ''
    return
  }
  const reader = new FileReader()
  reader.onload = (ev) => {
    sourceCode.value = ev.target.result
    const nameMatch = sourceCode.value.match(
      /return\s+"([^"]+)"\s*;\s*\/\/\s*name\s*\(\)|public\s+String\s+name\s*\(\s*\)\s*\{[^}]*return\s+"([^"]+)"/s
    )
    if (nameMatch) {
      const extracted = nameMatch[1] || nameMatch[2]
      if (extracted) form.value.name = extracted
    }
    const paramList = []
    const regex = /@ConfigParam\s*\(\s*label\s*=\s*"([^"]*)"(.*?)\)\s*(?:private|public)\s+(\w+)\s+(\w+)\s*=\s*([^;]*);/gs
    let m
    while ((m = regex.exec(sourceCode.value)) !== null) {
      const label = m[1]
      const rest = m[2]
      const type = m[3] === 'String' ? 'STRING' : m[3] === 'int' || m[3] === 'long' ? 'INT' : 'BOOLEAN'
      const name = m[4]
      const value = (m[5] || '').trim().replace(/"/g, '')
      paramList.push({
        name, label, type,
        required: rest.includes('required'),
        sensitive: rest.includes('sensitive'),
        value,
        numValue: parseInt(value) || 0
      })
    }
    uploadedParams.value = paramList
    params.value = paramList
    message.success(t('customProvider.paramsParsed', { count: paramList.length }))
  }
  reader.readAsText(file)
  e.target.value = ''
}

// ── 构建配置 ──
function buildConfigJson() {
  if (form.value.mode === 'BRIDGE') {
    return JSON.stringify({
      bridgeUrl: bridgeUrl.value,
      authHeader: authHeader.value,
      timeoutSeconds: bridgeTimeout.value,
      rateLimitMs: bridgeRateLimitMs.value,
      rateLimitRetries: bridgeRetries.value,
      rateLimitBackoffMs: bridgeBackoffMs.value,
    })
  }
  return JSON.stringify({
    params: params.value.map(p => ({
      name: p.name, label: p.label, type: p.type,
      required: p.required, sensitive: p.sensitive, value: p.value
    }))
  })
}

// ── 测试 ──
async function doTest() {
  testing.value = true
  testResult.value = null
  testModal.value = true
  try {
    if (isNew.value) {
      await doSave(true)
      if (!form.value.id) {
        testResult.value = { success: false, message: t('customProvider.testFailed'), detail: t('customProvider.saveFirst') }
        testing.value = false
        return
      }
    }
    testResult.value = await api.testProvider(form.value.id)
  } catch (e) {
    testResult.value = {
      success: false,
      message: t('customProvider.testFailed'),
      detail: e?.response?.data?.error || e.message
    }
  } finally {
    testing.value = false
  }
}

// ── 保存 ──
async function doSave(silent = false) {
  saving.value = true
  try {
    const autoName = form.value.name
      || (form.value.mode === 'JAVA'
        ? ''
        : 'custom:' + (form.value.label || 'bridge').replace(/[^a-zA-Z0-9一-鿿_-]/g, ''))
      || ('custom:' + Date.now())
    const payload = {
      name: autoName,
      label: form.value.label || t('customProvider.unnamed'),
      description: form.value.description || '',
      icon: form.value.icon || '🔌',
      mode: form.value.mode,
      enabled: true,
      configJson: buildConfigJson(),
      sourceCode: form.value.mode === 'JAVA' ? sourceCode.value : null,
      version: '1.0.0',
    }
    if (isNew.value) {
      const res = await api.create(payload)
      form.value.id = res.id
      form.value.name = res.name
      if (!silent) {
        message.success(t('customProvider.created'))
        router.replace('/config')
      }
    } else {
      await api.update(id.value, payload)
      if (!silent) {
        message.success(t('customProvider.saved'))
        router.replace('/config')
      }
    }
  } catch (e) {
    if (!silent) {
      message.error(t('customProvider.saveFailed', { error: e?.response?.data?.error || e.message }))
    } else {
      throw e
    }
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
/* ── 页面容器 ── */
.editor-page {
  padding: var(--space-6) var(--space-6) var(--space-12);
}

/* ── 页头 ── */
.page-head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-5);
}
.page-head h3 {
  margin: 0;
  font-size: var(--text-lg);
  font-weight: 600;
}

/* ── 卡片间距 ── */
.section { margin-bottom: var(--space-4); }

/* ── 基本信息行 ── */
.info-row {
  display: flex;
  gap: var(--space-5);
  align-items: flex-start;
}
.info-name { flex: 1; }
.info-icon { flex-shrink: 0; width: 140px; }

/* ── 模式卡片标题行 ── */
.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.card-title-text { font-weight: 600; font-size: var(--text-md); }

/* ── 模式按钮 ── */
.radio-inner {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
}

/* ── 模式内容 ── */
.mode-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

/* ── 表单标签带字段名 ── */
.field-label { font-size: var(--text-base); }
.field-key {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
  background: var(--color-border-light);
  padding: 1px var(--space-1);
  border-radius: var(--radius-sm);
  font-family: ui-monospace, monospace;
  margin-left: var(--space-2);
}

/* ── BRIDGE 参数双列网格 ── */
.bridge-params-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 var(--space-4);
}

/* ── 接口约定提示 ── */
.api-hint {
  margin-top: var(--space-2);
  font-size: var(--text-base);
}
.api-hint :deep(code) {
  background: var(--color-border-light);
  padding: 0 var(--space-1);
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
}

/* ── JAVA 工具栏 ── */
.java-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}
.upload-badge { margin-left: var(--space-1); }

/* ── 参数区域 ── */
.param-section {
  margin-top: var(--space-2);
  padding: var(--space-3);
  background: var(--color-border-light);
  border-radius: var(--radius-md);
}
.param-heading {
  font-size: var(--text-base);
  margin-bottom: var(--space-3);
  display: block;
}
.param-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-2) var(--space-3);
}
.param-field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}
.param-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}
.param-label-text {
  font-size: var(--text-base);
  font-weight: 500;
}
.param-field-name {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
  background: var(--color-border);
  padding: 0 var(--space-1);
  border-radius: var(--radius-sm);
  font-family: ui-monospace, monospace;
}
.param-control {
  padding-left: var(--space-1);
}

/* ── 操作栏 ── */
.actions-bar {
  display: flex;
  align-items: center;
  margin-top: var(--space-1);
  padding-top: var(--space-1);
}
.actions-right {
  margin-left: auto;
  display: flex;
  gap: var(--space-2);
}

/* ── 测试结果弹窗 ── */
.test-result {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--space-4) 0 var(--space-2);
  text-align: center;
}
.test-status { margin-bottom: var(--space-4); }
.test-msg {
  font-size: var(--text-lg);
  font-weight: 600;
  margin: var(--space-1) 0;
}
.test-detail {
  font-size: var(--text-base);
  color: var(--color-text-secondary);
  margin: var(--space-1) 0;
}
.test-meta {
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
  margin: var(--space-1) 0;
}
.test-error-block {
  margin-top: var(--space-4);
  width: 100%;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: var(--text-base);
  text-align: left;
}
</style>
