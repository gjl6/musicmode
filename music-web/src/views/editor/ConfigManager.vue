<template>
  <div class="config-page">

    <div class="tab-bar">
      <n-button
        v-for="tab in tabs"
        :key="tab.key"
        :type="activeTab === tab.key ? 'primary' : 'default'"
        :text="activeTab !== tab.key"
        size="small"
        @click="activeTab = tab.key"
      >
        <template #icon><n-icon :size="14"><component :is="tab.icon" /></n-icon></template>
        {{ tab.label }}
      </n-button>
      <div class="tab-actions">
        <n-button size="tiny" text @click="refreshCache">
          <template #icon><n-icon :size="14"><RefreshOutline /></n-icon></template>
          {{ $t('config.refreshCache') }}
        </n-button>
      </div>
    </div>


    <div v-if="activeTab === 'enrich'" class="tab-content">
      <n-spin :show="loading">
        <div class="provider-grid">
          <div
            v-for="p in providerList"
            :key="p.code"
            class="provider-card"
            :class="{ 'provider-disabled': !p.enabled }"
          >

            <div class="card-head">
              <div class="card-title">
                <span class="provider-icon" :style="{ background: p.color }">{{ p.icon }}</span>
                <div class="card-name-row">
                  <span class="provider-name">{{ p.label }}</span>
                  <span class="provider-version">v{{ p.version }}</span>
                  <n-tag
                    :type="p.enabled ? 'success' : 'default'"
                    size="tiny"
                    :bordered="false"
                  >{{ p.enabled ? $t('configEnrich.online') : $t('configEnrich.offline') }}</n-tag>
                </div>
              </div>
              <n-switch
                :value="p.enabled"
                size="small"
                @update:value="(v) => toggleProvider(p.code, v)"
              />
            </div>


            <p class="card-desc">{{ p.description }}</p>


            <div class="card-tags">
              <n-tag
                v-for="t in p.metadataTags"
                :key="t"
                size="tiny"
                :bordered="false"
                type="info"
              >{{ t }}</n-tag>
            </div>


            <div class="card-actions">
              <n-button size="tiny" quaternary @click="openConfig(p)">
                <template #icon><n-icon :size="14"><SettingsOutline /></n-icon></template>
                {{ $t('configEnrich.config') }}
              </n-button>
              <n-button
                size="tiny"
                quaternary
                :loading="p.testing"
                @click="testProvider(p)"
              >
                <template #icon><n-icon :size="14"><PulseOutline /></n-icon></template>
                {{ $t('configEnrich.test') }}
              </n-button>
            </div>
          </div>

          <div
            v-for="p in customProviders"
            :key="'cp-' + p.id"
            class="provider-card custom-provider"
          >
            <div class="card-head">
              <div class="card-title">
                <span class="provider-icon" style="background:#f0f0f0">{{ p.icon || '🔌' }}</span>
                <div class="card-name-row">
                  <span class="provider-name">{{ p.label }}</span>
                  <n-tag size="tiny" :bordered="false" type="warning">{{ $t('configEnrich.custom') }}</n-tag>
                </div>
              </div>
              <n-switch
                :value="p.enabled"
                size="small"
                @update:value="(v) => toggleCustom(p, v)"
              />
            </div>
            <p class="card-desc">{{ p.description || $t('configEnrich.customDesc', { mode: p.mode }) }}</p>
            <div class="card-actions">
              <n-button size="tiny" quaternary @click="$router.push('/custom-providers/' + p.id)">{{ $t('configEnrich.config') }}</n-button>
              <n-button size="tiny" quaternary :loading="p._testing" @click="testCustom(p)">{{ $t('configEnrich.test') }}</n-button>
              <n-popconfirm @positive-click="removeCustom(p)">
                <template #trigger>
                  <n-button size="tiny" quaternary type="error">{{ $t('watch.delete') }}</n-button>
                </template>
                {{ $t('configEnrich.deleteConfirm', { label: p.label }) }}
              </n-popconfirm>
            </div>
          </div>


          <div class="provider-card add-card" @click="$router.push('/custom-providers/new')">
            <div class="add-content">
              <n-icon :size="28" color="#aaa"><AddCircleOutline /></n-icon>
              <span class="add-text">{{ $t('configEnrich.addCustom') }}</span>
            </div>
          </div>
        </div>
      </n-spin>
    </div>


    <n-modal
      v-model:show="configModal"
      preset="card"
      :title="$t('configEnrich.configTitle', { label: editingProvider?.label })"
      style="max-width: 560px"
      :mask-closable="false"
    >
      <n-tabs v-if="editingProvider" type="segment" size="small">

        <n-tab-pane name="rate" :tab="$t('configEnrich.rateLimit')">
          <n-grid :cols="3" :x-gap="12" :y-gap="12">
            <n-grid-item v-for="f in providerFields" :key="f.key">
              <n-form-item :label="f.label" label-placement="top" size="small">
                <n-input-number
                  :value="getNumber('enrich.' + editingProvider.code + '.' + f.key)"
                  :min="f.min" :max="f.max" :step="f.step"
                  size="small"
                  @update:value="(v) => saveField('enrich.' + editingProvider.code + '.' + f.key, String(v ?? f.fallback))"
                />
              </n-form-item>
            </n-grid-item>
            <n-grid-item>
              <n-form-item :label="$t('configEnrich.retries')" label-placement="top" size="small">
                <n-input-number
                  :value="getNumber('enrich.' + editingProvider.code + '.rate_limit_retries')"
                  :min="0" :max="10" size="small"
                  @update:value="(v) => saveField('enrich.' + editingProvider.code + '.rate_limit_retries', String(v ?? 3))"
                />
              </n-form-item>
            </n-grid-item>
            <n-grid-item>
              <n-form-item :label="$t('configEnrich.backoffMs')" label-placement="top" size="small">
                <n-input-number
                  :value="getNumber('enrich.' + editingProvider.code + '.rate_limit_backoff_ms')"
                  :min="1000" :max="120000" :step="1000" size="small"
                  @update:value="(v) => saveField('enrich.' + editingProvider.code + '.rate_limit_backoff_ms', String(v ?? 30000))"
                />
              </n-form-item>
            </n-grid-item>
          </n-grid>
        </n-tab-pane>


        <n-tab-pane name="urls" :tab="$t('configEnrich.apiUrls')">
          <n-form-item
            v-for="u in editingProvider.urls"
            :key="u.key"
            :label="u.label"
            label-placement="left"
            label-width="80"
            size="small"
          >
            <n-input
              :value="getString('enrich.' + editingProvider.code + '.' + u.key, '')"
              size="small"
              @update:value="(v) => saveField('enrich.' + editingProvider.code + '.' + u.key, v)"
            />
          </n-form-item>
        </n-tab-pane>
      </n-tabs>

      <template #footer>
        <n-space justify="end">
          <n-button size="small" @click="copyDefaults(editingProvider.code)">{{ $t('configEnrich.restoreDefaults') }}</n-button>
          <n-button size="small" type="primary" @click="configModal = false">{{ $t('configEnrich.done') }}</n-button>
        </n-space>
      </template>
    </n-modal>


    <n-modal
      v-model:show="testModal"
      preset="card"
      :title="$t('configEnrich.testTitle', { label: testResult?.label })"
      style="max-width: 480px"
    >
      <div v-if="testResult" class="test-result">
        <div class="test-status">
          <n-spin v-if="testResult.success === null" :size="40" />
          <n-icon v-else-if="testResult.success" :size="40" color="#18a058"><CheckmarkCircleOutline /></n-icon>
          <n-icon v-else :size="40" color="#d03050"><CloseCircleOutline /></n-icon>
        </div>
        <p class="test-msg">{{ testResult.message }}</p>
        <p v-if="testResult.latencyMs" class="test-detail">{{ $t('configEnrich.latency') }} {{ testResult.latencyMs }}{{ $t('configEnrich.ms') }}</p>
        <p v-if="testResult.detail" class="test-detail">{{ testResult.detail }}</p>
      </div>
      <template #footer>
        <n-button size="small" @click="testModal = false">{{ $t('watch.close') }}</n-button>
      </template>
    </n-modal>


    <div v-if="activeTab === 'watch'" class="tab-content">
      <n-spin :show="watchLoading">
        <div class="provider-grid">

          <div
            v-for="p in watchProfiles"
            :key="p.id"
            class="provider-card"
            :class="{ 'provider-disabled': !p.enabled }"
          >
            <div class="card-head">
              <div class="card-title">
                <span class="provider-icon" style="background:#f0f0f0">⚡</span>
                <div class="card-name-row">
                  <span class="provider-name">{{ p.name }}</span>
                  <n-tag :type="p.enabled ? 'success' : 'default'" size="tiny" :bordered="false">
                    {{ p.enabled ? $t('watch.enabled') : $t('watch.disabled') }}
                  </n-tag>
                </div>
              </div>
              <n-switch :value="p.enabled" size="small" @update:value="(v) => toggleWatchEnabled(p, v)" />
            </div>


            <p class="watch-path">
              <n-text depth="2" style="font-size:12px">📁 {{ p.watchPath === '/' ? $t('watch.rootDir') : p.watchPath }}</n-text>
            </p>


            <div class="watch-steps-row">
              <n-tag
                v-for="(s, si) in p.steps"
                :key="si"
                size="tiny"
                :bordered="false"
                :type="si === p.steps.length - 1 ? 'primary' : 'info'"
              >{{ stepLabel(s.name) }}</n-tag>
              <n-text v-if="!p.steps || p.steps.length === 0" depth="3" style="font-size:11px">{{ $t('watch.noSteps') }}</n-text>
            </div>


            <div class="card-actions">
              <n-switch :value="p.autoTrigger" size="small" @update:value="(v) => toggleWatchAuto(p, v)">
                <template #checked>{{ $t('watch.auto') }}</template>
                <template #unchecked>{{ $t('watch.manual') }}</template>
              </n-switch>
              <n-button size="tiny" quaternary @click="openWatchEdit(p)">
                <template #icon><n-icon :size="14"><SettingsOutline /></n-icon></template>
                {{ $t('watch.edit') }}
              </n-button>
              <n-button size="tiny" quaternary @click="openWatchExecute(p)">
                <template #icon><n-icon :size="14"><PulseOutline /></n-icon></template>
                {{ $t('watch.execute') }}
              </n-button>
              <n-popconfirm @positive-click="removeWatchProfile(p.id)">
                <template #trigger>
                  <n-button size="tiny" quaternary type="error">{{ $t('watch.delete') }}</n-button>
                </template>
                {{ $t('watch.deleteConfirm', { name: p.name }) }}
              </n-popconfirm>
            </div>
          </div>


          <div class="provider-card add-card" @click="openWatchCreate()">
            <div class="add-content">
              <n-icon :size="28" color="#aaa"><AddCircleOutline /></n-icon>
              <span class="add-text">{{ $t('watch.newTask') }}</span>
            </div>
          </div>
        </div>
      </n-spin>


      <n-modal
        v-model:show="watchModal"
        preset="card"
        :title="watchEditingId ? $t('watch.editTask') : $t('watch.newTask')"
        style="max-width: 640px"
        :mask-closable="false"
      >
        <div v-if="watchDraft">
          <n-form-item :label="$t('watch.name')" label-placement="left" label-width="80" size="small">
            <n-input v-model:value="watchDraft.name" :placeholder="$t('watch.namePlaceholder')" size="small" />
          </n-form-item>
          <n-form-item :label="$t('watch.watchPath')" label-placement="left" label-width="80" size="small">
            <n-input v-model:value="watchDraft.watchPath" :placeholder="$t('watch.watchPathPlaceholder')" size="small" />
          </n-form-item>
          <n-form-item :label="$t('watch.description')" label-placement="left" label-width="80" size="small">
            <n-input v-model:value="watchDraft.description" :placeholder="$t('watch.descriptionPlaceholder')" size="small" />
          </n-form-item>
          <n-grid :cols="2" :x-gap="12">
            <n-grid-item>
              <n-form-item :label="$t('watch.priority')" label-placement="left" label-width="80" size="small">
                <n-input-number v-model:value="watchDraft.priority" :min="0" :max="1000" size="small" />
              </n-form-item>
            </n-grid-item>
            <n-grid-item>
              <n-form-item :label="$t('watch.enabled')" label-placement="left" label-width="80" size="small">
                <n-switch v-model:value="watchDraft.enabled" size="small" />
              </n-form-item>
            </n-grid-item>
            <n-grid-item>
              <n-form-item :label="$t('watch.autoTrigger')" label-placement="left" label-width="80" size="small">
                <n-switch v-model:value="watchDraft.autoTrigger" size="small" />
              </n-form-item>
            </n-grid-item>
          </n-grid>


          <n-divider style="margin: 12px 0 8px">{{ $t('watch.processingSteps') }}</n-divider>
          <div class="step-list">
            <div
              v-for="(step, si) in watchDraft.steps"
              :key="si"
              class="step-item"
            >

              <div class="step-head">
                <span class="step-num">{{ si + 1 }}</span>
                <n-select
                  :value="step.name"
                  :options="stepTypeOptions"
                  size="small"
                  style="flex:1;max-width:200px"
                  @update:value="(v) => onStepTypeChange(si, v)"
                />
                <n-button-group size="small">
                  <n-button size="small" :disabled="si === 0" @click="moveStep(si, -1)">
                    <template #icon><n-icon :size="16"><ArrowUpOutline /></n-icon></template>
                  </n-button>
                  <n-button size="small" :disabled="si === watchDraft.steps.length - 1" @click="moveStep(si, 1)">
                    <template #icon><n-icon :size="16"><ArrowDownOutline /></n-icon></template>
                  </n-button>
                </n-button-group>
                <n-popconfirm @positive-click="removeStep(si)">
                  <template #trigger>
                    <n-button size="small" text type="error">
                      <template #icon><n-icon :size="16"><TrashOutline /></n-icon></template>
                    </n-button>
                  </template>
                  {{ $t('watch.removeStepConfirm') }}
                </n-popconfirm>
              </div>


              <div v-if="step.name === 'organize'" class="step-params">
                <div class="step-params-grid">
                  <n-form-item :label="$t('watch.mode')" label-placement="left" label-width="72" size="small">
                    <n-select
                      :value="step.config.mode ?? 'move'"
                      :options="paramOptions('organize', 'mode')"
                      size="small" style="width:120px"
                      @update:value="(v) => setStepParam(si, 'mode', v)"
                    />
                  </n-form-item>
                  <n-form-item :label="$t('watch.targetRoot')" label-placement="left" label-width="72" size="small">
                    <n-input
                      :value="step.config.targetRoot ?? ''"
                      :placeholder="$t('watch.targetRootPlaceholder')"
                      size="small" style="width:220px"
                      @update:value="(v) => setStepParam(si, 'targetRoot', v)"
                    />
                  </n-form-item>
                </div>


                <div class="org-levels">
                  <div class="org-levels-header">
                    <span class="org-label">{{ $t('watch.levels') }}</span>
                    <span class="org-count">{{ (step.config.levels || []).length }}</span>
                  </div>
                  <div class="org-levels-list">
                    <div v-for="(lv, li) in (step.config.levels || [])" :key="li" class="org-level-row">
                      <span class="org-level-idx">{{ li + 1 }}</span>
                      <n-select
                        :value="lv.field"
                        :options="orgFieldOptions"
                        size="small"
                        style="flex:1"
                        @update:value="(v) => updateOrgLevel(si, li, v)"
                      />
                      <n-button size="small" text type="error" @click="removeOrgLevel(si, li)">
                        <template #icon><n-icon :size="14"><TrashOutline /></n-icon></template>
                      </n-button>
                    </div>
                  </div>
                  <n-button size="small" dashed @click="addOrgLevel(si)" style="margin-top:4px">
                    <template #icon><n-icon :size="14"><AddCircleOutline /></n-icon></template>
                    {{ $t('watch.addLevel') }}
                  </n-button>


                  <div v-if="(step.config.levels || []).length > 0" class="org-preview">
                    <span class="org-preview-label">{{ $t('watch.preview') }}</span>
                    <code class="org-preview-path">{{ orgPreview(si) }}</code>
                  </div>
                </div>
              </div>


              <div v-else-if="stepParams(si).length > 0" class="step-params">
                <div class="step-params-grid">
                  <n-form-item
                    v-for="pk in stepParams(si)"
                    :key="pk.key"
                    :label="pk.label"
                    label-placement="left"
                    label-width="72"
                    size="small"
                  >
                    <template v-if="pk.options">
                      <n-select
                        :value="step.config[pk.key] ?? ''"
                        :options="pk.options"
                        size="small"
                        style="width:180px"
                        @update:value="(v) => setStepParam(si, pk.key, v)"
                      />
                    </template>
                    <template v-else>
                      <n-input
                        :value="step.config[pk.key] ?? ''"
                        :placeholder="pk.placeholder"
                        size="small"
                        style="width:220px"
                        @update:value="(v) => setStepParam(si, pk.key, v)"
                      />
                    </template>
                  </n-form-item>
                </div>
              </div>
            </div>
          </div>

          <n-button size="small" dashed @click="addStep" style="margin-top: 8px">
            <template #icon><n-icon :size="16"><AddCircleOutline /></n-icon></template>
            {{ $t('watch.addStep') }}
          </n-button>
        </div>

        <template #footer>
          <n-space justify="end">
            <n-button size="small" @click="watchModal = false">{{ $t('watch.cancel') }}</n-button>
            <n-button size="small" type="primary" @click="saveWatchProfile">{{ $t('watch.save') }}</n-button>
          </n-space>
        </template>
      </n-modal>


      <n-modal
        v-model:show="watchExecModal"
        preset="card"
        :title="$t('watch.manualExec', { name: watchExecTarget?.name })"
        style="max-width: 480px"
        :mask-closable="false"
      >
        <n-form-item :label="$t('watch.filePaths')" label-placement="top" size="small">
          <n-input
            v-model:value="watchExecPaths"
            type="textarea"
            :rows="5"
            :placeholder="$t('watch.filePathsPlaceholder')"
          />
        </n-form-item>
        <n-text v-if="watchExecResult" depth="2" style="font-size:12px">
          {{ $t('watch.pipelineId') }}{{ watchExecResult }}
        </n-text>
        <template #footer>
          <n-space justify="end">
            <n-button size="small" @click="watchExecModal = false">{{ $t('watch.close') }}</n-button>
            <n-button size="small" type="primary" :loading="watchExecuting" @click="doExecute">{{ $t('watch.doExecute') }}</n-button>
          </n-space>
        </template>
      </n-modal>
    </div>


    <div v-if="activeTab === 'pipeline'" class="tab-content">
      <n-spin :show="loading">
        <n-grid :cols="2" :x-gap="12">
          <n-grid-item v-for="group in pipelineGroups" :key="group.title">
            <n-card size="small" :title="group.title" class="section-card">
              <n-form-item
                v-for="f in group.fields"
                :key="f.key"
                :label="f.label"
                label-placement="left"
                label-width="140"
                size="small"
              >
                <template v-if="f.type === 'BOOLEAN'">
                  <n-switch
                    :value="getBoolean(f.key, f.fallback === 'true')"
                    size="small"
                    @update:value="(v) => saveField(f.key, String(v))"
                  />
                </template>
                <template v-else>
                  <n-input-number
                    :value="getNumber(f.key)"
                    :min="f.min" :max="f.max" :step="f.step"
                    size="small"
                    @update:value="(v) => saveField(f.key, String(v ?? f.fallback))"
                  />
                </template>
                <span class="unit">{{ f.unit }}</span>
              </n-form-item>
            </n-card>
          </n-grid-item>
        </n-grid>
      </n-spin>
    </div>


    <div v-if="activeTab === 'others'" class="tab-content">
      <div class="config-toolbar">
        <n-input v-model:value="searchQuery" :placeholder="$t('others.searchPlaceholder')" clearable class="search-input">
          <template #prefix><n-icon :size="14"><SearchOutline /></n-icon></template>
        </n-input>
      </div>
      <n-spin :show="loading">
        <n-data-table
          v-if="otherList.length > 0"
          :columns="tableColumns"
          :data="otherList"
          :row-key="(r) => r.configKey"
          size="small"
          class="config-table"
        />
        <n-empty v-else :description="$t('others.noData')" size="small" />
      </n-spin>
    </div>
  </div>
