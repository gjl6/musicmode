<template>
  <div class="edit-section">
    <div class="section-header" @click="collapsed = !collapsed">
      <span class="section-chevron" :class="{ rotated: !collapsed }">
        <n-icon :size="16"><ChevronDownOutline /></n-icon>
      </span>
      <span class="section-title">{{ title }}</span>
      <span v-if="dirtyCount > 0" class="section-badge">{{ dirtyCount }}</span>
    </div>
    <div v-if="!collapsed" class="section-body">
      <slot />
    </div>
    <n-divider style="margin: 0 0 4px 0" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ChevronDownOutline } from '@vicons/ionicons5'

const props = defineProps({
  title: { type: String, required: true },
  dirtyCount: { type: Number, default: 0 },
  defaultOpen: { type: Boolean, default: false },
})

const collapsed = ref(!props.defaultOpen)
</script>
