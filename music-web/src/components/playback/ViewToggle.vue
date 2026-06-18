<template>
  <n-tooltip>
    <template #trigger>
      <n-button
        size="small"
        @click="$emit('update:modelValue', modelValue === 'grid' ? 'list' : 'grid')"
      >
        <template #icon>
          <n-icon :size="16">
            <GridOutline v-if="modelValue === 'list'" />
            <ListOutline v-else />
          </n-icon>
        </template>
      </n-button>
    </template>
    {{ modelValue === 'grid' ? $t('viewToggle.switchToList') : $t('viewToggle.switchToGrid') }}
  </n-tooltip>
</template>

<script setup>
import { GridOutline, ListOutline } from '@vicons/ionicons5'

defineProps({
  modelValue: { type: String, default: 'grid' },
})

defineEmits(['update:modelValue'])
</script>