</template>

<script setup>
import { computed, h, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useMessage } from 'naive-ui'
import {
  SearchOutline, RefreshOutline, CloudOutline,
  GitBranchOutline, SettingsOutline, PulseOutline, FlashOutline,
  CheckmarkCircleOutline, CloseCircleOutline, AddCircleOutline,
  ArrowUpOutline, ArrowDownOutline, TrashOutline,
} from '@vicons/ionicons5'
import { NButton, NIcon, NTag, NText, NPopconfirm } from 'naive-ui'
import { fetchConfigs, updateConfig, refreshConfigCache } from '@/api/editor/config.js'
import { fetchAll as fetchCustomAll, update as updateCustom, remove as removeCustomApi, testProvider as testCustomApi } from '@/api/editor/custom-provider.js'
import { fetchProfiles, createProfile, updateProfile, deleteProfile, executeProfile, fetchSteps } from '@/api/editor/watch.js'

const message = useMessage()
const { t } = useI18n()

const activeTab = ref('enrich')
const tabs = computed(() => [
  { key: 'enrich', label: t('configEnrich.tab'), icon: CloudOutline },
  { key: 'watch', label: t('watch.tab'), icon: FlashOutline },
  { key: 'pipeline', label: t('configPipeline.tab'), icon: GitBranchOutline },
  { key: 'others', label: t('others.tab'), icon: SettingsOutline },
])

