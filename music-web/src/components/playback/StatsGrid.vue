<template>
  <div class="stats-grid">
    <n-card v-for="(stat, i) in items" :key="i" size="small" class="stat-card">
      <n-icon :size="22" :color="stat.color || 'var(--ct-accent)'">
        <component :is="stat.icon" />
      </n-icon>
      <div class="stat-value">{{ stat.value }}</div>
      <div class="stat-label">{{ stat.label }}</div>
    </n-card>
  </div>
</template>

<script setup>
defineProps({
  items: { type: Array, required: true },

})
</script>

<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
}

.stat-card {
  text-align: center;
  padding: 16px;
}

.stat-card :deep(.n-card__content) {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--ct-text);
  font-feature-settings: 'tnum';
  letter-spacing: -1px;
}

.stat-label {
  font-size: 11px;
  color: var(--ct-text-3);
  letter-spacing: 0.03em;
  text-transform: uppercase;
}
</style>
