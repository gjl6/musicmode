<template>
  <div class="alphabet-index">
    <span
      v-for="letter in letters"
      :key="letter"
      class="alpha-letter"
      :class="{ active: letter === active, empty: !available.has(letter) }"
      @click="available.has(letter) && $emit('select', letter)"
    >
      {{ letter }}
    </span>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  active: { type: String, default: '' },
  available: { type: Set, default: () => new Set() },
})

defineEmits(['select'])

const letters = computed(() =>
  'ABCDEFGHIJKLMNOPQRSTUVWXYZ#'.split('')
)
</script>

<style scoped>
.alphabet-index {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  padding: 4px 2px;
  font-size: 10px;
  user-select: none;
}

.alpha-letter {
  cursor: pointer;
  padding: 1px 4px;
  border-radius: 2px;
  color: var(--ct-text-3);
  font-weight: 500;
  transition: all 0.1s;
}
.alpha-letter:hover {
  color: var(--ct-accent);
  background: rgb(var(--ct-accent-rgb) / 0.1);
}
.alpha-letter.active {
  color: var(--color-on-accent);
  background: var(--ct-accent);
}
.alpha-letter.empty {
  color: var(--ct-text-4);
  cursor: default;
}
.alpha-letter.empty:hover {
  color: var(--ct-text-4);
  background: none;
}
</style>