const rawData = ref([])
const loading = ref(true)
const searchQuery = ref('')
const configModal = ref(false)
const testModal = ref(false)
const editingProvider = ref(null)
const testResult = ref(null)

const watchProfiles = ref([])
const watchStepsMeta = ref([])
const watchLoading = ref(false)
const watchModal = ref(false)
const watchEditingId = ref(null)
const watchDraft = ref(null)

const watchExecModal = ref(false)
const watchExecTarget = ref(null)
const watchExecPaths = ref('')
const watchExecuting = ref(false)
const watchExecResult = ref('')

const stepI18nMap = {
  'encoding-repair': () => t('watch.steps.encodingRepair'),
  'chinese-convert': () => t('watch.steps.chineseConvert'),
  enrich: () => t('watch.steps.enrich'),
  fingerprint: () => t('watch.steps.fingerprint'),
  organize: () => t('watch.steps.organize'),
  'format-convert': () => t('watch.steps.formatConvert'),
}
const stepLabelFallback = {}
function stepLabel(name) { return stepI18nMap[name]?.() || stepLabelFallback[name] || name }

const stepTypeOptions = computed(() =>
  watchStepsMeta.value.map(s => ({
    label: stepI18nMap[s.name]?.() || s.label,
    value: s.name,
  }))
)

