<template>
  <div class="config-page">
    <!-- ════════════════ Tab 导航 — 粘土 Pill 风格 ════════════════ -->
    <div class="tab-bar">
      <div class="tab-links">
        <a
          v-for="tab in tabs"
          :key="tab.key"
          class="tab-link"
          :class="{ active: activeTab === tab.key }"
          @click="onTabClick(tab.key)"
        >
          <n-icon :size="13"><component :is="tab.icon" /></n-icon>
          {{ tab.label }}
        </a>
      </div>
      <div class="tab-actions">
        <a class="tab-link" @click="refreshCache">
          <n-icon :size="13"><RefreshOutline /></n-icon>
          {{ $t('config.refreshCache') }}
        </a>
      </div>
    </div>

    <!-- ════════════════ 标签源 Tab ════════════════ -->
    <div v-if="activeTab === 'enrich'" class="tab-content">
      <n-spin :show="loading">
        <!-- 实体类型切换 -->
        <div class="entity-tabs">
          <a
            v-for="et in entityTypes"
            :key="et.key"
            class="entity-tab"
            :class="{ active: activeEntity === et.key }"
            @click="activeEntity = et.key"
          >{{ et.label }}</a>
        </div>
        <div class="section-header">
          <span class="section-bar" />
          <span class="section-label">{{ $t('configEnrich.builtinSection') }}</span>
          <span class="section-count">{{ providerList.filter(p => p.enabled).length }}/{{ providerList.length }}</span>
        </div>
        <div class="provider-grid">
          <div
            v-for="p in providerList"
            :key="p.code"
            class="provider-card"
            :class="{ 'provider-disabled': !p.enabled }"
          >
            <!-- 卡片头部 -->
            <div class="card-head">
              <div class="card-title">
                <span class="provider-icon" :style="{ background: p.color }">
                  <n-icon :size="18" color="#fff"><component :is="p.icon" /></n-icon>
                </span>
                <div class="card-name-row">
                  <span class="provider-name">{{ p.label }}</span>
                  <span class="provider-version">v{{ p.version }}</span>
                  <span class="status-dot" :class="p.enabled ? 'online' : 'offline'" />
                  <span style="font-size:11px;color:var(--color-text-tertiary)">{{ p.enabled ? $t('configEnrich.online') : $t('configEnrich.offline') }}</span>
                </div>
              </div>
              <n-switch
                :value="p.enabled"
                size="small"
                @update:value="(v) => toggleProvider(p.code, v)"
              />
            </div>

            <!-- 描述 -->
            <p class="card-desc">{{ p.description }}</p>

            <!-- 元数据能力标签 -->
            <div class="card-tags">
              <n-tag
                v-for="t in p.metadataTags"
                :key="t.key"
                size="tiny"
                :bordered="false"
                type="info"
              >{{ t.label }}</n-tag>
            </div>

            <!-- 操作按钮 -->
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
          <!-- 自定义标签源 -->
          <div v-if="customProviders.length > 0" class="section-header custom-section">
            <span class="section-bar" />
            <span class="section-label">{{ $t('configEnrich.customSection') }}</span>
            <span class="section-count">{{ customProviders.filter(p => p.enabled).length }}/{{ customProviders.length }}</span>
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

          <!-- 添加按钮 -->
          <div class="provider-card add-card" @click="$router.push('/custom-providers/new')">
            <div class="add-content">
              <n-icon :size="28" color="#aaa"><AddCircleOutline /></n-icon>
              <span class="add-text">{{ $t('configEnrich.addCustom') }}</span>
            </div>
          </div>
        </div>
      </n-spin>
    </div>

    <!-- 配置弹窗（内置源） -->
    <n-modal
      v-model:show="configModal"
      preset="card"
      :title="$t('configEnrich.configTitle', { label: editingProvider?.label })"
      style="max-width: 560px"
      :mask-closable="false"
    >
      <n-tabs v-if="editingProvider" type="segment" size="small">
        <!-- 限流 -->
        <n-tab-pane name="rate" :tab="$t('configEnrich.rateLimit')">
          <n-grid :cols="3" :x-gap="12" :y-gap="12">
            <n-grid-item v-for="f in providerFields" :key="f.key">
              <n-form-item :label="f.label" label-placement="top" size="small">
                <n-input-number
                  :value="getNumber(`${enrichPrefix}.${editingProvider.code}.${f.key}`)"
                  :min="f.min" :max="f.max" :step="f.step"
                  size="small"
                  @update:value="(v) => saveField(`${enrichPrefix}.${editingProvider.code}.${f.key}`, String(v ?? f.fallback))"
                />
              </n-form-item>
            </n-grid-item>
            <n-grid-item>
              <n-form-item :label="$t('configEnrich.retries')" label-placement="top" size="small">
                <n-input-number
                  :value="getNumber(`${enrichPrefix}.${editingProvider.code}.rate_limit_retries`)"
                  :min="0" :max="10" size="small"
                  @update:value="(v) => saveField(`${enrichPrefix}.${editingProvider.code}.rate_limit_retries`, String(v ?? 3))"
                />
              </n-form-item>
            </n-grid-item>
            <n-grid-item>
              <n-form-item :label="$t('configEnrich.backoffMs')" label-placement="top" size="small">
                <n-input-number
                  :value="getNumber(`${enrichPrefix}.${editingProvider.code}.rate_limit_backoff_ms`)"
                  :min="1000" :max="120000" :step="1000" size="small"
                  @update:value="(v) => saveField(`${enrichPrefix}.${editingProvider.code}.rate_limit_backoff_ms`, String(v ?? 30000))"
                />
              </n-form-item>
            </n-grid-item>
          </n-grid>
        </n-tab-pane>

        <!-- API 地址 -->
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
              :value="getString(`${enrichPrefix}.${editingProvider.code}.${u.key}`, '')"
              size="small"
              @update:value="(v) => saveField(`${enrichPrefix}.${editingProvider.code}.${u.key}`, v)"
            />
          </n-form-item>
        </n-tab-pane>

        <!-- 元数据标签 -->
        <n-tab-pane name="tags" :tab="$t('configEnrich.metadataTags')">
          <div class="metadata-tags-grid">
            <div
              v-for="tag in editingProvider.metadataTags"
              :key="tag.key"
              class="metadata-tag-chip"
              :class="{ 'tag-off': !metadataTagEnabled(editingProvider.code, tag.key) }"
              @click="toggleMetadataTag(editingProvider.code, tag.key)"
            >
              <n-icon :size="14">
                <CheckmarkCircleOutline v-if="metadataTagEnabled(editingProvider.code, tag.key)" />
                <CloseCircleOutline v-else />
              </n-icon>
              <span>{{ tag.label }}</span>
            </div>
          </div>
          <n-text depth="3" style="font-size:11px;margin-top:12px;display:block">
            {{ $t('configEnrich.tagsHint') }}
          </n-text>
        </n-tab-pane>
      </n-tabs>

      <template #footer>
        <n-space justify="end">
          <n-button size="small" @click="copyDefaults(editingProvider.code)">{{ $t('configEnrich.restoreDefaults') }}</n-button>
          <n-button size="small" type="primary" @click="configModal = false">{{ $t('configEnrich.done') }}</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 测试结果弹窗 -->
    <n-modal
      v-model:show="testModal"
      preset="card"
      :title="$t('configEnrich.testTitle', { label: testResult?.label })"
      style="max-width: 480px"
    >
      <div v-if="testResult" class="test-result">
        <div class="test-status">
          <n-spin v-if="testResult.success === null" :size="40" />
          <div v-else-if="testResult.success" class="test-icon-wrap success">
            <n-icon :size="28" color="var(--color-success)"><CheckmarkCircleOutline /></n-icon>
          </div>
          <div v-else class="test-icon-wrap fail">
            <n-icon :size="28" color="var(--color-destructive)"><CloseCircleOutline /></n-icon>
          </div>
        </div>
        <p class="test-msg">{{ testResult.message }}</p>
        <p v-if="testResult.latencyMs" class="test-detail">{{ $t('configEnrich.latency') }} {{ testResult.latencyMs }}{{ $t('configEnrich.ms') }}</p>
        <p v-if="testResult.detail" class="test-detail">{{ testResult.detail }}</p>
      </div>
      <template #footer>
        <n-button size="small" @click="testModal = false">{{ $t('watch.close') }}</n-button>
      </template>
    </n-modal>

    <!-- ════════════════ 自动任务 Tab ════════════════ -->
    <div v-if="activeTab === 'watch'" class="tab-content">
      <n-spin :show="watchLoading">
        <div class="provider-grid">
          <!-- Profile 卡片 (v2: 含扫描配置 + 状态) -->
          <div
            v-for="p in watchProfiles"
            :key="p.id"
            class="provider-card"
            :class="{ 'provider-disabled': !p.enabled }"
          >
            <div class="card-head">
              <div class="card-title">
                <span class="provider-icon" style="background:var(--color-accent)">⚡</span>
                <div class="card-name-row">
                  <span class="provider-name">{{ p.name }}</span>
                  <n-tag :type="p.enabled ? 'success' : 'default'" size="tiny" :bordered="false">
                    {{ p.enabled ? $t('watch.enabled') : $t('watch.disabled') }}
                  </n-tag>
                  <n-tag :type="watchStateType(p.state)" size="tiny" :bordered="false">
                    {{ watchStateLabel(p.state) }}
                  </n-tag>
                </div>
              </div>
              <div class="card-head-actions">
                <n-button v-if="!watchPaused[p.id]" size="tiny" quaternary @click="togglePause(p)">
                  <template #icon><n-icon :size="12"><PauseOutline /></n-icon></template>
                </n-button>
                <n-button v-else size="tiny" quaternary type="warning" @click="toggleResume(p)">
                  <template #icon><n-icon :size="12"><PlayOutline /></n-icon></template>
                </n-button>
                <n-switch :value="p.enabled" size="small" @update:value="(v) => toggleWatchEnabled(p, v)" />
              </div>
            </div>

            <!-- 路径 -->
            <p class="watch-path">
              <n-text depth="2" style="font-size:12px">📁 {{ p.watchPath === '/' ? $t('watch.rootDir') : p.watchPath }}</n-text>
            </p>

            <!-- 扫描配置 -->
            <div class="watch-scan-config">
              <div class="scan-row">
                <span class="scan-label">DIR_SCAN</span>
                <n-tag :type="p.dirScanEnabled ? 'success' : 'default'" size="tiny" :bordered="false">
                  {{ p.dirScanEnabled ? p.dirScanIntervalSec + 's' : $t('watch.off') }}
                </n-tag>
                <span class="scan-label">FILE_SCAN</span>
                <n-tag :type="p.fileScanEnabled ? 'info' : 'default'" size="tiny" :bordered="false">
                  {{ p.fileScanEnabled ? p.fileScanIntervalSec + 's' : $t('watch.off') }}
                </n-tag>
              </div>
            </div>

            <!-- 步骤链 Tag 展示 -->
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

            <!-- 运行信息 -->
            <div class="watch-run-info" v-if="p.lastScanAt || p.lastRunAt">
              <n-text depth="3" style="font-size:10px">
                <span v-if="p.lastScanAt">{{ $t('watch.lastScan') }}: {{ fmtTime(p.lastScanAt) }}</span>
                <span v-if="p.lastRunAt" style="margin-left:8px">{{ $t('watch.lastRun') }}: {{ fmtTime(p.lastRunAt) }}</span>
              </n-text>
            </div>

            <!-- 操作 -->
            <div class="card-actions">
              <n-button size="tiny" quaternary @click="scanNow(p)">
                <template #icon><n-icon :size="14"><SearchOutline /></n-icon></template>
                {{ $t('watch.scanNow') }}
              </n-button>
              <n-button size="tiny" quaternary @click="openWatchExecute(p)">
                <template #icon><n-icon :size="14"><PulseOutline /></n-icon></template>
                {{ $t('watch.execute') }}
              </n-button>
              <n-button size="tiny" quaternary @click="openWatchEdit(p)">
                <template #icon><n-icon :size="14"><SettingsOutline /></n-icon></template>
                {{ $t('watch.edit') }}
              </n-button>
              <n-popconfirm @positive-click="removeWatchProfile(p.id)">
                <template #trigger>
                  <n-button size="tiny" quaternary type="error">{{ $t('watch.delete') }}</n-button>
                </template>
                {{ $t('watch.deleteConfirm', { name: p.name }) }}
              </n-popconfirm>
            </div>
          </div>

          <!-- 添加卡片 -->
          <div class="provider-card add-card" @click="openWatchCreate()">
            <div class="add-content">
              <n-icon :size="28" color="#aaa"><AddCircleOutline /></n-icon>
              <span class="add-text">{{ $t('watch.newTask') }}</span>
            </div>
          </div>
        </div>
      </n-spin>

      <!-- 创建/编辑弹窗 -->
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

          <!-- v2: 扫描配置 -->
          <n-divider style="margin: 12px 0 8px">{{ $t('watch.scanConfig') }}</n-divider>
          <n-grid :cols="2" :x-gap="12">
            <n-grid-item>
              <n-form-item :label="$t('watch.dirScan')" label-placement="left" label-width="80" size="small">
                <n-switch v-model:value="watchDraft.dirScanEnabled" size="small" />
              </n-form-item>
            </n-grid-item>
            <n-grid-item>
              <n-form-item :label="$t('watch.intervalSec')" label-placement="left" label-width="80" size="small">
                <n-input-number v-model:value="watchDraft.dirScanIntervalSec" :min="5" :max="86400" :step="5" size="small" />
              </n-form-item>
            </n-grid-item>
            <n-grid-item>
              <n-form-item :label="$t('watch.fileScan')" label-placement="left" label-width="80" size="small">
                <n-switch v-model:value="watchDraft.fileScanEnabled" size="small" />
              </n-form-item>
            </n-grid-item>
            <n-grid-item>
              <n-form-item :label="$t('watch.intervalSec')" label-placement="left" label-width="80" size="small">
                <n-input-number v-model:value="watchDraft.fileScanIntervalSec" :min="60" :max="86400" :step="60" size="small" />
              </n-form-item>
            </n-grid-item>
          </n-grid>

          <!-- 数据来源选择（仅 artist/album 数据驱动任务） -->
          <template v-if="taskCategory !== 'song' && selectionSchema">
            <n-divider style="margin: 12px 0 8px">{{ selectionSchema.label }}</n-divider>
            <n-text depth="3" style="font-size:12px">{{ selectionSchema.description }}</n-text>
            <div style="margin-top:8px">
              <ConfigFormRenderer
                :schema="selectionSchema.fields"
                :model-value="selectionConfig"
                @update:model-value="v => selectionConfig = v"
              />
            </div>
          </template>

          <!-- 步骤链编辑器 -->
          <n-divider style="margin: 12px 0 8px">{{ $t('watch.processingSteps') }}</n-divider>
          <div class="step-list">
            <div
              v-for="(step, si) in watchDraft.steps"
              :key="si"
              class="step-item"
            >
              <!-- 步骤头部 -->
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

              <!-- 步骤配置表单（动态，由后端 configSchema 驱动） -->
              <div v-if="getStepSchema(step.name)" class="step-params">
                <ConfigFormRenderer
                  :schema="getStepSchema(step.name)"
                  :model-value="step.config"
                  @update:model-value="(v) => setStepConfig(si, v)"
                />
              </div>
              <div v-else class="step-params">
                <n-text depth="3" style="font-size:12px">{{ $t('watch.noSteps') }}</n-text>
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

      <!-- 手动执行弹窗 -->
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

    <!-- ════════════════ 管道 Tab ════════════════ -->
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

    <!-- ════════════════ 基础配置 Tab ════════════════ -->
    <div v-if="activeTab === 'basic'" class="tab-content">
      <n-spin :show="loading">
        <div class="basic-config-grid">
          <!-- 全局开关 -->
          <div class="basic-section">
            <div class="section-header">
              <span class="section-bar" />
              <span class="section-label">{{ $t('configBasic.globalSwitches') }}</span>
            </div>
            <div class="basic-card">
              <div class="basic-row">
                <div class="basic-info">
                  <span class="basic-label">{{ $t('configBasic.persistEnabled') }}</span>
                  <span class="basic-desc">{{ $t('configBasic.persistEnabledDesc') }}</span>
                </div>
                <n-switch
                  :value="getBoolean('switches.persist_enabled', true)"
                  size="small"
                  @update:value="(v) => saveField('switches.persist_enabled', String(v))"
                />
              </div>
              <div class="basic-row">
                <div class="basic-info">
                  <span class="basic-label">{{ $t('configBasic.writeTagsEnabled') }}</span>
                  <span class="basic-desc">{{ $t('configBasic.writeTagsEnabledDesc') }}</span>
                </div>
                <n-switch
                  :value="getBoolean('switches.write_tags_enabled', true)"
                  size="small"
                  @update:value="(v) => saveField('switches.write_tags_enabled', String(v))"
                />
              </div>
            </div>
          </div>

          <!-- 艺术家设置 -->
          <div class="basic-section">
            <div class="section-header">
              <span class="section-bar" />
              <span class="section-label">{{ $t('configBasic.artistSettings') }}</span>
              <a class="restore-link" @click="restoreArtistDefaults">{{ $t('configBasic.restoreDefaults') }}</a>
            </div>
            <div class="basic-card">
              <!-- 分隔符编辑器 -->
              <div class="basic-row basic-row-top">
                <div class="basic-info">
                  <span class="basic-label">{{ $t('configBasic.splitSeparators') }}</span>
                  <span class="basic-desc">{{ $t('configBasic.splitSeparatorsDesc') }}</span>
                </div>
              </div>
              <div class="separator-editor">
                <div class="sep-tags">
                  <span
                    v-for="(sep, si) in separatorList"
                    :key="si"
                    class="sep-tag"
                    :class="{ 'sep-tag-special': isSpecialSep(sep) }"
                  >
                    <span class="sep-tag-text">{{ formatSepDisplay(sep) }}</span>
                    <n-popconfirm
                      :positive-text="$t('configBasic.removeSeparator')"
                      :negative-text="$t('watch.cancel')"
                      @positive-click="removeSeparator(si)"
                    >
                      <template #trigger>
                        <span class="sep-tag-remove" :title="$t('configBasic.removeSeparator')">
                          <n-icon :size="12"><CloseOutline /></n-icon>
                        </span>
                      </template>
                      {{ $t('configBasic.removeSeparator') }}「{{ formatSepDisplay(sep) }}」?
                    </n-popconfirm>
                  </span>
                  <span v-if="separatorList.length === 0" class="sep-empty">
                    {{ $t('configBasic.separatorPlaceholder') }}
                  </span>
                </div>
                <div class="sep-add-row">
                  <n-input
                    v-model:value="newSeparator"
                    :placeholder="$t('configBasic.separatorPlaceholder')"
                    size="small"
                    class="sep-input"
                    @keyup.enter="addSeparator"
                  />
                  <n-button
                    size="small"
                    secondary
                    @click="addSeparator"
                    :disabled="!newSeparator.trim()"
                  >
                    <template #icon><n-icon :size="14"><AddCircleOutline /></n-icon></template>
                    {{ $t('configBasic.addSeparator') }}
                  </n-button>
                </div>
              </div>

              <!-- 连接符 -->
              <div class="basic-row">
                <div class="basic-info">
                  <span class="basic-label">{{ $t('configBasic.joinSeparator') }}</span>
                  <span class="basic-desc">{{ $t('configBasic.joinSeparatorDesc') }}</span>
                </div>
                <n-input
                  :value="getString('music.artist.join-separator', ' / ')"
                  size="small"
                  class="basic-input-narrow"
                  @update:value="(v) => saveField('music.artist.join-separator', v)"
                />
              </div>
            </div>
          </div>

          <!-- 文件监控已迁移至"自动任务" Tab（每任务独立配置） -->
        </div>
      </n-spin>
    </div>

    <!-- ════════════════ 权限管理 Tab ════════════════ -->
    <div v-if="activeTab === 'admin'" class="tab-content">
      <!-- 系统级开关 -->
      <div class="admin-switches">
        <div class="basic-card">
          <div class="basic-row">
            <div class="basic-info">
              <span class="basic-label">{{ $t('configAdmin.allowRegistration') }}</span>
              <span class="basic-desc">{{ $t('configAdmin.allowRegistrationDesc') }}</span>
            </div>
            <n-switch
              :value="registrationAllowed"
              size="small"
              :loading="registrationLoading"
              @update:value="toggleRegistration"
            />
          </div>
        </div>
      </div>

      <n-tabs v-model:value="adminTab" type="line" animated>
        <n-tab-pane name="users" tab="用户管理">
          <UserManagement />
        </n-tab-pane>
        <n-tab-pane name="roles" tab="角色管理">
          <RoleManagement />
        </n-tab-pane>
        <n-tab-pane name="permissions" tab="权限列表">
          <PermissionList />
        </n-tab-pane>
      </n-tabs>
    </div>
  </div>
