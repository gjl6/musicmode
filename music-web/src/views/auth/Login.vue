<template>
  <div class="login-page">
    <div class="login-card">
      <!-- 品牌标识 -->
      <div class="brand-area">
        <div class="brand-icon-ring">
          <n-icon class="brand-icon" :component="MusicalNotesIcon" size="22" />
        </div>
      </div>

      <h1 class="login-title">Music Mode</h1>
      <p class="login-subtitle">登录以继续</p>

      <n-tabs
        v-model:value="activeTab"
        type="segment"
        animated
        class="login-tabs"
      >
        <n-tab-pane name="login" tab="登录" />
        <n-tab-pane v-if="registrationAllowed" name="register" tab="注册" />
      </n-tabs>

      <n-form
        ref="formRef"
        :model="form"
        :rules="rules"
        class="login-form"
      >
        <n-form-item path="username">
          <n-input
            v-model:value="form.username"
            placeholder="请输入用户名"
            :disabled="auth.loading"
            @keyup.enter="handleSubmit"
          >
            <template #prefix>
              <n-icon :component="PersonIcon" />
            </template>
          </n-input>
        </n-form-item>

        <n-form-item path="password">
          <n-input
            v-model:value="form.password"
            type="password"
            show-password-on="click"
            placeholder="请输入密码"
            :disabled="auth.loading"
            @keyup.enter="handleSubmit"
          >
            <template #prefix>
              <n-icon :component="LockIcon" />
            </template>
          </n-input>
        </n-form-item>

        <template v-if="activeTab === 'register'">
          <n-form-item path="displayName">
            <n-input
              v-model:value="form.displayName"
              placeholder="显示名（可选）"
              :disabled="auth.loading"
            >
              <template #prefix>
                <n-icon :component="PersonAddIcon" />
              </template>
            </n-input>
          </n-form-item>
          <n-form-item path="email">
            <n-input
              v-model:value="form.email"
              placeholder="邮箱（可选）"
              :disabled="auth.loading"
            >
              <template #prefix>
                <n-icon :component="MailIcon" />
              </template>
            </n-input>
          </n-form-item>
        </template>

        <n-button
          type="primary"
          block
          :loading="auth.loading"
          class="submit-btn"
          @click="handleSubmit"
        >
          {{ activeTab === 'login' ? '登录' : '注册' }}
        </n-button>
      </n-form>

      <!-- 错误提示 -->
      <n-alert
        v-if="errorMsg"
        type="error"
        closable
        class="error-alert"
        @update:show="errorMsg = ''"
      >
        {{ errorMsg }}
      </n-alert>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth.js'
import { getRegisterStatus } from '@/api/auth/auth.js'
import {
  MusicalNotesOutline,
  PersonOutline,
  LockClosedOutline,
  PersonAddOutline,
  MailOutline,
} from '@vicons/ionicons5'

const router = useRouter()
const auth = useAuthStore()

const MusicalNotesIcon = MusicalNotesOutline
const PersonIcon = PersonOutline
const LockIcon = LockClosedOutline
const PersonAddIcon = PersonAddOutline
const MailIcon = MailOutline

const activeTab = ref('login')
const errorMsg = ref('')
const formRef = ref(null)
const registrationAllowed = ref(false)
const form = ref({
  username: '',
  password: '',
  displayName: '',
  email: '',
})

onMounted(async () => {
  try {
    const res = await getRegisterStatus()
    registrationAllowed.value = res.allowRegistration === true
  } catch {
    // 查询失败时默认显示注册 Tab，后端会兜底返回错误
    registrationAllowed.value = true
  }
})

const rules = computed(() => {
  const base = {
    username: [
      { required: true, message: '请输入用户名', trigger: 'blur' },
      { min: 3, max: 50, message: '用户名长度 3-50', trigger: 'blur' },
    ],
    password: [
      { required: true, message: '请输入密码', trigger: 'blur' },
      { min: 6, max: 100, message: '密码长度至少 6 位', trigger: 'blur' },
    ],
    displayName: [],
    email: [],
  }
  if (activeTab.value === 'register') {
    base.displayName = [
      { max: 50, message: '显示名最长 50 字符', trigger: 'blur' },
    ]
    base.email = [
      { type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' },
    ]
  }
  return base
})

async function handleSubmit() {
  errorMsg.value = ''

  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  const { username, password, displayName, email } = form.value

  try {
    if (activeTab.value === 'login') {
      await auth.login(username, password)
    } else {
      await auth.register(username, password, displayName || undefined, email || undefined)
    }
    router.replace('/player')
  } catch (err) {
    const msg =
      err?.response?.data?.message ||
      err?.response?.data?.error ||
      err?.message ||
      '请求失败，请检查后端服务是否启动'
    errorMsg.value = msg
  }
}
</script>

<style scoped>
.login-page {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-4);
  background: var(--ct-bg);
  /* 微弱的品牌色径向辉光 */
  background-image: radial-gradient(
    ellipse at 50% 40%,
    rgba(var(--color-accent-rgb), 0.05) 0%,
    transparent 70%
  );
}

.login-card {
  width: 100%;
  max-width: 360px;
  padding: var(--space-6);
  border-radius: var(--radius-xl);
  background: var(--gradient-card, var(--color-surface));
  border: var(--border-width-default) solid var(--color-border);
  box-shadow: var(--shadow-lg);
  animation: card-enter var(--transition-slow) both;
}

@keyframes card-enter {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ── 品牌标识 ── */

.brand-area {
  display: flex;
  justify-content: center;
  margin-bottom: var(--space-4);
}

.brand-icon-ring {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-full);
  background: rgba(var(--color-accent-rgb), 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-icon {
  color: var(--color-accent);
}

/* ── 标题 ── */

.login-title {
  font-size: var(--text-2xl);
  font-weight: 700;
  text-align: center;
  margin: 0 0 var(--space-1);
  letter-spacing: -0.5px;
  color: var(--color-text);
}

.login-subtitle {
  text-align: center;
  color: var(--color-text-secondary);
  margin: 0 0 var(--space-4);
  font-size: var(--text-base);
}

/* ── Tab ── */

.login-tabs {
  margin-bottom: var(--space-5);
}

/* ── 表单 ── */

.login-form {
  margin-bottom: 0;
}

.submit-btn {
  margin-top: var(--space-2);
}

/* ── 错误 ── */

.error-alert {
  margin-top: var(--space-3);
}

/* ── Naive UI 内部间距压缩 ── */

.login-form :deep(.n-form-item) {
  margin-bottom: 19px !important;
  --n-blank-height: 0 !important;
  --n-feedback-height: 0 !important;
  --n-feedback-padding: 2px !important;
  --n-label-height: 0 !important;
  --n-label-padding: 0 !important;
}

/* 压缩表单控件自身高度 */
.login-form :deep(.n-input) {
  --n-height: 34px !important;
}

:deep(.n-tabs) {
  --n-tab-gap: 0;
}
</style>
