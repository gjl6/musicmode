<template>
  <div class="perm-list">
    <n-spin :show="loading">
      <n-data-table
        :columns="columns"
        :data="permissions"
        :row-key="(r) => r.id"
        :bordered="false"
        size="small"
      />
    </n-spin>
  </div>
</template>

<script setup>
import { h, ref, onMounted } from 'vue'
import { NDataTable, NSpin, NTag, useMessage } from 'naive-ui'
import * as adminApi from '@/api/auth/admin.js'

const message = useMessage()
const loading = ref(false)
const permissions = ref([])

const columns = [
  {
    key: 'permissionCode',
    title: '权限码',
    width: 200,
  },
  {
    key: 'permissionName',
    title: '名称',
    width: 140,
    render: (r) => r.permissionName,
  },
  {
    key: 'description',
    title: '描述',
    ellipsis: { tooltip: true },
  },
  {
    key: 'roleCount',
    title: '关联角色',
    width: 120,
    align: 'center',
    render: (r) => r.roleCount || 0,
  },
]

async function load() {
  loading.value = true
  try {
    permissions.value = await adminApi.getPermissions() || []
  } catch {
    message.error('加载权限列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
