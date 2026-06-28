<template>
  <span class="star-rating" :class="{ 'star-rating--readonly': readonly }" @click.stop>
    <button
      v-for="s in 5"
      :key="s"
      class="star-btn"
      :class="{ active: s <= model }"
      :disabled="readonly || loading"
      :title="readonly ? `${model}/5` : `${s} 星`"
      @click="setRating(s)"
    >
      <n-icon :size="size" :color="s <= model ? '#F59E0B' : undefined">
        <Star v-if="s <= model" />
        <StarOutline v-else />
      </n-icon>
    </button>
  </span>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { Star, StarOutline } from '@vicons/ionicons5'
import { NIcon } from 'naive-ui'
import { subsonicSetRating } from '@/api/playback/subsonic.js'

const props = defineProps({
  /** 当前评分 0-5（0 表示未评分） */
  rating: { type: Number, default: 0 },
  /** 歌曲 ID */
  songId: { type: [Number, String], default: null },
  /** 只读模式（不显示为可点击按钮） */
  readonly: { type: Boolean, default: false },
  /** 星星大小 */
  size: { type: Number, default: 16 },
})

const emit = defineEmits(['rated'])

const model = ref(Number(props.rating) || 0)
const loading = ref(false)

watch(() => props.rating, (v) => {
  model.value = Number(v) || 0
})

async function setRating(r) {
  if (props.readonly || loading.value) return
  if (!props.songId) return
  const prev = model.value
  // 点击同一颗星 = 取消评分（设为 0）
  const newVal = r === prev ? 0 : r
  model.value = newVal
  loading.value = true
  try {
    await subsonicSetRating(props.songId, newVal)
    emit('rated', newVal)
  } catch {
    model.value = prev  // 失败回滚
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.star-rating {
  display: inline-flex;
  gap: 1px;
  align-items: center;
}
.star-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 2px;
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--ct-text-3);
  border-radius: 3px;
  transition: transform 0.12s, color 0.12s;
}
.star-btn:not(:disabled):hover {
  transform: scale(1.2);
  color: #F59E0B;
}
.star-btn:disabled {
  cursor: default;
}
.star-btn.active {
  color: #F59E0B;
}
.star-rating--readonly .star-btn {
  cursor: default;
}
</style>