function paramLabel(key) {
  const map = {
    direction: t('watch.params.direction'), matchMode: t('watch.params.matchMode'),
    mergeScope: t('watch.params.mergeScope'), provider: t('watch.params.provider'),
    mode: t('watch.params.mode'), pattern: t('watch.params.pattern'),
    fields: t('watch.params.fields'), targetFormat: t('watch.params.targetFormat'),
  }
  return map[key] || key
}

function stepParams(si) {
  const stepName = watchDraft.value?.steps[si]?.name
  if (!stepName) return []
  const meta = watchStepsMeta.value.find(s => s.name === stepName)
  if (!meta || !meta.configKeys) return []
  return Object.entries(meta.configKeys).map(([key, desc]) => ({
    key,
    label: paramLabel(key),
    placeholder: desc,
    options: paramOptions(stepName, key),
  }))
}

function paramOptions(stepName, key) {
  if (stepName === 'chinese-convert' && key === 'direction') {
    return [
      { label: t('watch.params.toSimplified'), value: 'toSimplified' },
      { label: t('watch.params.toTraditional'), value: 'toTraditional' },
    ]
  }
  if (stepName === 'enrich') {
    if (key === 'matchMode') return [
      { label: t('watch.params.loose'), value: 'LOOSE' },
      { label: t('watch.params.standard'), value: 'STANDARD' },
      { label: t('watch.params.strict'), value: 'STRICT' },
    ]
    if (key === 'mergeScope') return [
      { label: t('watch.params.fillOnly'), value: 'FILL_ONLY' },
      { label: t('watch.params.replaceAll'), value: 'REPLACE_ALL' },
    ]
    if (key === 'provider') return [
      { label: 'QQ音乐', value: 'qqmusic' },
      { label: t('watch.providers.kugou'), value: 'kugou' },
      { label: t('watch.providers.kuwo'), value: 'kuwo' },
      { label: t('watch.providers.netease'), value: 'netease' },
      { label: 'iTunes', value: 'itunes' },
      { label: 'MusicBrainz', value: 'musicbrainz' },
      { label: t('watch.providers.migu'), value: 'migu' },
    ]
  }
  if (stepName === 'encoding-repair' && key === 'fields') {
    return [
      { label: t('watch.params.allFields'), value: 'all' },
      { label: t('organize.fields.title'), value: 'title' },
      { label: t('organize.fields.artist'), value: 'artist' },
      { label: t('organize.fields.album'), value: 'album' },
      { label: t('watch.params.albumArtist'), value: 'albumArtist' },
      { label: t('organize.fields.trackNumber'), value: 'track' },
      { label: t('organize.fields.discNumber'), value: 'disc' },
      { label: t('organize.fields.year'), value: 'year' },
      { label: t('organize.fields.style'), value: 'style' },
      { label: t('watch.params.lyric'), value: 'lyric' },
      { label: t('watch.params.comment'), value: 'comment' },
    ]
  }
  if (stepName === 'organize') {
    if (key === 'mode') return [
      { label: t('watch.params.move'), value: 'move' },
      { label: t('watch.params.copy'), value: 'copy' },
    ]
  }
  return null
}