</template>

<script setup>
import { computed, h, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useMessage } from 'naive-ui'
import { useAuthStore } from '@/store/auth.js'
import {
  SearchOutline, RefreshOutline, CloudOutline,
  GitBranchOutline, SettingsOutline, PulseOutline, FlashOutline,
  CheckmarkCircleOutline, CloseCircleOutline, CloseOutline, AddCircleOutline,
  ArrowUpOutline, ArrowDownOutline, TrashOutline,
  MusicalNotesOutline, HeadsetOutline, DiscOutline, RadioOutline, LogoApple,
  PauseOutline, PlayOutline, ShieldCheckmarkOutline,
} from '@vicons/ionicons5'
import { NButton, NIcon, NTag, NText, NPopconfirm } from 'naive-ui'
import { fetchConfigs, updateConfig, refreshConfigCache } from '@/api/editor/config.js'
import { fetchAll as fetchCustomAll, update as updateCustom, remove as removeCustomApi, testProvider as testCustomApi } from '@/api/editor/custom-provider.js'
import { fetchProfiles, createProfile, updateProfile, deleteProfile, executeProfile, fetchSteps, pauseProfile, resumeProfile, scanNow as scanNowApi } from '@/api/editor/watch.js'
import { getRegisterStatus, updateRegisterStatus } from '@/api/auth/auth.js'
import ConfigFormRenderer from '@/components/editor/tool/ConfigFormRenderer.vue'
import UserManagement from '@/views/auth/admin/UserManagement.vue'
import RoleManagement from '@/views/auth/admin/RoleManagement.vue'
import PermissionList from '@/views/auth/admin/PermissionList.vue'

