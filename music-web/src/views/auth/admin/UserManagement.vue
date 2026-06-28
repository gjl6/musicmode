<template>
  <div class="user-mgmt">
    <!-- 工具栏 -->
    <div class="toolbar">
      <n-input
        v-model:value="keyword"
        placeholder="搜索用户名、邮箱或显示名..."
        clearable
        style="width: 280px"
        @update:value="onSearch"
      />
      <n-button type="primary" @click="openCreate">
        <template #icon><n-icon><AddOutline /></n-icon></template>
        新增用户
      </n-button>
    </div>

    <!-- 表格 -->
    <n-spin :show="loading">
      <n-data-table
        :columns="columns"
        :data="users"
        :row-key="(r) => r.id"
        :bordered="false"
        size="small"
      />
    </n-spin>

    <!-- 分页 -->
    <div v-if="total > 0" class="pager">
      <n-pagination
        v-model:page="page"
        :page-size="size"
        :item-count="total"
        :page-sizes="[10, 20, 50]"
        show-size-picker
        @update:page="load"
        @update:page-size="onSizeChange"
      />
    </div>

    <!-- 新增/编辑弹窗 -->
    <n-modal v-model:show="showForm" :title="editingId ? '编辑用户' : '新增用户'" preset="card" style="width: 520px">
      <n-form ref="formRef" :model="form" :rules="formRules" label-placement="left" label-width="80">
        <n-form-item label="用户名" path="username">
          <n-input v-model:value="form.username" :disabled="!!editingId" placeholder="登录用户名" />
        </n-form-item>
        <n-form-item v-if="!editingId" label="密码" path="password">
          <n-input v-model:value="form.password" type="password" placeholder="至少6位" />
        </n-form-item>
        <n-form-item label="显示名" path="displayName">
          <n-input v-model:value="form.displayName" placeholder="显示名称" />
        </n-form-item>
        <n-form-item label="邮箱" path="email">
          <n-input v-model:value="form.email" placeholder="user@example.com" />
        </n-form-item>
        <n-form-item v-if="editingId" label="状态" path="status">
          <n-switch v-model:value="form.status" :checked-value="1" :unchecked-value="0" checked-text="启用" unchecked-text="禁用" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showForm = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="save">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 分配角色弹窗 -->
    <n-modal v-model:show="showRoles" title="分配角色" preset="card" style="width: 420px">
      <n-checkbox-group v-model:value="roleForm.roleIds">
        <n-space vertical>
          <n-checkbox v-for="r in allRoles" :key="r.id" :value="r.id" :label="r.roleName" />
        </n-space>
      </n-checkbox-group>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showRoles = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveRoles">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 重置密码弹窗 -->
    <n-modal v-model:show="showPwd" title="重置密码" preset="card" style="width: 380px">
      <n-form :model="pwdForm" label-placement="left" label-width="80">
        <n-form-item label="新密码">
          <n-input v-model:value="pwdForm.newPassword" type="password" placeholder="至少6位" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showPwd = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="savePwd">确定</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { h, ref, onMounted } from 'vue'
import { NButton, NInput, NIcon, NDataTable, NSpin, NPagination, NModal, NForm, NFormItem, NSpace, NSwitch, NCheckboxGroup, NCheckbox, NPopconfirm, NTag, useMessage } from 'naive-ui'
import { AddOutline, CreateOutline, TrashOutline, KeyOutline, PeopleOutline } from '@vicons/ionicons5'
import * as adminApi from '@/api/auth/admin.js'

const message = useMessage()

// ── 列表 ──
const loading = ref(false)
const users = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')