const orgFieldOptions = [
  { label: () => t('organize.fields.artist'), value: 'artist' },
  { label: () => t('organize.fields.album'), value: 'album' },
  { label: () => t('organize.fields.title'), value: 'title' },
  { label: () => t('organize.fields.year'), value: 'year' },
  { label: () => t('organize.fields.format'), value: 'format' },
  { label: () => t('organize.fields.style'), value: 'style' },
  { label: () => t('organize.fields.language'), value: 'language' },
  { label: () => t('organize.fields.trackNumber'), value: 'trackNumber' },
  { label: () => t('organize.fields.discNumber'), value: 'discNumber' },
  { label: () => t('organize.fields.composer'), value: 'composer' },
  { label: () => t('organize.fields.lyricist'), value: 'lyricist' },
  { label: () => t('organize.fields.albumYear'), value: 'albumYear' },
  { label: () => t('organize.fields.company'), value: 'company' },
]

function addOrgLevel(si) {
  const steps = watchDraft.value?.steps
  if (!steps) return
  if (!steps[si].config.levels) steps[si].config.levels = []
  steps[si].config.levels = [...steps[si].config.levels, { field: 'artist' }]
}

function removeOrgLevel(si, li) {
  const steps = watchDraft.value?.steps
  if (!steps) return
  steps[si].config.levels = steps[si].config.levels.filter((_, i) => i !== li)
}

function updateOrgLevel(si, li, field) {
  const steps = watchDraft.value?.steps
  if (!steps) return
  const updated = [...steps[si].config.levels]
  updated[li] = { field }
  steps[si].config.levels = updated
}

function orgPreview(si) {
  const step = watchDraft.value?.steps[si]
  if (!step) return ''
  const root = step.config.targetRoot || t('watch.sourceDir')
  const levels = step.config.levels || []
  if (levels.length === 0) return root + '/...'
  const segs = levels.map(l => {
    const opt = orgFieldOptions.find(o => o.value === l.field)
    return (opt?.label && typeof opt.label === 'function' ? opt.label() : l.field)
  })
  return root + '/' + segs.join('/') + '/' + t('watch.fileName')
}


function openWatchCreate() {
  watchEditingId.value = null
  watchDraft.value = {
    name: '', watchPath: '/', description: '', enabled: true,
    autoTrigger: true, priority: 0, steps: [],
  }
  watchModal.value = true
}

function openWatchEdit(p) {
  watchEditingId.value = p.id
    watchDraft.value = {
    name: p.name,
    watchPath: p.watchPath,
    description: p.description || '',
    enabled: p.enabled,
    autoTrigger: p.autoTrigger,
    priority: p.priority || 0,
    steps: (p.steps || []).map(s => ({ name: s.name, config: { ...(s.config || {}) } })),
  }
  watchModal.value = true
}

async function saveWatchProfile() {
  const d = watchDraft.value
  if (!d || !d.name.trim()) { message.warning(t('watch.nameRequired')); return }
  try {
    if (watchEditingId.value) {
      await updateProfile(watchEditingId.value, d)
      message.success(t('watch.updated'))
    } else {
      await createProfile(d)
      message.success(t('watch.created'))
    }
    watchModal.value = false
    await loadWatchProfiles()
  } catch (e) { message.error(t('watch.saveFailed')) }
}

async function removeWatchProfile(id) {
  try {
    await deleteProfile(id)
    message.success(t('watch.deleted'))
    await loadWatchProfiles()
  } catch (e) { message.error(t('watch.deleteFailed')) }
}

async function toggleWatchEnabled(p, v) {
  try {
    await updateProfile(p.id, { ...p, enabled: v, steps: p.steps })
    p.enabled = v
  } catch (e) { message.error(t('watch.toggleFailed')) }
}

async function toggleWatchAuto(p, v) {
  try {
    await updateProfile(p.id, { ...p, autoTrigger: v, steps: p.steps })
    p.autoTrigger = v
  } catch (e) { message.error(t('watch.toggleFailed')) }
}


function addStep() {
  if (!watchDraft.value) return
  const def = watchStepsMeta.value[0]
  watchDraft.value.steps.push({ name: def?.name || '', config: {} })
}

function removeStep(si) {
  watchDraft.value?.steps.splice(si, 1)
}

function moveStep(si, dir) {
  const steps = watchDraft.value?.steps
  if (!steps) return
  const target = si + dir
  if (target < 0 || target >= steps.length) return
  const tmp = steps[si]
  steps[si] = steps[target]
  steps[target] = tmp
    watchDraft.value = { ...watchDraft.value, steps: [...steps] }
}