const message = useMessage()
const { t } = useI18n()
const auth = useAuthStore()

// ── Tab ──
const activeTab = ref('enrich')
const activeEntity = ref('song')
const adminTab = ref('users')
const registrationAllowed = ref(false)
const registrationLoading = ref(false)

async function loadRegistrationStatus() {
  try {
    const res = await getRegisterStatus()
    registrationAllowed.value = res.allowRegistration === true
  } catch { /* 加载失败保持默认 */ }
}

async function toggleRegistration(v) {
  registrationLoading.value = true
  try {
    await updateRegisterStatus(v)
    registrationAllowed.value = v
    message.success(v ? '已开放注册' : '已关闭注册')
  } catch (e) {
    message.error('修改注册开关失败')
  } finally {
    registrationLoading.value = false
  }
}
const tabs = computed(() => {
  const list = [
    { key: 'enrich', label: t('configEnrich.tab'), icon: CloudOutline },
    { key: 'watch', label: t('watch.tab'), icon: FlashOutline },
    { key: 'pipeline', label: t('configPipeline.tab'), icon: GitBranchOutline },
    { key: 'basic', label: t('configBasic.tab'), icon: SettingsOutline },
  ]
  if (auth.hasRole('ROLE_ADMIN')) {
    list.push({ key: 'admin', label: '权限管理', icon: ShieldCheckmarkOutline })
  }
  return list
})

