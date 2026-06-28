<template>
  <div class="settings-page">
    <div class="settings-header">
      <n-button text size="small" class="settings-back" @click="$router.back()">
        <template #icon><n-icon :size="18"><ArrowBackOutline /></n-icon></template>
        {{ $t('player.settings.back') }}
      </n-button>
      <h1 class="settings-title">
        <n-icon :size="24" color="var(--ct-accent)"><SettingsOutline /></n-icon>
        {{ $t('player.settings.title') }}
      </h1>
    </div>

    <div class="settings-sections">
      <!-- ═══ 个人信息 ═══ -->
      <section class="settings-card">
        <div class="sc-head">
          <h2 class="sc-title">{{ $t('player.settings.profile') }}</h2>
          <p class="sc-desc">{{ $t('player.settings.profileDesc') }}</p>
        </div>

        <!-- 两栏布局：左头像 | 右信息 -->
        <div class="sc-profile-layout">
          <!-- 左栏：大头像 -->
          <div class="sc-avatar-col">
            <div class="sc-avatar-preview" @click="triggerAvatarUpload" :title="$t('player.settings.avatarHint')">
              <n-spin :show="avatarUploading" size="small">
                <img v-if="avatarPreview" :src="avatarPreview" class="sc-avatar-img" alt="avatar" />
                <span v-else class="sc-avatar-letter">{{ avatarLetter }}</span>
              </n-spin>
              <div class="sc-avatar-overlay">
                <n-icon :size="22"><CameraOutline /></n-icon>
                <span class="sc-avatar-overlay-text">{{ $t('player.settings.avatarHint') }}</span>
              </div>
            </div>
            <input
              ref="avatarInputRef"
              type="file"
              accept="image/*"
              style="display:none"
              @change="handleAvatarChange"
            />
          </div>

          <!-- 右栏：信息字段 -->
          <div class="sc-info-col">
            <div class="sc-field">
              <label class="sc-label">{{ $t('player.settings.username') }}</label>
              <n-input :value="username" disabled />
            </div>
            <div class="sc-field">
              <label class="sc-label">{{ $t('player.settings.displayName') }}</label>
              <n-input v-model:value="profileForm.displayName" :placeholder="$t('player.settings.displayNamePlaceholder')" maxlength="64" />
            </div>
            <div class="sc-field">
              <label class="sc-label">{{ $t('player.settings.email') }}</label>
              <n-input v-model:value="profileForm.email" placeholder="email@example.com" maxlength="128" />
            </div>
            <div class="sc-actions">
              <n-button type="primary" round :loading="profileSaving" @click="saveProfile">
                {{ $t('player.settings.save') }}
              </n-button>
            </div>
          </div>
        </div>
      </section>

      <!-- ═══ 修改密码 ═══ -->
      <section class="settings-card">
        <div class="sc-head">
          <h2 class="sc-title">{{ $t('player.settings.changePassword') }}</h2>
          <p class="sc-desc">{{ $t('player.settings.changePasswordDesc') }}</p>
        </div>
        <div class="sc-body">
          <div class="sc-field">
            <label class="sc-label">{{ $t('player.settings.oldPassword') }}</label>
            <n-input v-model:value="passwordForm.oldPassword" type="password" show-password-on="click" maxlength="128" />
          </div>
          <div class="sc-field">
            <label class="sc-label">{{ $t('player.settings.newPassword') }}</label>
            <n-input v-model:value="passwordForm.newPassword" type="password" show-password-on="click" maxlength="128" />
          </div>
          <div class="sc-field">
            <label class="sc-label">{{ $t('player.settings.confirmPassword') }}</label>
            <n-input v-model:value="passwordForm.confirmPassword" type="password" show-password-on="click" maxlength="128" />
          </div>
          <div class="sc-actions">
            <n-button type="primary" round :loading="passwordSaving" @click="savePassword">
              {{ $t('player.settings.updatePassword') }}
            </n-button>
          </div>
        </div>
      </section>

      <!-- ═══ 外观 ═══ -->
      <section class="settings-card">
        <div class="sc-head">
          <h2 class="sc-title">{{ $t('player.settings.appearance') }}</h2>
          <p class="sc-desc">{{ $t('player.settings.appearanceDesc') }}</p>
        </div>
        <div class="sc-body">
          <div class="sc-toggle-row">
            <div class="sc-toggle-info">
              <span class="sc-toggle-label">{{ $t('player.settings.theme') }}</span>
              <span class="sc-toggle-hint">{{ app.colorScheme === 'dark' ? $t('player.settings.darkMode') : $t('player.settings.lightMode') }}</span>
            </div>
            <n-switch
              :value="app.colorScheme === 'dark'"
              @update:value="app.toggleTheme()"
              :rail-style="() => ({ background: app.colorScheme === 'dark' ? 'var(--ct-accent)' : 'var(--ct-border)' })"
            >
              <template #checked-icon><n-icon :size="14"><MoonOutline /></n-icon></template>
              <template #unchecked-icon><n-icon :size="14"><SunnyOutline /></n-icon></template>
            </n-switch>
          </div>
          <div class="sc-toggle-row">
            <div class="sc-toggle-info">
              <span class="sc-toggle-label">{{ $t('player.settings.style') }}</span>
              <span class="sc-toggle-hint">{{ app.themeStyle === 'clay' ? $t('player.settings.clayStyle') : $t('player.settings.defaultStyle') }}</span>
            </div>
            <n-switch
              :value="app.themeStyle === 'clay'"
              @update:value="app.toggleThemeStyle()"
              :rail-style="() => ({ background: app.themeStyle === 'clay' ? 'var(--ct-accent)' : 'var(--ct-border)' })"
            >
              <template #checked-icon><n-icon :size="14"><CubeOutline /></n-icon></template>
              <template #unchecked-icon><n-icon :size="14"><CubeOutline /></n-icon></template>
            </n-switch>
          </div>
        </div>
      </section>

      <!-- ═══ 语言 ═══ -->
      <section class="settings-card">
        <div class="sc-head">
          <h2 class="sc-title">{{ $t('player.settings.language') }}</h2>
          <p class="sc-desc">{{ $t('player.settings.languageDesc') }}</p>
        </div>
        <div class="sc-body">
          <div class="sc-toggle-row">
            <div class="sc-toggle-info">
              <span class="sc-toggle-label">{{ $t('player.settings.interfaceLanguage') }}</span>
              <span class="sc-toggle-hint">{{ app.locale === 'zh-CN' ? '中文' : 'English' }}</span>
            </div>
            <n-switch
              :value="app.locale === 'en'"
              @update:value="toggleLang"
              :rail-style="() => ({ background: app.locale === 'en' ? 'var(--ct-accent)' : 'var(--ct-border)' })"
            >
              <template #checked-icon><span style="font-size:11px;font-weight:700">EN</span></template>
              <template #unchecked-icon><span style="font-size:11px;font-weight:700">中</span></template>
            </n-switch>
          </div>
        </div>
      </section>

      <!-- ═══ 危险区域 ═══ -->
      <section class="settings-card settings-card--danger">
        <div class="sc-head">
          <h2 class="sc-title">{{ $t('player.settings.dangerZone') }}</h2>
        </div>
        <div class="sc-body">
          <div class="sc-actions">
            <n-button type="error" round @click="handleLogout">
              <template #icon><n-icon :size="16"><LogOutOutline /></n-icon></template>
              {{ $t('player.settings.logout') }}
            </n-button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  SettingsOutline, MoonOutline, SunnyOutline,
  CubeOutline, LogOutOutline, CameraOutline,
  ArrowBackOutline,
} from '@vicons/ionicons5'
import { useAppStore } from '@/store/editor/app.js'
import { useAuthStore } from '@/store/auth.js'
import { changePassword, updateProfile, uploadAvatar, getAvatarUrl } from '@/api/auth/auth.js'

