<template>
  <div class="forbidden">
    <div class="forbidden-card">
      <div class="forbidden-icon">🔒</div>
      <h1 class="forbidden-title">403</h1>
      <p class="forbidden-desc">您没有权限访问此页面</p>
      <n-button type="primary" @click="$router.push({ name: 'Workbench' })">
        返回工作台
      </n-button>
    </div>
  </div>
</template>

<script setup>
import { NButton } from 'naive-ui'
</script>

<style scoped>
.forbidden {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: var(--ct-bg);
  color: var(--ct-text);
}
.forbidden-card {
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}
.forbidden-icon {
  font-size: 64px;
  line-height: 1;
}
.forbidden-title {
  font-size: 48px;
  font-weight: 700;
  margin: 0;
  color: var(--ct-text);
}
.forbidden-desc {
  font-size: 15px;
  color: var(--ct-text-2);
  margin: 0;
}
</style>