function onTabClick(key) {
  activeTab.value = key
}
const entityTypes = computed(() => [
  { key: 'song', label: t('configEnrich.entityTypes.song') },
  { key: 'artist', label: t('configEnrich.entityTypes.artist') },
  { key: 'album', label: t('configEnrich.entityTypes.album') },
])
const entityConfigKey = computed(() =>
  activeEntity.value === 'artist' ? 'enrich.artist_provider' :
  activeEntity.value === 'album' ? 'enrich.album_provider' :
  'enrich.default_provider'
)
const enrichPrefix = computed(() => 'enrich.' + activeEntity.value)

// ── 数据 ──
const rawData = ref([])
const loading = ref(true)
const searchQuery = ref('')
const configModal = ref(false)
const testModal = ref(false)
const editingProvider = ref(null)
const testResult = ref(null)

// ── 基础配置 — 分隔符编辑器 ──
const newSeparator = ref('')

const separatorList = computed(() => {
  try {
    const raw = getString('music.artist.split-separators', '[]')
    return JSON.parse(raw)
  } catch { return [] }
})

function formatSepDisplay(sep) {
  if (sep === ' ') return '␣ 空格'
  if (sep === '\t') return '␣ Tab'
  if (sep === ' ') return '␣ NULL'
  if (sep === '\\\\') return '\\ (反斜杠)'
  return sep
}

function isSpecialSep(sep) {
  return [' ', '\t', ' ', '\\\\'].includes(sep)
}

function addSeparator() {
  const v = newSeparator.value.trim()
  if (!v) return
  const list = [...separatorList.value, v]
  saveSeparators(list)
  newSeparator.value = ''
}

function removeSeparator(index) {
  const list = separatorList.value.filter((_, i) => i !== index)
  saveSeparators(list)
}

function saveSeparators(list) {
  saveField('music.artist.split-separators', JSON.stringify(list))
}

const ARTIST_DEFAULTS = {
  'music.artist.split-separators': '["\\\\", ",", ";", "&", "+", "|", "、", "，", "/", "_", "ft.", "feat.", "featuring", "presents", "pres.", "vs.", "versus", "x", " ", "\\u0000"]',
  'music.artist.join-separator': ' / ',
}

async function restoreArtistDefaults() {
  for (const [key, value] of Object.entries(ARTIST_DEFAULTS)) {
    await saveField(key, value)
  }
  message.success(t('configBasic.restoreDefaults'))
}

// ── 自动任务 (WatchProfile) ──
const watchProfiles = ref([])
const watchStepsMeta = ref([])
const watchLoading = ref(false)
const watchModal = ref(false)
const watchEditingId = ref(null)
const watchDraft = ref(null)

// 手动执行
const watchExecModal = ref(false)
const watchExecTarget = ref(null)
const watchExecPaths = ref('')
const watchExecuting = ref(false)
const watchExecResult = ref('')

// 步骤标签（从 API 返回的 meta 动态获取）
function stepLabel(name) {
  const meta = watchStepsMeta.value.find(s => s.name === name)
  return meta?.label || name
}

