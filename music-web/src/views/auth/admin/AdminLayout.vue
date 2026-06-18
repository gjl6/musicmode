<template>
  <div class="admin-layout">
    <header class="admin-header">
      <n-button text @click="$router.push({ name: 'Workbench' })">
        <template #icon><n-icon><ArrowBackOutline /></n-icon></template>
        返回工作台
      </n-button>
      <h1 class="admin-title">权限管理</h1>
    </header>

    <n-tabs
      :value="activeTab"
      type="line"
      animated
      @update:value="onTabChange"
    >
      <n-tab-pane name="users" tab="用户管理" />
      <n-tab-pane name="roles" tab="角色管理" />
      <n-tab-pane name="permissions" tab="权限列表" />
    </n-tabs>

    <div class="admin-body">
      <router-view />
    </div>
  </div>
</template>

<script setup>
import { computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NButton, NIcon, NTabs, NTabPane } from 'naive-ui'
import { ArrowBackOutline } from '@vicons/ionicons5'

const router = useRouter()
const route = useRoute()

const TAB_ROUTES = { users: 'AdminUsers', roles: 'AdminRoles', permissions: 'AdminPermissions' }

const activeTab = computed(() => {
  if (route.name === 'AdminUsers') return 'users'
  if (route.name === 'AdminRoles') return 'roles'
  if (route.name === 'AdminPermissions') return 'permissions'
  return 'users'
})

function onTabChange(key) {
  router.push({ name: TAB_ROUTES[key] })
}
</script>

<style scoped>
.admin-layout {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
  min-height: 100vh;
  background: var(--ct-bg);
  color: var(--ct-text);
}
.admin-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
.admin-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
}
.admin-body {
  padding-top: 16px;
}
</style>
