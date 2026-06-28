<template>
  <div class="role-mgmt">
    <div class="toolbar">
      <span></span>
      <n-button type="primary" @click="openCreate">
        <template #icon><n-icon><AddOutline /></n-icon></template>
        新增角色
      </n-button>
    </div>

    <n-spin :show="loading">
      <n-data-table
        :columns="columns"
        :data="roles"
        :row-key="(r) => r.id"
        :bordered="false"
        size="small"
      />
    </n-spin>

    <!-- 创建/编辑弹窗 -->
    <n-modal v-model:show="showForm" :title="editingId ? '编辑角色' : '新增角色'" preset="card" style="width: 480px">
      <n-form ref="formRef" :model="form" :rules="formRules" label-placement="left" label-width="80">
        <n-form-item label="角色名" path="roleName">
          <n-input v-model:value="form.roleName" placeholder="显示名称，如：编辑员" />
        </n-form-item>
        <n-form-item v-if="!editingId" label="角色码" path="roleCode">
          <n-input v-model:value="form.roleCode" placeholder="ROLE_EDITOR" />
        </n-form-item>
        <n-form-item label="描述" path="description">
          <n-input v-model:value="form.description" placeholder="角色描述" type="textarea" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showForm = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="save">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 分配权限弹窗 -->
    <n-modal v-model:show="showPerms" title="分配权限" preset="card" style="width: 420px">
      <n-checkbox-group v-model:value="permForm.permIds">
        <n-space vertical>
          <n-checkbox v-for="p in allPermissions" :key="p.id" :value="p.id" :label="`${p.permissionName} (${p.permissionCode})`" />
        </n-space>
      </n-checkbox-group>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showPerms = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="savePerms">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { h, ref, onMounted } from 'vue'
import { NButton, NInput, NIcon, NDataTable, NSpin, NModal, NForm, NFormItem, NSpace, NCheckboxGroup, NCheckbox, NPopconfirm, NTag, useMessage } from 'naive-ui'
import { AddOutline, CreateOutline, TrashOutline, LockClosedOutline } from '@vicons/ionicons5'
import { useAuthStore } from '@/store/auth.js'
import * as adminApi from '@/api/auth/admin.js'

const message = useMessage()
const auth = useAuthStore()

// ── 列表 ──
const loading = ref(false)
const roles = ref([])

const columns = [
  { key: 'roleName', title: '角色名', width: 120 },
  { key: 'roleCode', title: '角色码', width: 160 },
  { key: 'description', title: '描述', width: 200, ellipsis: { tooltip: true } },
  {
    key: 'permissions', title: '权限', width: 280,
    render: (r) => {
      const labels = (r.permissions || []).map((p) => auth.getPermissionName(p))
      return labels.length > 0
        ? h(NSpace, { wrap: false }, () => labels.map((l) => h(NTag, { size: 'small' }, () => l)))
        : '—'
    },
  },
  { key: 'userCount', title: '用户数', width: 70, align: 'center' },
  {
    key: 'actions', title: '操作', width: 160, align: 'center',
    render: (r) => h(NSpace, { justify: 'center' }, () => [
      h(NButton, { size: 'tiny', onClick: () => openPerms(r) }, { icon: () => h(NIcon, null, () => h(LockClosedOutline)) }),
      h(NButton, { size: 'tiny', onClick: () => openEdit(r) }, { icon: () => h(NIcon, null, () => h(CreateOutline)) }),
      h(NPopconfirm, { onPositiveClick: () => doDelete(r.id) }, {
        trigger: () => h(NButton, { size: 'tiny', type: 'error' }, { icon: () => h(NIcon, null, () => h(TrashOutline)) }),
        default: () => `确定删除角色 ${r.roleName}？`,
      }),
    ]),
  },
]

async function load() {
  loading.value = true
  try {
    roles.value = await adminApi.getRoles() || []
  } catch {
    message.error('加载角色列表失败')
  } finally {
    loading.value = false
  }
}

// ── 创建/编辑 ──
const showForm = ref(false)
const editingId = ref(null)
const saving = ref(false)
const formRef = ref(null)
const form = ref({ roleName: '', roleCode: '', description: '' })
const formRules = {
  roleName: [{ required: true, message: '请输入角色名', trigger: 'blur' }],
  roleCode: editingId.value ? [] : [{ required: true, message: '请输入角色码', trigger: 'blur' }],
}

function openCreate() {
  editingId.value = null
  form.value = { roleName: '', roleCode: '', description: '' }
  showForm.value = true
}

function openEdit(r) {
  editingId.value = r.id
  form.value = { roleName: r.roleName, roleCode: r.roleCode, description: r.description || '' }
  showForm.value = true
}

async function save() {
  saving.value = true
  try {
    if (editingId.value) {
      await adminApi.updateRole(editingId.value, {
        roleName: form.value.roleName,
        description: form.value.description,
      })
      message.success('角色已更新')
    } else {
      await adminApi.createRole(form.value)
      message.success('角色已创建')
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
    await adminApi.deleteRole(id)
    message.success('角色已删除')
    load()
  } catch (e) {
    message.error(e?.response?.data?.message || '删除失败')
  }
}

// ── 权限分配 ──
const showPerms = ref(false)
const permRoleId = ref(null)
const permForm = ref({ permIds: [] })
const allPermissions = ref([])

async function openPerms(r) {
  permRoleId.value = r.id
  showPerms.value = true
  const [role, perms] = await Promise.all([
    adminApi.getRole(r.id).catch(() => null),
    adminApi.getPermissions().catch(() => []),
  ])
  allPermissions.value = perms || []
  permForm.value.permIds = (role?.permissions || r.permissions || [])
    .map((code) => allPermissions.value.find((p) => p.permissionCode === code))
    .filter(Boolean)
    .map((p) => p.id)
}

async function savePerms() {
  saving.value = true
  try {
    await adminApi.assignPermissions(permRoleId.value, permForm.value.permIds)
    message.success('权限分配成功，关联用户需重新登录')
    showPerms.value = false
    load()
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
</style>
