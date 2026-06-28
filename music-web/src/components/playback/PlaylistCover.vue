<template>
  <div class="playlist-cover" :style="containerStyle">
    <canvas
      ref="canvasRef"
      :width="sizeNum"
      :height="sizeNum"
      class="cover-canvas"
    />
    <!-- 加载中/无歌曲时占位 -->
    <div v-if="showPlaceholder" class="cover-placeholder">
      <n-icon :size="iconSize" color="var(--ct-text-3)">
        <MusicalNotesOutline />
      </n-icon>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { MusicalNotesOutline } from '@vicons/ionicons5'
import { NIcon } from 'naive-ui'
import { subsonicGetCoverArtUrl } from '@/api/playback/subsonic.js'

const props = defineProps({
  entries: { type: Array, default: () => [] },
  size: { type: [Number, String], default: 180 },
  /** 无歌曲时回退的封面 art ID（歌单/专辑 ID 等） */
  fallbackArt: { type: String, default: '' },
})

const canvasRef = ref(null)
const dataUrl = ref('')
const loading = ref(true)
const showPlaceholder = ref(true)

const sizeNum = computed(() => {
  const s = typeof props.size === 'number' ? props.size : parseInt(props.size) || 180
  return s
})

const iconSize = computed(() => Math.max(16, Math.floor(sizeNum.value / 5)))

const containerStyle = computed(() => {
  const s = `${sizeNum.value}px`
  return { width: s, height: s }
})

// ── 获取封面 URL ──

function getCoverUrls() {
  const entries = props.entries || []
  const half = Math.ceil(sizeNum.value / 2)
  const urls = []
  for (let i = 0; i < 4; i++) {
    const entry = entries[i]
    if (entry) {
      const artId = entry.coverArt || entry.id
      urls.push(artId ? subsonicGetCoverArtUrl(artId, half) : null)
    } else {
      urls.push(null)
    }
  }
  return urls
}

// ── 图片加载工具 ──

function loadImage(url) {
  return new Promise((resolve) => {
    if (!url) { resolve(null); return }
    const img = new Image()
    img.crossOrigin = 'anonymous'
    const timer = setTimeout(() => { resolve(null) }, 5000)
    img.onload = () => { clearTimeout(timer); resolve(img) }
    img.onerror = () => { clearTimeout(timer); resolve(null) }
    img.src = url
  })
}

// ── 绘制拼贴 ──

const ACCENT_COLORS = [
  '#6366F1', // indigo
  '#8B5CF6', // violet
  '#EC4899', // pink
  '#F97316', // orange
]

async function drawCollage() {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const w = sizeNum.value
  const half = Math.ceil(w / 2)

  loading.value = true
  showPlaceholder.value = false

  const urls = getCoverUrls()
  const hasAnyUrl = urls.some(u => u !== null)

  if (!hasAnyUrl) {
    // 无 entry 封面 → 尝试回退到 fallbackArt
    if (props.fallbackArt) {
      const fallbackUrl = subsonicGetCoverArtUrl(props.fallbackArt, w)
      const img = await loadImage(fallbackUrl)
      if (img) {
        const srcSize = Math.min(img.width, img.height)
        const sx = (img.width - srcSize) / 2
        const sy = (img.height - srcSize) / 2
        ctx.drawImage(img, sx, sy, srcSize, srcSize, 0, 0, w, w)
        dataUrl.value = canvas.toDataURL('image/jpeg', 0.85)
        loading.value = false
        showPlaceholder.value = false
        return
      }
    }
    // 回退也失败 → 渐变占位
    drawPlaceholder(ctx, w)
    loading.value = false
    showPlaceholder.value = true
    return
  }

  const images = await Promise.all(urls.map(loadImage))

  // 检查是否全部失败
  const allFailed = images.every(img => img === null)
  if (allFailed) {
    drawPlaceholder(ctx, w)
    loading.value = false
    showPlaceholder.value = true
    return
  }

  // 绘制 2x2 拼贴
  for (let i = 0; i < 4; i++) {
    const col = i % 2
    const row = Math.floor(i / 2)
    const x = col * half
    const y = row * half
    const img = images[i]
    const dim = (i === 3 && w % 2 !== 0) ? half : half // 右下角处理奇数尺寸

    if (img) {
      // 裁剪为正方形填充
      const srcSize = Math.min(img.width, img.height)
      const sx = (img.width - srcSize) / 2
      const sy = (img.height - srcSize) / 2
      ctx.drawImage(img, sx, sy, srcSize, srcSize, x, y, half, half)
    } else {
      // 无图片象限用纯色填充
      ctx.fillStyle = ACCENT_COLORS[i] + '44'
      ctx.fillRect(x, y, half, half)
      // 中央小图标
      ctx.fillStyle = ACCENT_COLORS[i] + '88'
      ctx.beginPath()
      const cx = x + half / 2
      const cy = y + half / 2
      const r = half / 6
      ctx.arc(cx, cy, r, 0, Math.PI * 2)
      ctx.fill()
    }
  }

  dataUrl.value = canvas.toDataURL('image/jpeg', 0.85)
  loading.value = false
}

function drawPlaceholder(ctx, w) {
  // 渐变背景
  const grad = ctx.createLinearGradient(0, 0, w, w)
  grad.addColorStop(0, '#6366F1')
  grad.addColorStop(1, '#8B5CF6')
  ctx.fillStyle = grad
  ctx.fillRect(0, 0, w, w)
  dataUrl.value = ''
}

// ── 响应式重绘 ──

onMounted(() => { nextTick(drawCollage) })

watch(
  () => [props.entries, props.fallbackArt],
  () => { nextTick(drawCollage) },
  { deep: true },
)

watch(sizeNum, () => { nextTick(drawCollage) })
</script>

<style scoped>
.playlist-cover {
  position: relative;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: var(--radius-lg, 10px);
  background: linear-gradient(135deg, #6366F1, #8B5CF6);
}

.cover-canvas {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: var(--radius-lg, 10px);
}

.cover-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