const { t, locale } = useI18n()
const router = useRouter()
const app = useAppStore()
const auth = useAuthStore()

// ── 头像 ──
const avatarInputRef = ref(null)
const avatarUploading = ref(false)

const avatarPreview = computed(() => {
  if (auth.user?.id) {
    void auth.avatarVersion
    return `${getAvatarUrl(auth.user.id)}?v=${auth.avatarVersion}`
  }
  return null
})

const avatarLetter = computed(() => {
  const name = auth.user?.displayName || auth.user?.username || ''
  return name ? name.charAt(0).toUpperCase() : '?'
})

function triggerAvatarUpload() {
  avatarInputRef.value?.click()
}

async function handleAvatarChange(e) {
  const file = e.target.files?.[0]
  if (!file) return

  if (!file.type.startsWith('image/')) {
    window.$message?.warning(t('player.settings.avatarInvalidType'))
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    window.$message?.warning(t('player.settings.avatarTooLarge'))
    return
  }

  avatarUploading.value = true
  try {
    const result = await uploadAvatar(file)
    // 更新 store 中的 avatarPath + 触发联动刷新
    if (auth.user) {
      auth.user.avatarPath = result.avatarPath
    }
    auth.bumpAvatar()
    window.$message?.success(t('player.settings.avatarUploaded'))
  } catch (e) {
    window.$message?.error(e?.message || t('player.settings.saveFailed'))
  } finally {
    avatarUploading.value = false
    // 清空 input，允许重复选择同一文件
    e.target.value = ''
  }
}

// ── 个人资料 ──
const username = computed(() => auth.user?.username || '')
const profileSaving = ref(false)

const profileForm = ref({
  displayName: auth.user?.displayName || '',
  email: auth.user?.email || '',
})

async function saveProfile() {
  profileSaving.value = true
  try {
    const updated = await updateProfile(
      profileForm.value.displayName,
      profileForm.value.email
    )
    // 同步更新 store 中的用户信息
    if (auth.user) {
      auth.user.displayName = updated.displayName
      auth.user.email = updated.email
    }
    window.$message?.success(t('player.settings.profileSaved'))
  } catch (e) {
    window.$message?.error(e?.message || t('player.settings.saveFailed'))
  } finally {
    profileSaving.value = false
  }
}