function onStepTypeChange(si, newName) {
  const steps = watchDraft.value?.steps
  if (!steps) return
  steps[si].name = newName
  steps[si].config = {}
    if (newName === 'organize') {
    steps[si].config = { mode: 'move', targetRoot: '', levels: [] }
  }
}

function setStepParam(si, key, value) {
  const steps = watchDraft.value?.steps
  if (!steps) return
  if (!steps[si].config) steps[si].config = {}
  steps[si].config[key] = value
}


function openWatchExecute(p) {
  watchExecTarget.value = p
  watchExecPaths.value = ''
  watchExecResult.value = ''
  watchExecModal.value = true
}

async function doExecute() {
  const paths = watchExecPaths.value.split('\n').map(s => s.trim()).filter(Boolean)
  if (paths.length === 0) { message.warning(t('watch.pathsRequired')); return }
  watchExecuting.value = true
  try {
    const res = await executeProfile(watchExecTarget.value.id, paths)
    watchExecResult.value = res.pipelineId || `(${t('watch.noData')})`
    message.success(t('watch.submitted'))
  } catch (e) { message.error(t('watch.execFailed')) }
  finally { watchExecuting.value = false }
}


async function loadWatchProfiles() {
  watchLoading.value = true
  try { watchProfiles.value = (await fetchProfiles()) || [] } catch (e) {  }
  finally { watchLoading.value = false }
}

const configMap = computed(() => {
  const m = {}
  for (const c of rawData.value) m[c.configKey] = c.configValue
  return m
})

function getString(key, fallback = '') { return configMap.value[key] ?? fallback }
function getNumber(key) { const v = configMap.value[key]; return (v == null || v === '') ? null : parseFloat(v) }
function getBoolean(key, fb = false) { const v = configMap.value[key]; return v == null ? fb : v === 'true' }

const providerFields = computed(() => [
  { key: 'rate_limit_ms', label: t('configEnrich.fields.rateLimitMs'), min: 50, max: 10000, step: 10, fallback: 240 },
  { key: 'max_concurrent', label: t('configEnrich.fields.maxConcurrent'), min: 1, max: 50, step: 1, fallback: 6 },
  { key: 'timeout_seconds', label: t('configEnrich.fields.timeoutSeconds'), min: 3, max: 60, step: 1, fallback: 15 },
])

const providerDefs = computed(() => {
  const T = (code) => t(`configEnrich.providers.${code}`)
  const tag = (k) => t(`configEnrich.tags.${k}`)
  const url = (k) => t(`configEnrich.urls.${k}`)
  return {
    qqmusic: {
      label: T('qqmusic.label'), icon: '🐧', color: '#10b981', version: '1.0.0',
      description: T('qqmusic.desc'),
      tags: [tag('title'), tag('artist'), tag('album'), tag('lyric'), tag('cover'), tag('year'), tag('track'), tag('disc')],
      urls: [{ key: 'search_url', label: url('search') }, { key: 'album_url', label: url('album') }, { key: 'lyric_url', label: url('lyric') }],
    },
    netease: {
      label: T('netease.label'), icon: '☁️', color: '#ef4444', version: '1.0.0',
      description: T('netease.desc'),
      tags: [tag('title'), tag('artist'), tag('album'), tag('lyric'), tag('cover'), tag('year'), tag('track'), tag('disc')],
      urls: [{ key: 'search_url', label: url('search') }, { key: 'detail_url', label: url('detail') }, { key: 'lyric_url', label: url('lyric') }],
    },
    kugou: {
      label: T('kugou.label'), icon: '🎵', color: '#3b82f6', version: '1.0.0',
      description: T('kugou.desc'),
      tags: [tag('title'), tag('artist'), tag('album'), tag('lyric'), tag('cover'), tag('year'), tag('track'), tag('disc')],
      urls: [
        { key: 'search_url', label: url('search') }, { key: 'detail_url', label: url('detail') },
        { key: 'lyric_search_url', label: url('lyricSearch') }, { key: 'lyric_download_url', label: url('lyricDownload') },
      ],
    },
    kuwo: {
      label: T('kuwo.label'), icon: '🎶', color: '#f59e0b', version: '1.0.0',
      description: T('kuwo.desc'),
      tags: [tag('title'), tag('artist'), tag('album'), tag('lyric'), tag('cover'), tag('year'), tag('track'), tag('disc')],
      urls: [{ key: 'search_url', label: url('search') }, { key: 'lyric_url', label: url('lyric') }],
    },
    migu: {
      label: T('migu.label'), icon: '📻', color: '#ec4899', version: '1.0.0',
      description: T('migu.desc'),
      tags: [tag('title'), tag('artist'), tag('album'), tag('lyric'), tag('cover'), tag('year'), tag('track'), tag('disc')],
      urls: [{ key: 'search_url', label: url('search') }, { key: 'song_url', label: url('song') }],
    },
    itunes: {
      label: T('itunes.label'), icon: '🍎', color: '#a855f7', version: '1.0.0',
      description: T('itunes.desc'),
      tags: [tag('title'), tag('artist'), tag('album'), tag('cover'), tag('year'), tag('track'), tag('disc')],
      urls: [{ key: 'search_url', label: url('search') }, { key: 'lookup_url', label: url('lookup') }],
    },
    musicbrainz: {
      label: T('musicbrainz.label'), icon: '🧠', color: '#6366f1', version: '1.0.0',
      description: T('musicbrainz.desc'),
      tags: [tag('title'), tag('artist'), tag('album'), tag('cover'), tag('year'), tag('track'), tag('disc')],
      urls: [{ key: 'search_url', label: url('search') }, { key: 'release_url', label: url('release') }],
    },
  }
})

const providerList = computed(() => {
  const list = []
  const defaultProvider = getString('enrich.default_provider', '')
  const enabledSet = new Set(defaultProvider.split(',').map((s) => s.trim()).filter(Boolean))

  for (const [code, def] of Object.entries(providerDefs.value)) {
    list.push({
      ...def,
      code,
      enabled: enabledSet.has(code),
      metadataTags: def.tags,
      testing: false,
    })
  }
  return list
})