// 步骤类型下拉选项（按 category 分组）
const stepTypeOptions = computed(() => {
  const song = [], artist = [], album = []
  for (const s of watchStepsMeta.value) {
    const item = { label: s.label || s.name, value: s.name }
    const cat = s.category || 'song'
    if (cat === 'artist') artist.push(item)
    else if (cat === 'album') album.push(item)
    else song.push(item)
  }
  const groups = []
  if (song.length) groups.push({ type: 'group', label: '🎵 Song', key: 'song', children: song })
  if (artist.length) groups.push({ type: 'group', label: '👤 Artist', key: 'artist', children: artist })
  if (album.length) groups.push({ type: 'group', label: '💿 Album', key: 'album', children: album })
  return groups
})

// 获取步骤的 configSchema（前端据此动态渲染配置表单）
function getStepSchema(stepName) {
  const meta = watchStepsMeta.value.find(s => s.name === stepName)
  return meta?.configSchema || null
}

// 当前任务分类
const taskCategory = computed(() => {
  const steps = watchDraft.value?.steps
  if (!steps?.length) return 'song'
  const name = steps[0].name
  if (name?.startsWith('artist-')) return 'artist'
  if (name?.startsWith('album-')) return 'album'
  return 'song'
})

// 数据来源选择 schema（仅 artist/album）
const selectionSchema = computed(() => {
  const steps = watchDraft.value?.steps
  if (!steps?.length) return null
  const meta = watchStepsMeta.value.find(s => s.name === steps[0].name)
  return meta?.selectionSchema || null
})

// 数据来源配置（存于首步骤 config._selection）
const selectionConfig = ref({})


// ── WatchProfile CRUD ──

function openWatchCreate() {
  watchEditingId.value = null
  selectionConfig.value = {}
  watchDraft.value = {
    name: '', watchPath: '/', description: '', enabled: true,
    autoTrigger: true, priority: 0, steps: [],
    dirScanEnabled: true, dirScanIntervalSec: 60,
    fileScanEnabled: true, fileScanIntervalSec: 3600,
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
    dirScanEnabled: p.dirScanEnabled ?? true,
    dirScanIntervalSec: p.dirScanIntervalSec ?? 60,
    fileScanEnabled: p.fileScanEnabled ?? true,
    fileScanIntervalSec: p.fileScanIntervalSec ?? 3600,
    steps: (p.steps || []).map(s => ({ name: s.name, config: { ...(s.config || {}) } })),
  }
  // 提取首步骤的 _selection 配置
  const steps = watchDraft.value.steps
  if (steps.length && steps[0].config?._selection) {
    selectionConfig.value = { ...steps[0].config._selection }
    delete steps[0].config._selection
  } else {
    selectionConfig.value = {}
  }
  watchModal.value = true
}

