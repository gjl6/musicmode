<template>
  <div class="cover-art" :class="classes" :style="containerStyle">
    <img
      v-if="src"
      v-show="loaded && !error"
      :src="src"
      :alt="alt"
      class="cover-img"
      :class="{ circle }"
      @error="onError"
      @load="onLoad"
    />
    <div v-if="!src || !loaded || error" class="cover-placeholder" :class="{ circle }">
      <n-icon :size="iconSize">
        <component :is="placeholderIcon" />
      </n-icon>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { MusicalNotesOutline, PersonOutline, DiscOutline } from '@vicons/ionicons5'

const props = defineProps({
  src: { type: String, default: '' },
  alt: { type: String, default: '' },
  size: { type: [Number, String], default: 160 },
  circle: { type: Boolean, default: false },

  type: { type: String, default: 'album' },
})

const loaded = ref(false)
const error = ref(false)

const containerStyle = computed(() => ({}))

const classes = computed(() => ({
  'cover-art--loaded': loaded,
  'cover-art--error': error,
}))

const iconSize = computed(() => {
  const s = typeof props.size === 'number' ? props.size : parseInt(props.size) || 160
  return Math.max(16, Math.floor(s / 4))
})

const placeholderIcon = computed(() => {
  switch (props.type) {
    case 'artist': return PersonOutline
    case 'album': return DiscOutline
    default: return MusicalNotesOutline
  }
})

function onLoad() {
  loaded.value = true
  error.value = false
}

function onError() {
  loaded.value = false
  error.value = true
}
</script>

<style scoped>
.cover-art {
  position: relative;
  width: 100%;
  height: 100%;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 4px;
  background: var(--ct-bg-secondary);
}

.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.cover-img.circle {
  border-radius: 50%;
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ct-text-3);
  background: var(--ct-bg-secondary);
}
.cover-placeholder.circle {
  border-radius: 50%;
}
</style>