const pipelineGroups = computed(() => [
  {
    title: t('configPipeline.scheduler'),
    fields: [
      { key: 'pipeline.executor.scheduler.max_pipelines', label: t('configPipeline.maxPipelines'), type: 'INT', min: 1, max: 16, fallback: 1, unit: '' },
    ],
  },
  {
    title: t('configPipeline.worker'),
    fields: [
      { key: 'pipeline.executor.worker.core_size', label: t('configPipeline.coreSize'), type: 'INT', min: 1, max: 128, fallback: 4, unit: '' },
      { key: 'pipeline.executor.worker.max_size', label: t('configPipeline.maxSize'), type: 'INT', min: 1, max: 128, fallback: 8, unit: '' },
      { key: 'pipeline.executor.worker.queue_capacity', label: t('configPipeline.queueCapacity'), type: 'INT', min: 100, max: 10000, fallback: 2000, unit: '' },
    ],
  },
  {
    title: t('configPipeline.virtual'),
    fields: [
      { key: 'pipeline.executor.virtual.max_concurrent', label: t('configPipeline.maxConcurrent'), type: 'INT', min: 0, max: 512, fallback: 0, unit: '' },
    ],
  },
  {
    title: t('configPipeline.persistence'),
    fields: [
      { key: 'pipeline.recovery.enabled', label: t('configPipeline.recoveryEnabled'), type: 'BOOLEAN', fallback: 'true', unit: '' },
      { key: 'pipeline.task.retention_hours', label: t('configPipeline.retentionHours'), type: 'INT', min: 1, max: 8760, fallback: 720, unit: t('configPipeline.hours') },
    ],
  },
  {
    title: t('configPipeline.batch'),
    fields: [
      { key: 'pipeline.persistence.item_log_flush_size', label: t('configPipeline.flushSize'), type: 'INT', min: 50, max: 1000, fallback: 200, unit: t('configPipeline.items') },
      { key: 'scanner.streaming_threshold', label: t('configPipeline.streamingThreshold'), type: 'INT', min: 100, max: 10000, fallback: 2000, unit: t('configPipeline.files') },
      { key: 'writer.batch_size', label: t('configPipeline.writeBatch'), type: 'INT', min: 50, max: 1000, fallback: 200, unit: '' },
      { key: 'writer.fork_batch', label: t('configPipeline.forkBatch'), type: 'INT', min: 10, max: 200, fallback: 50, unit: '' },
      { key: 'delete.batch_size', label: t('configPipeline.deleteBatch'), type: 'INT', min: 50, max: 500, fallback: 200, unit: '' },
    ],
  },
])

const otherList = computed(() => {
  const exclude = ['enrich.', 'pipeline.', 'scanner.', 'writer.', 'delete.', 'switches.']
  let list = rawData.value.filter((c) => !exclude.some((p) => c.configKey.startsWith(p)))
  const kw = searchQuery.value.trim().toLowerCase()
  if (kw) list = list.filter((r) => r.configKey.toLowerCase().includes(kw) || (r.label && r.label.toLowerCase().includes(kw)))
  return list
})

const tableColumns = computed(() => [
  { title: t('others.key'), key: 'configKey', width: 220, ellipsis: { tooltip: true }, render: (r) => h(NText, { depth: 2, class: 'mono' }, { default: () => r.configKey }) },
  { title: t('others.label'), key: 'label', width: 150, ellipsis: { tooltip: true }, render: (r) => h(NText, { depth: 2 }, { default: () => r.label || '-' }) },
  {
    title: t('others.value'), key: 'configValue', ellipsis: { tooltip: true },
    render: (r) => {
      if (r.isSensitive) return h(NText, { depth: 3, italic: true }, { default: () => '****' })
      const v = r.configValue?.length > 40 ? r.configValue.slice(0, 40) + '…' : r.configValue
      return h(NText, { depth: 1 }, { default: () => v || '-' })
    },
  },
  { title: t('others.type'), key: 'valueType', width: 70, render: (r) => h(NTag, { size: 'tiny', bordered: false }, { default: () => r.valueType || 'STRING' }) },
  {
    title: '', key: '_a', width: 50,
    render: (r) => h(NButton, {
      size: 'tiny', text: true, type: 'primary',
      onClick: () => { const v = r.isSensitive ? '' : (r.configValue || ''); const nv = prompt(t('others.modifyPrompt', { key: r.configKey }), v); if (nv !== null && nv !== v) saveField(r.configKey, nv) },
    }, { default: () => t('others.edit') }),
  },
])


async function saveField(key, value) {
  try {
    await updateConfig(key, value)
    const ex = rawData.value.find((c) => c.configKey === key)
    if (ex) ex.configValue = value
  } catch (e) { message.error(t('configEnrich.saveFailed', { key })) }
}

async function toggleProvider(code, enabled) {
  const current = getString('enrich.default_provider', '')
  const providers = current.split(',').map((s) => s.trim()).filter(Boolean)
  const np = enabled ? [...new Set([...providers, code])] : providers.filter((p) => p !== code)
  await saveField('enrich.default_provider', np.join(','))
}

function openConfig(p) {
  editingProvider.value = p
  configModal.value = true
}

async function testProvider(p) {
  const provider = providerList.value.find((x) => x.code === p.code)
  if (provider) provider.testing = true
    testResult.value = { label: p.label, success: null, message: t('configEnrich.testing'), detail: t('configEnrich.testHint'), latencyMs: 0 }
  testModal.value = true
  try {
    const res = await fetch(`/api/config/provider-test?provider=${encodeURIComponent(p.code)}`)
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const data = await res.json()
    testResult.value = { label: p.label, ...data }
  } catch (e) {
    testResult.value = { label: p.label, success: false, message: t('configEnrich.testFailed'), detail: e.message }
  } finally {
    if (provider) provider.testing = false
  }
}

async function copyDefaults(code) {
  for (const k of ['rate_limit_ms', 'max_concurrent', 'timeout_seconds', 'rate_limit_retries', 'rate_limit_backoff_ms']) {
    const dv = getString('enrich.default.' + k, '')
    if (dv) await saveField('enrich.' + code + '.' + k, dv)
  }
  message.success(t('configEnrich.restoreSuccess', { label: providerDefs.value[code].label }))
}