async function saveWatchProfile() {
  const d = watchDraft.value
  if (!d || !d.name.trim()) { message.warning(t('watch.nameRequired')); return }
  // 将数据来源选择嵌入首步骤 config._selection
  const payload = { ...d }
  if (payload.steps?.length && taskCategory.value !== 'song' && Object.keys(selectionConfig.value).length) {
    payload.steps = payload.steps.map((s, i) => {
      if (i === 0) return { ...s, config: { ...s.config, _selection: { ...selectionConfig.value } } }
      return s
    })
  }
  try {
    if (watchEditingId.value) {
      await updateProfile(watchEditingId.value, payload)
      message.success(t('watch.updated'))
    } else {
      await createProfile(payload)
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

// ── v2: 启停 & 状态 ──

const watchPaused = ref({})

function watchStateType(state) {
  const m = { IDLE: 'default', WAITING: 'info', RUNNING: 'success', ERROR: 'error' }
  return m[state] || 'default'
}

function watchStateLabel(state) {
  const m = {
    IDLE: t('watch.stateIdle'), WAITING: t('watch.stateWaiting'),
    RUNNING: t('watch.stateRunning'), ERROR: t('watch.stateError'),
  }
  return m[state] || state || t('watch.stateIdle')
}

function fmtTime(ts) {
  if (!ts) return ''
  try {
    const d = new Date(ts)
    if (isNaN(d.getTime())) return ts
    return d.toLocaleString()
  } catch { return ts }
}

async function togglePause(p) {
  try {
    await pauseProfile(p.id)
    watchPaused.value = { ...watchPaused.value, [p.id]: true }
    message.success(t('watch.paused'))
  } catch (e) { message.error(t('watch.toggleFailed')) }
}

async function toggleResume(p) {
  try {
    await resumeProfile(p.id)
    const next = { ...watchPaused.value }
    delete next[p.id]
    watchPaused.value = next
    message.success(t('watch.resumed'))
  } catch (e) { message.error(t('watch.toggleFailed')) }
}

async function scanNow(p) {
  try {
    message.info(t('watch.scanning'))
    await scanNowApi(p.id)
    message.success(t('watch.scanComplete'))
    await loadWatchProfiles()
  } catch (e) { message.error(t('watch.scanFailed')) }
}

// ── 步骤编辑 ──

function addStep() {
  if (!watchDraft.value) return
  const def = watchStepsMeta.value[0]
  watchDraft.value.steps.push({ name: def?.name || '', config: {} })
}

function removeStep(si) {
  watchDraft.value?.steps.splice(si, 1)
  if (!watchDraft.value?.steps.length) selectionConfig.value = {}
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
}

function setStepConfig(si, config) {
  const steps = watchDraft.value?.steps
  if (!steps) return
  steps[si].config = { ...config }
}

// ── 手动执行 ──

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

// ── 数据加载 ──

async function loadWatchProfiles() {
  watchLoading.value = true
  try { watchProfiles.value = (await fetchProfiles()) || [] } catch (e) { /* ignore */ }
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

// ── Provider 定义 ──
const providerFields = computed(() => [
  { key: 'rate_limit_ms', label: t('configEnrich.fields.rateLimitMs'), min: 50, max: 10000, step: 10, fallback: 240 },
  { key: 'max_concurrent', label: t('configEnrich.fields.maxConcurrent'), min: 1, max: 50, step: 1, fallback: 6 },
  { key: 'timeout_seconds', label: t('configEnrich.fields.timeoutSeconds'), min: 3, max: 60, step: 1, fallback: 15 },
])

const providerDefs = computed(() => {
  const T = (code) => t(`configEnrich.providers.${code}`)
  const tag = (k) => ({ key: k, label: t(`configEnrich.tags.${k}`) })
  const url = (k) => t(`configEnrich.urls.${k}`)
  const allTags = () => ['title', 'artist', 'album', 'lyric', 'cover', 'year', 'track', 'disc'].map(tag)
  return {
    qqmusic: {
      label: T('qqmusic.label'), icon: MusicalNotesOutline, color: '#10b981', version: '1.0.0',
      description: T('qqmusic.desc'),
      tags: allTags(),
      urls: [{ key: 'search_url', label: url('search') }, { key: 'album_url', label: url('album') }, { key: 'lyric_url', label: url('lyric') }],
    },
    netease: {
      label: T('netease.label'), icon: CloudOutline, color: '#ef4444', version: '1.0.0',
      description: T('netease.desc'),
      tags: allTags(),
      urls: [{ key: 'search_url', label: url('search') }, { key: 'detail_url', label: url('detail') }, { key: 'lyric_url', label: url('lyric') }],
    },
    kugou: {
      label: T('kugou.label'), icon: HeadsetOutline, color: '#3b82f6', version: '1.0.0',
      description: T('kugou.desc'),
      tags: allTags(),
      urls: [
        { key: 'search_url', label: url('search') }, { key: 'detail_url', label: url('detail') },
        { key: 'lyric_search_url', label: url('lyricSearch') }, { key: 'lyric_download_url', label: url('lyricDownload') },
      ],
    },
    kuwo: {
      label: T('kuwo.label'), icon: DiscOutline, color: '#f59e0b', version: '1.0.0',
      description: T('kuwo.desc'),
      tags: allTags(),
      urls: [{ key: 'search_url', label: url('search') }, { key: 'lyric_url', label: url('lyric') }],
    },
    migu: {
      label: T('migu.label'), icon: RadioOutline, color: '#ec4899', version: '1.0.0',
      description: T('migu.desc'),
      tags: allTags(),
      urls: [{ key: 'search_url', label: url('search') }, { key: 'song_url', label: url('song') }],
    },
    itunes: {
      label: T('itunes.label'), icon: LogoApple, color: '#a855f7', version: '1.0.0',
      description: T('itunes.desc'),
      tags: ['title', 'artist', 'album', 'cover', 'year', 'track', 'disc'].map(tag),
      urls: [{ key: 'search_url', label: url('search') }, { key: 'lookup_url', label: url('lookup') }],
    },
    musicbrainz: {
      label: T('musicbrainz.label'), icon: GitBranchOutline, color: '#6366f1', version: '1.0.0',
      description: T('musicbrainz.desc'),
      tags: ['title', 'artist', 'album', 'cover', 'year', 'track', 'disc'].map(tag),
      urls: [{ key: 'search_url', label: url('search') }, { key: 'release_url', label: url('release') }],
    },
  }
})

// ── 艺术家 Provider 定义 ──
const artistProviderDefs = computed(() => {
  const T = (code) => t(`configEnrich.providers.${code}`)
  const url = (k) => t(`configEnrich.urls.${k}`)
  const mkTag = (k) => ({ key: k, label: t(`configEnrich.entityTags.artist.${k}`) })
  const tags = () => ['introduction', 'gender', 'country', 'coverUrl'].map(mkTag)
  return {
    qqmusic:      { label: T('qqmusic.label'), icon: MusicalNotesOutline, color: '#10b981', version: '1.0.0', description: T('qqmusic.desc'), tags: tags(), urls: [{ key: 'search_url', label: url('search') }, { key: 'detail_url', label: url('detail') }, { key: 'cover_tpl', label: url('coverTpl') }] },
    netease:      { label: T('netease.label'), icon: CloudOutline, color: '#ef4444', version: '1.0.0', description: T('netease.desc'), tags: tags(), urls: [{ key: 'search_url', label: url('search') }, { key: 'detail_url', label: url('detail') }] },
    itunes:       { label: T('itunes.label'), icon: LogoApple, color: '#a855f7', version: '1.0.0', description: T('itunes.desc'), tags: ['coverUrl'].map(mkTag), urls: [{ key: 'search_url', label: url('search') }, { key: 'lookup_url', label: url('lookup') }] },
    musicbrainz:  { label: T('musicbrainz.label'), icon: GitBranchOutline, color: '#6366f1', version: '1.0.0', description: T('musicbrainz.desc'), tags: tags(), urls: [{ key: 'search_url', label: url('search') }, { key: 'detail_url', label: url('detail') }] },
    baidubaike:   { label: T('baidubaike.label'), icon: SearchOutline, color: '#f97316', version: '1.0.0', description: T('baidubaike.desc'), tags: tags(), urls: [{ key: 'search_url', label: url('search') }, { key: 'base_url', label: url('baseUrl') }] },
    wikipedia:    { label: T('wikipedia.label'), icon: CloudOutline, color: '#6366f1', version: '1.0.0', description: T('wikipedia.desc'), tags: tags(), urls: [{ key: 'api_url', label: url('apiUrl') }] },
  }
})

// ── 专辑 Provider 定义 ──
const albumProviderDefs = computed(() => {
  const T = (code) => t(`configEnrich.providers.${code}`)
  const url = (k) => t(`configEnrich.urls.${k}`)
  const mkTag = (k) => ({ key: k, label: t(`configEnrich.entityTags.album.${k}`) })
  const tags = () => ['introduction', 'albumType', 'albumYear', 'company', 'language', 'coverUrl'].map(mkTag)
  return {
    qqmusic:      { label: T('qqmusic.label'), icon: MusicalNotesOutline, color: '#10b981', version: '1.0.0', description: T('qqmusic.desc'), tags: tags(), urls: [{ key: 'api_url', label: url('apiUrl') }, { key: 'cover_tpl', label: url('coverTpl') }] },
    netease:      { label: T('netease.label'), icon: CloudOutline, color: '#ef4444', version: '1.0.0', description: T('netease.desc'), tags: ['introduction', 'albumType', 'albumYear', 'company', 'coverUrl'].map(mkTag), urls: [{ key: 'search_url', label: url('search') }, { key: 'detail_url', label: url('detail') }] },
    itunes:       { label: T('itunes.label'), icon: LogoApple, color: '#a855f7', version: '1.0.0', description: T('itunes.desc'), tags: ['albumType', 'albumYear', 'coverUrl'].map(mkTag), urls: [{ key: 'search_url', label: url('search') }] },
    musicbrainz:  { label: T('musicbrainz.label'), icon: GitBranchOutline, color: '#6366f1', version: '1.0.0', description: T('musicbrainz.desc'), tags: ['albumType', 'albumYear'].map(mkTag), urls: [{ key: 'search_url', label: url('search') }] },
  }
})

// ── 按实体类型选择 provider 定义 ──
const entityProviderDefs = computed(() => {
  if (activeEntity.value === 'artist') return artistProviderDefs.value
  if (activeEntity.value === 'album') return albumProviderDefs.value
  return providerDefs.value
})

const providerList = computed(() => {
  const list = []
  const raw = getString(entityConfigKey.value, '')
  const enabledSet = new Set(raw.split(',').map((s) => s.trim()).filter(Boolean))

  for (const [code, def] of Object.entries(entityProviderDefs.value)) {
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

// ── 管道 ──
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

// ── 其他配置 ──
const otherList = computed(() => {
  const exclude = ['enrich.', 'pipeline.', 'scanner.', 'writer.', 'delete.', 'switches.', 'music.artist.', 'watch.']
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

// ── 方法 ──

async function saveField(key, value) {
  try {
    await updateConfig(key, value)
    const ex = rawData.value.find((c) => c.configKey === key)
    if (ex) {
      ex.configValue = value
    } else {
      // 后端写入成功但 key 不在当前列表（可能是新增或隐藏前缀 key）→ 推入本地
      rawData.value.push({ configKey: key, configValue: value, valueType: 'STRING' })
    }
  } catch (e) { message.error(t('configEnrich.saveFailed', { key })) }
}

async function toggleProvider(code, enabled) {
  const configKey = entityConfigKey.value
  const current = getString(configKey, '')
  const providers = current.split(',').map((s) => s.trim()).filter(Boolean)
  const np = enabled ? [...new Set([...providers, code])] : providers.filter((p) => p !== code)
  await saveField(configKey, np.join(','))
}

function openConfig(p) {
  editingProvider.value = p
  configModal.value = true
}

async function testProvider(p) {
  const provider = providerList.value.find((x) => x.code === p.code)
  if (provider) provider.testing = true
  // 先弹窗显示加载状态
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
    if (dv) await saveField(`${enrichPrefix.value}.${code}.${k}`, dv)
  }
  message.success(t('configEnrich.restoreSuccess', { label: entityProviderDefs.value[code]?.label || code }))
}

// ── 元数据标签编辑 ──
function metadataTagEnabled(code, tag) {
  const saved = getString(`${enrichPrefix.value}.${code}.metadata_tags`, '')
  if (saved) return saved.split(',').map(s => s.trim()).includes(tag)
  // 默认：全部启用
  return true
}

async function toggleMetadataTag(code, tag) {
  const defTags = entityProviderDefs.value[code]?.tags || []
  const defKeys = defTags.map(t => t.key).join(',')
  const saved = getString(`${enrichPrefix.value}.${code}.metadata_tags`, defKeys)
  const current = saved.split(',').map(s => s.trim()).filter(Boolean)
  const next = current.includes(tag) ? current.filter(t => t !== tag) : [...current, tag]
  await saveField(`${enrichPrefix.value}.${code}.metadata_tags`, next.join(','))
}

async function refreshCache() {
  try { await refreshConfigCache(); await load(); message.success(t('configEnrich.cacheRefreshed')) } catch (e) { message.error(t('configEnrich.refreshFailed')) }
}

// ── 自定义 Provider ──
const customProviders = ref([])

async function loadCustom() {
  try { customProviders.value = (await fetchCustomAll()) || [] } catch (e) { /* ignore */ }
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
  loadRegistrationStatus()
  fetchSteps().then(steps => {
    watchStepsMeta.value = steps || []
  }).catch(() => {})
})
</script>

<style scoped>
.config-page { height: 100%; display: flex; flex-direction: column; overflow: hidden; }

/* ═══ 权限管理开关 ═══ */
.admin-switches {
  max-width: 720px;
  margin-bottom: 16px;
}

/* ═══ 基础配置 ═══ */
.basic-config-grid {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 720px;
}
.basic-section {
  display: flex;
  flex-direction: column;
}
.basic-card {
  background: var(--gradient-card, var(--color-surface));
  border: var(--border-width-strong) solid var(--color-border);
  border-radius: var(--radius-lg, 16px);
  box-shadow: var(--effect-card-inner), var(--effect-card-outer);
  overflow: hidden;
}
.basic-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  gap: 16px;
}
.basic-row + .basic-row {
  border-top: var(--border-width-default) solid var(--color-border-light, var(--color-border));
}
.basic-row-top {
  padding-bottom: 6px;
}
.basic-info {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  flex: 1;
}
.basic-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}
.basic-desc {
  font-size: 11px;
  color: var(--color-text-tertiary);
  line-height: 1.5;
}
.basic-input-narrow {
  width: 140px;
  flex-shrink: 0;
}

/* ── 分隔符编辑器 ── */
.separator-editor {
  padding: 0 18px 14px;
  border-top: var(--border-width-default) solid var(--color-border-light, var(--color-border));
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.sep-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 36px;
  align-items: center;
}
.sep-tag {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 6px 12px;
  border-radius: var(--radius-xl, 24px);
  background: var(--color-accent);
  color: var(--color-on-primary, #fff);
  font-size: 13px;
  font-weight: 500;
  line-height: 1.5;
  box-shadow: var(--effect-card-inner);
  transition: all 0.15s ease;
}
.sep-tag:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}
.sep-tag-special {
  background: #f59e0b;
}
.sep-tag-text {
  font-family: var(--font-family-mono);
  font-size: 13px;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sep-tag-remove {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  margin-left: 4px;
  border-radius: 50%;
  background: rgba(0,0,0,0.15);
  color: rgba(255,255,255,0.85);
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.15s ease;
}
.sep-tag-remove:hover {
  background: rgba(0,0,0,0.35);
  color: #fff;
  transform: scale(1.1);
}
.sep-empty {
  font-size: 13px;
  color: var(--color-text-tertiary);
  font-style: italic;
}
.sep-add-row {
  display: flex;
  gap: 8px;
}
.sep-input {
  flex: 1;
  max-width: 240px;
}

/* ═══ Tab 导航栏 — 粘土 Pill 风格 ═══ */
.tab-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: var(--gradient-button, var(--ct-bg));
  border-bottom: var(--border-width-strong) solid var(--ct-border);
  box-shadow: var(--shadow-sm);
  flex-shrink: 0;
  gap: 8px;
}
.tab-links { display: flex; align-items: center; gap: 6px; }
.tab-actions { margin-left: auto; display: flex; gap: 4px; }

.tab-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 14px;
  border-radius: var(--radius-xl, 24px);
  font-size: 12.5px;
  font-weight: 500;
  color: var(--ct-text-2);
  cursor: pointer;
  user-select: none;
  white-space: nowrap;
  text-decoration: none;
  border: var(--border-width-default, 2px) solid transparent;
  transition: all 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.tab-link:hover {
  color: var(--ct-text);
  background: var(--color-surface, var(--ct-card-bg));
  border-color: rgb(var(--ct-accent-rgb) / 0.2);
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}
.tab-link.active {
  color: var(--color-on-primary, #FFFFFF);
  background: var(--gradient-button-primary, linear-gradient(135deg, var(--ct-accent), rgb(var(--ct-accent-rgb) / 0.85)));
  border-color: var(--color-primary, var(--ct-accent));
  box-shadow: var(--shadow-md);
  font-weight: 700;
}
.tab-link:active {
  transform: scale(0.94);
  transition: transform 0.1s ease-out;
}

/* 内容区 */
.tab-content { flex: 1; overflow-y: auto; padding: 16px; }

/* ═══ 实体类型子Tab ═══ */
.entity-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 14px;
}
.entity-tab {
  display: inline-flex;
  align-items: center;
  padding: 4px 16px;
  border-radius: var(--radius-xl, 24px);
  font-size: 12px;
  font-weight: 500;
  color: var(--ct-text-2);
  cursor: pointer;
  user-select: none;
  white-space: nowrap;
  text-decoration: none;
  border: var(--border-width-default, 2px) solid transparent;
  transition: all 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.entity-tab:hover {
  color: var(--ct-text);
  background: var(--color-surface, var(--ct-card-bg));
  border-color: rgb(var(--ct-accent-rgb) / 0.2);
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}
.entity-tab.active {
  color: var(--color-on-primary, #FFFFFF);
  background: var(--gradient-button-primary, linear-gradient(135deg, var(--ct-accent), rgb(var(--ct-accent-rgb) / 0.85)));
  border-color: var(--color-primary, var(--ct-accent));
  box-shadow: var(--shadow-md);
  font-weight: 700;
}
.entity-tab:active { transform: scale(0.94); transition: transform 0.1s ease-out; }

/* ═══ 分区标题 ═══ */
.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding: 0 4px;
}
.section-bar {
  width: 3px; height: 16px;
  border-radius: 2px;
  background: var(--color-accent);
  flex-shrink: 0;
}
.section-label {
  font-size: var(--text-xs, 11px);
  font-weight: 700;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.section-count {
  font-size: var(--text-2xs, 10px);
  color: var(--color-text-tertiary);
  background: var(--color-tag-bg);
  border-radius: var(--radius-full);
  padding: 1px 7px;
  font-weight: 600;
}
.restore-link {
  margin-left: auto;
  font-size: 11px;
  color: var(--color-accent);
  cursor: pointer;
  user-select: none;
  font-weight: 500;
  transition: opacity 0.15s ease;
}
.restore-link:hover {
  opacity: 0.75;
}
.custom-section { margin-top: 8px; }

/* ═══ Provider 网格 — 响应式 ═══ */
.provider-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 12px; }

/* ═══ 粘土卡片 ═══ */
.provider-card {
  background: var(--gradient-card, var(--color-surface));
  border: var(--border-width-strong) solid var(--color-border);
  border-radius: var(--radius-lg, 16px);
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  box-shadow: var(--effect-card-inner), var(--effect-card-outer);
  transition: all 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.provider-card:hover {
  background: var(--gradient-card-hover, var(--color-surface-hover));
  box-shadow: var(--shadow-md);
  transform: var(--transform-card-hover);
  border-color: rgb(var(--ct-accent-rgb, var(--color-accent-rgb)) / 0.3);
}
.provider-card:active { transform: var(--transform-card-active); box-shadow: var(--effect-press); }
.provider-card.provider-disabled { opacity: 0.42; filter: grayscale(0.3); }

/* 卡片头部 */
.card-head { display: flex; align-items: flex-start; justify-content: space-between; }
.card-title { display: flex; align-items: center; gap: 10px; min-width: 0; }
.provider-icon {
  width: 36px; height: 36px;
  border-radius: var(--radius-md, 12px);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  box-shadow: var(--effect-card-inner);
  border: var(--border-width-default) solid rgba(255,255,255,0.25);
}
.card-name-row { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; min-width: 0; }
.provider-name { font-weight: 600; font-size: 14px; color: var(--color-text); }
.provider-version { font-size: 10px; color: var(--color-text-tertiary); }

/* 状态指示器 */
.status-dot {
  display: inline-block;
  width: 6px; height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
.status-dot.online { background: var(--color-success); box-shadow: 0 0 6px rgb(var(--color-success-rgb) / 0.5); }
.status-dot.offline { background: var(--color-text-tertiary); }

/* 描述 */
.card-desc { margin: 0; font-size: 12px; color: var(--color-text-secondary); line-height: 1.55; }

/* 元数据标签 */
.card-tags { display: flex; flex-wrap: wrap; gap: 4px; }
.card-tags :deep(.n-tag) {
  border-radius: var(--radius-xl, 24px);
  box-shadow: var(--effect-card-inner);
  border: var(--border-width-default) solid var(--color-border-light);
}

/* 操作按钮 */
.card-actions { display: flex; gap: 8px; margin-top: 2px; }

/* ── 自定义 provider ── */
.custom-provider { border-style: dashed; }
.add-card {
  cursor: pointer;
  border-style: dashed;
  border-color: var(--color-border);
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 140px;
  transition: all 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.add-card:hover { border-color: var(--color-accent); box-shadow: var(--shadow-sm); transform: translateY(-2px); }
.add-content { display: flex; flex-direction: column; align-items: center; gap: 8px; }
.add-text { font-size: 13px; color: var(--color-text-tertiary); }

/* ═══ 测试结果弹窗 — 粘土风格 ═══ */
.test-result { text-align: center; }
.test-status {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.test-icon-wrap {
  width: 56px; height: 56px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--effect-card-inner), var(--effect-card-outer);
  border: var(--border-width-strong) solid var(--color-border);
}
.test-icon-wrap.success { background: rgb(var(--color-success-rgb) / 0.1); }
.test-icon-wrap.fail { background: rgb(var(--color-destructive-rgb) / 0.1); }
.test-msg { font-size: 15px; font-weight: 600; margin: 0 0 4px; color: var(--color-text); }
.test-detail { font-size: 12px; color: var(--color-text-tertiary); margin: 0; }

/* ═══ 元数据标签编辑器 ═══ */
.metadata-tags-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}
.metadata-tag-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--gradient-card, var(--color-surface));
  border: var(--border-width-strong) solid var(--color-border);
  box-shadow: var(--effect-card-inner);
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);
  font-size: 13px;
  color: var(--color-text);
  user-select: none;
}
.metadata-tag-chip:hover { box-shadow: var(--shadow-sm); transform: translateY(-1px); }
.metadata-tag-chip.tag-off { opacity: 0.45; color: var(--color-text-tertiary); }
.metadata-tag-chip:active { transform: scale(0.96); }

/* 管道 */
.section-card { margin-bottom: 12px; }
.unit { margin-left: 6px; font-size: 11px; color: var(--color-text-tertiary); }

/* 其他 */
.config-toolbar { display: flex; align-items: center; margin-bottom: 12px; }
.search-input { flex: 1; max-width: 320px; }
.config-table { flex: 1; }

/* 自动任务 */
.watch-path { margin: 2px 0; }
.watch-steps-row { display: flex; flex-wrap: wrap; gap: 4px; margin: 4px 0; }

/* 步骤列表 */
.step-list { display: flex; flex-direction: column; gap: 10px; }
.step-item {
  background: var(--gradient-card, var(--color-surface));
  border-radius: var(--radius-lg);
  border: var(--border-width-default) solid var(--color-border);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

/* 步骤头部 */
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

/* 步骤参数区 */
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

/* organize 层级编辑器 */
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