const columns = [
  { key: 'username', title: '用户名', width: 130, ellipsis: { tooltip: true } },
  { key: 'displayName', title: '显示名', width: 120 },
  { key: 'email', title: '邮箱', width: 180, ellipsis: { tooltip: true } },
  {
    key: 'status', title: '状态', width: 70, align: 'center',
    render: (r) => r.status === 1
      ? h(NTag, { type: 'success', size: 'small' }, () => '正常')
      : h(NTag, { type: 'error', size: 'small' }, () => '禁用'),
  },
  {
    key: 'roles', title: '角色', width: 150,
    render: (r) => (r.roles || []).join(', ') || '—',
  },
  {
    key: 'createTime', title: '创建时间', width: 160,
    render: (r) => r.createTime ? r.createTime.substring(0, 16).replace('T', ' ') : '—',
  },
  {
    key: 'actions', title: '操作', width: 220, align: 'center',
    render: (r) => h(NSpace, { justify: 'center' }, () => [
      h(NButton, { size: 'tiny', onClick: () => openEdit(r) }, { icon: () => h(NIcon, null, () => h(CreateOutline)) }),
      h(NButton, { size: 'tiny', onClick: () => openRoles(r) }, { icon: () => h(NIcon, null, () => h(PeopleOutline)) }),
      h(NButton, { size: 'tiny', onClick: () => openPwd(r) }, { icon: () => h(NIcon, null, () => h(KeyOutline)) }),
      h(NPopconfirm, { onPositiveClick: () => doDelete(r.id) }, {
        trigger: () => h(NButton, { size: 'tiny', type: 'error' }, { icon: () => h(NIcon, null, () => h(TrashOutline)) }),
        default: () => `确定删除用户 ${r.username}？`,
      }),
    ]),
  },
]

async function load() {
  loading.value = true
  try {
    const res = await adminApi.getUsers({ keyword: keyword.value, page: page.value, size: size.value })
    users.value = res.data || []
    total.value = res.total || 0
  } catch (e) {
    message.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

let searchTimer = null
function onSearch() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { page.value = 1; load() }, 300)
}
function onSizeChange(s) { size.value = s; page.value = 1; load() }

// ── 创建/编辑 ──
const showForm = ref(false)
const editingId = ref(null)
const saving = ref(false)
const formRef = ref(null)
const form = ref({ username: '', password: '', displayName: '', email: '', status: 1 })
const formRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: editingId.value ? [] : [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 6, message: '密码至少6位', trigger: 'blur' }],
}

function openCreate() {
  editingId.value = null
  form.value = { username: '', password: '', displayName: '', email: '', status: 1 }
  showForm.value = true
}

function openEdit(r) {
  editingId.value = r.id
  form.value = { username: r.username, displayName: r.displayName || '', email: r.email || '', status: r.status }
  showForm.value = true
}

async function save() {
  saving.value = true
  try {
    if (editingId.value) {
      await adminApi.updateUser(editingId.value, {
        displayName: form.value.displayName,
        email: form.value.email,
        status: form.value.status,
      })
      message.success('用户已更新')
    } else {
      await adminApi.createUser(form.value)
      message.success('用户已创建')
    }
    showForm.value = false
    load()
  } catch (e) {
    message.error(e?.response?.data?.message || '操作失败')
  } finally {
    saving.value = false
  }
}

// ── 删除 ──
async function doDelete(id) {
  try {
    await adminApi.deleteUser(id)
    message.success('用户已删除')
    load()
  } catch (e) {
    message.error(e?.response?.data?.message || '删除失败')
  }
}

// ── 角色分配 ──
const showRoles = ref(false)
const roleUserId = ref(null)
const roleForm = ref({ roleIds: [] })
const allRoles = ref([])

async function openRoles(r) {
  roleUserId.value = r.id
  showRoles.value = true
  // 加载所有角色 + 用户的当前角色
  const [roles, userRes] = await Promise.all([
    adminApi.getRoles(),
    adminApi.getUser(r.id).catch(() => null),
  ])
  allRoles.value = roles || []
  const userRoles = userRes?.roles || r.roles || []
  roleForm.value.roleIds = allRoles.value.filter(rr => userRoles.includes(rr.roleCode)).map(rr => rr.id)
}

async function saveRoles() {
  saving.value = true
  try {
    await adminApi.assignRoles(roleUserId.value, roleForm.value.roleIds)
    message.success('角色分配成功，用户需重新登录')
    showRoles.value = false
    load()
  } catch (e) {
    message.error(e?.response?.data?.message || '操作失败')
  } finally {
    saving.value = false
  }
}

// ── 密码重置 ──
const showPwd = ref(false)
const pwdUserId = ref(null)
const pwdForm = ref({ newPassword: '' })

function openPwd(r) {
  pwdUserId.value = r.id
  pwdForm.value.newPassword = ''
  showPwd.value = true
}

async function savePwd() {
  if (!pwdForm.value.newPassword || pwdForm.value.newPassword.length < 6) {
    message.warning('密码至少6位')
    return
  }
  saving.value = true
  try {
    await adminApi.resetPassword(pwdUserId.value, { newPassword: pwdForm.value.newPassword })
    message.success('密码已重置，用户需重新登录')
    showPwd.value = false
  } catch (e) {
    message.error(e?.response?.data?.message || '操作失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
