<template>
  <div class="login-page">
    <div class="login-card">
      <h1 class="login-title">Music Pipeline</h1>
      <p class="login-subtitle">登录以继续</p>

      <n-tabs
        v-model:value="activeTab"
        type="segment"
        animated
        class="login-tabs"
      >
        <n-tab-pane name="login" tab="登录" />
        <n-tab-pane name="register" tab="注册" />
      </n-tabs>

      <n-form
        ref="formRef"
        :model="form"
        :rules="rules"
        class="login-form"
      >
        <n-form-item path="username" label="用户名">
          <n-input
            v-model:value="form.username"
            placeholder="请输入用户名"
            :disabled="auth.loading"
            @keyup.enter="handleSubmit"
          />
        </n-form-item>

        <n-form-item path="password" label="密码">
          <n-input
            v-model:value="form.password"
            type="password"
            show-password-on="click"
            placeholder="请输入密码"
            :disabled="auth.loading"
            @keyup.enter="handleSubmit"
          />
        </n-form-item>

        <template v-if="activeTab === 'register'">
          <n-form-item path="displayName" label="显示名">
            <n-input
              v-model:value="form.displayName"
              placeholder="可选"
              :disabled="auth.loading"
            />
          </n-form-item>
          <n-form-item path="email" label="邮箱">
            <n-input
              v-model:value="form.email"
              placeholder="可选"
              :disabled="auth.loading"
            />
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


      <n-alert
        v-if="errorMsg"
        type="error"
        closable
        class="error-alert"
        @update:show="errorMsg = ''"
      >
        {{ errorMsg }}
      </n-alert>


      <div v-if="auth.isAuthenticated && auth.user" class="auth-info">
        <n-divider>当前登录信息</n-divider>
        <n-descriptions label-placement="left" :column="1" size="small" bordered>
          <n-descriptions-item label="用户名">
            {{ auth.user.username }}
          </n-descriptions-item>
          <n-descriptions-item label="显示名">
            {{ auth.user.displayName || '-' }}
          </n-descriptions-item>
          <n-descriptions-item label="角色">
            <n-tag
              v-for="r in auth.user.roles"
              :key="r"
              type="info"
              size="small"
              style="margin-right: 4px"
            >
              {{ r }}
            </n-tag>
            <span v-if="!auth.user.roles?.length">-</span>
          </n-descriptions-item>
          <n-descriptions-item label="Access Token">
            <n-ellipsis style="max-width: 320px; font-family: monospace; font-size: 11px;">
              {{ auth.accessToken }}
            </n-ellipsis>
          </n-descriptions-item>
        </n-descriptions>

        <div class="auth-actions">
          <n-button
            type="primary"
            size="small"
            @click="$router.push('/workbench')"
          >
            进入工作台
          </n-button>
          <n-button
            size="small"
            @click="handleLogout"
          >
            登出
          </n-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth.js'

const router = useRouter()
const auth = useAuthStore()

const activeTab = ref('login')
const errorMsg = ref('')
const formRef = ref(null)
const form = ref({
  username: '',
  password: '',
  displayName: '',
  email: '',
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
  } catch (err) {
    const msg =
      err?.response?.data?.message ||
      err?.response?.data?.error ||
      err?.message ||
      '请求失败，请检查后端服务是否启动'
    errorMsg.value = msg
  }
}

async function handleLogout() {
  await auth.logout()
  form.value = { username: '', password: '', displayName: '', email: '' }
  activeTab.value = 'login'
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--ct-bg, #f5f5f5);
}

.login-card {
  width: 100%;
  max-width: 420px;
  padding: 36px 32px;
  border-radius: var(--radius-xl, 12px);
  background: var(--gradient-card, var(--color-surface, #fff));
  border: var(--border-width-strong, 1px) solid var(--color-border, #e0e0e0);
  box-shadow: var(--shadow-lg, 0 4px 24px rgba(0,0,0,.08));
}

.login-title {
  font-size: 28px;
  font-weight: 700;
  text-align: center;
  margin: 0 0 4px;
  letter-spacing: -0.5px;
}

.login-subtitle {
  text-align: center;
  color: var(--ct-text-2, #888);
  margin: 0 0 20px;
  font-size: 14px;
}

.login-tabs {
  margin-bottom: 20px;
}

.login-form {
  margin-bottom: 0;
}

.submit-btn {
  margin-top: 8px;
}

.error-alert {
  margin-top: 16px;
}

.auth-info {
  margin-top: 8px;
}

.auth-actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
  justify-content: center;
}
</style>