async function refreshCache() {
  try { await refreshConfigCache(); await load(); message.success(t('configEnrich.cacheRefreshed')) } catch (e) { message.error(t('configEnrich.refreshFailed')) }
}

const customProviders = ref([])

async function loadCustom() {
  try { customProviders.value = (await fetchCustomAll()) || [] } catch (e) {  }
}

async function toggleCustom(p, enabled) {
  try { await updateCustom(p.id, { ...p, enabled, configJson: p.configJson || '{}', sourceCode: p.sourceCode || '' }) } catch (e) { message.error(t('configEnrich.toggleFailed')) }
  await loadCustom()
}

async function removeCustom(p) {
  try {
    await removeCustomApi(p.id)
    message.success(t('configEnrich.deleteSuccess', { label: p.label }))
    await loadCustom()
  } catch (e) { message.error(t('configEnrich.deleteFailed')) }
}

async function testCustom(p) {
  p._testing = true
  try {
    const res = await testCustomApi(p.id)
    testResult.value = { label: p.label, ...res }
    testModal.value = true
  } catch (e) {
    testResult.value = { label: p.label, success: false, message: t('configEnrich.testFailed'), detail: e.message }
    testModal.value = true
  } finally { p._testing = false }
}

async function load() {
  try { rawData.value = (await fetchConfigs()) || [] } catch (e) { console.error('[ConfigManager]', e) } finally { loading.value = false }
}

onMounted(() => {
  load()
  loadCustom()
  loadWatchProfiles()
  fetchSteps().then(steps => {
    watchStepsMeta.value = steps || []
    for (const s of steps || []) stepLabelFallback[s.name] = s.label
  }).catch(() => {})
})
</script>

<style scoped>
.config-page { height: 100%; display: flex; flex-direction: column; overflow: hidden; }


.tab-bar { display: flex; align-items: center; gap: 4px; padding: 8px 16px; border-bottom: var(--border-width-default) solid var(--color-border); flex-shrink: 0; }
.tab-actions { margin-left: auto; display: flex; gap: 4px; }


.tab-content { flex: 1; overflow-y: auto; padding: 16px; }


.provider-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }

.provider-card {
  background: var(--color-surface); border: var(--border-width-default) solid var(--color-border);
  border-radius: 8px; padding: 16px; display: flex; flex-direction: column; gap: 10px;
  transition: opacity 0.2s, box-shadow 0.2s;
}
.provider-card:hover { box-shadow: 0 2px 8px rgba(0,0,0,.08); }
.provider-card.provider-disabled { opacity: 0.45; }


.card-head { display: flex; align-items: flex-start; justify-content: space-between; }
.card-title { display: flex; align-items: center; gap: 10px; }
.provider-icon { width: 32px; height: 32px; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-size: 16px; flex-shrink: 0; }
.card-name-row { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.provider-name { font-weight: 600; font-size: 14px; }
.provider-version { font-size: 11px; color: var(--color-text-tertiary); }


.card-desc { margin: 0; font-size: 12px; color: var(--color-text-secondary); line-height: 1.55; }


.card-tags { display: flex; flex-wrap: wrap; gap: 4px; }


.card-actions { display: flex; gap: 8px; margin-top: 2px; }


.custom-provider { border-style: dashed; }
.add-card { cursor: pointer; border-style: dashed; border-color: #ccc; display: flex; align-items: center; justify-content: center; min-height: 140px; transition: border-color 0.2s; }
.add-card:hover { border-color: var(--color-primary); }
.add-content { display: flex; flex-direction: column; align-items: center; gap: 8px; }
.add-text { font-size: 13px; color: #aaa; }


.test-result { text-align: center; }
.test-status { margin-bottom: 12px; }
.test-msg { font-size: 15px; font-weight: 600; margin: 0 0 4px; }
.test-detail { font-size: 12px; color: var(--color-text-tertiary); margin: 0; }


.section-card { margin-bottom: 12px; }
.unit { margin-left: 6px; font-size: 11px; color: var(--color-text-tertiary); }


.config-toolbar { display: flex; align-items: center; margin-bottom: 12px; }
.search-input { flex: 1; max-width: 320px; }
.config-table { flex: 1; }


.watch-path { margin: 2px 0; }
.watch-steps-row { display: flex; flex-wrap: wrap; gap: 4px; margin: 4px 0; }


.step-list { display: flex; flex-direction: column; gap: 10px; }
.step-item {
  background: var(--gradient-card, var(--color-surface));
  border-radius: var(--radius-lg);
  border: var(--border-width-default) solid var(--color-border);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}


.step-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: var(--color-surface-hover, #fafafa);
}
.step-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px; height: 22px;
  border-radius: var(--radius-full);
  background: var(--color-accent);
  color: var(--color-on-primary, #fff);
  font-size: var(--text-xs);
  font-weight: 600;
  flex-shrink: 0;
}


.step-params {
  padding: 10px 12px 12px;
  border-top: var(--border-width-default) solid var(--color-border-light, var(--color-border));
}
.step-params-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 16px;
}
.step-params-grid .n-form-item {
  margin-bottom: 0;
}


.org-levels { margin-top: 8px; }
.org-levels-header { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
.org-label { font-size: var(--text-sm); font-weight: 600; color: var(--color-text-secondary); }
.org-count { font-size: var(--text-2xs); background: var(--color-accent); color: var(--color-on-primary, #fff); border-radius: var(--radius-full); padding: 0 6px; line-height: 17px; font-weight: 600; }
.org-levels-list { display: flex; flex-direction: column; gap: 4px; }
.org-level-row { display: flex; align-items: center; gap: 6px; }
.org-level-idx { font-size: var(--text-sm); color: var(--color-text-tertiary); min-width: 18px; text-align: right; }
.org-preview {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  padding: 6px 10px;
  border-radius: var(--radius-md);
  background: var(--color-bg-secondary, #f5f5f5);
}
.org-preview-label { font-size: var(--text-xs); color: var(--color-text-tertiary); flex-shrink: 0; }
.org-preview-path { font-family: var(--font-family-mono); font-size: var(--text-xs); color: var(--color-accent); word-break: break-all; }
:deep(.mono) { font-family: var(--font-family-mono); font-size: 12px; }
:deep(.n-form-item) { margin-bottom: 6px; }
</style>