// ── 修改密码 ──
const passwordSaving = ref(false)
const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

async function savePassword() {
  const { oldPassword, newPassword, confirmPassword } = passwordForm.value
  if (!oldPassword) {
    window.$message?.warning(t('player.settings.oldPasswordRequired'))
    return
  }
  if (!newPassword || newPassword.length < 6) {
    window.$message?.warning(t('player.settings.newPasswordTooShort'))
    return
  }
  if (newPassword !== confirmPassword) {
    window.$message?.warning(t('player.settings.passwordMismatch'))
    return
  }

  passwordSaving.value = true
  try {
    await changePassword(oldPassword, newPassword)
    window.$message?.success(t('player.settings.passwordChanged'))
    passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
    // 密码变更后强制重新登录
    setTimeout(() => {
      auth.logout()
      router.push({ name: 'Login' })
    }, 1500)
  } catch (e) {
    window.$message?.error(e?.message || t('player.settings.saveFailed'))
  } finally {
    passwordSaving.value = false
  }
}

// ── 语言切换 ──
function toggleLang() {
  const next = app.locale === 'zh-CN' ? 'en' : 'zh-CN'
  app.setLocale(next)
  locale.value = next
}

// ── 退出 ──
async function handleLogout() {
  await auth.logout()
  router.push({ name: 'Login' })
}
</script>

<style scoped>
.settings-page {
  width: min(100% - var(--content-padding-x) * 2, 680px);
  margin: 0 auto;
  padding: 32px 0 48px;
}

.settings-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 28px;
}

.settings-back {
  flex-shrink: 0;
  color: var(--ct-text-2) !important;
  padding: 4px 8px;
  border-radius: var(--radius-sm) !important;
}
.settings-back:hover {
  color: var(--ct-accent) !important;
  background: var(--gradient-button, var(--ct-bg-secondary)) !important;
}

.settings-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: var(--text-2xl);
  font-weight: 700;
  margin: 0;
  color: var(--ct-text);
  letter-spacing: -0.5px;
}

/* ═══ Sections ═══ */
.settings-sections {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.settings-card {
  padding: 24px;
  border-radius: var(--radius-xl);
  border: var(--border-width-strong, 3px) solid var(--color-border);
  background: var(--gradient-card);
  box-shadow: var(--effect-card-inner), var(--effect-card-outer);
}

.settings-card--danger {
  border-color: rgba(var(--color-destructive-rgb) / 0.3);
}

/* ── Card header ── */
.sc-head {
  margin-bottom: 20px;
}

.sc-title {
  font-size: var(--text-md);
  font-weight: 600;
  margin: 0 0 4px;
  color: var(--ct-text);
}

.sc-desc {
  font-size: var(--text-xs);
  color: var(--ct-text-3);
  margin: 0;
}

/* ── Card body ── */
.sc-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ── Profile two-column layout ── */
.sc-profile-layout {
  display: flex;
  gap: 28px;
  align-items: flex-start;
}

.sc-avatar-col {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

/* ── Avatar large ── */
.sc-avatar-preview {
  width: 120px;
  height: 120px;
  flex-shrink: 0;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  border: var(--border-width-strong, 3px) solid var(--color-border);
  background: var(--gradient-button-primary, var(--color-accent));
  box-shadow:
    inset 1px 1px 4px rgba(255 255 255 / 0.3),
    inset -3px -3px 6px rgba(0 0 0 / 0.12),
    0 4px 16px rgb(var(--color-accent-rgb) / 0.25);
  cursor: pointer;
  overflow: hidden;
  transition: transform var(--transition-fast), box-shadow var(--transition-fast);
}
.sc-avatar-preview:hover {
  transform: scale(1.05);
}
.sc-avatar-preview:hover .sc-avatar-overlay {
  opacity: 1;
}

.sc-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
}

.sc-avatar-letter {
  font-size: 44px;
  font-weight: 700;
  color: #fff;
}

.sc-avatar-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border-radius: 50%;
  background: rgba(0 0 0 / 0.4);
  color: #fff;
  opacity: 0;
  transition: opacity var(--transition-fast);
}

.sc-avatar-overlay-text {
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
}

/* ── Info col ── */
.sc-info-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ── Field ── */
.sc-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sc-label {
  font-size: var(--text-xs);
  font-weight: 500;
  color: var(--ct-text-2);
}

/* ── Toggle row ── */
.sc-toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
}

.sc-toggle-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sc-toggle-label {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--ct-text);
}

.sc-toggle-hint {
  font-size: var(--text-2xs);
  color: var(--ct-text-3);
}

/* ── Actions ── */
.sc-actions {
  padding-top: 4px;
}

/* ── Responsive ── */
@media (max-width: 600px) {
  .settings-page {
    padding: 20px 0 32px;
  }
  .settings-card {
    padding: 18px;
  }
  .sc-profile-layout {
    flex-direction: column;
    align-items: center;
  }
  .sc-avatar-preview {
    width: 96px;
    height: 96px;
  }
  .sc-avatar-letter {
    font-size: 36px;
  }
}
</style>
