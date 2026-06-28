<template>
    <table class="clay-table">
      <thead>
        <tr>
          <th class="col-idx">#</th>
          <th class="col-cv"></th>
          <th class="col-main">{{ t('album.cols.name') }} / {{ t('album.cols.artist') }}</th>
          <th class="col-type">类型</th>
          <th class="col-year">{{ t('album.cols.year') }}</th>
          <th class="col-num">{{ t('album.cols.songCount') }}</th>
          <th class="col-act"></th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="(row, idx) in albums"
          :key="row.id"
          class="clay-row"
          @dblclick="$emit('play', row)"
        >
          <td class="col-idx">{{ startIdx + idx }}</td>

          <td class="col-cv">
            <img v-if="row.coverArt || row.id" :src="cover(row, 64)" class="cv-img" />
            <span v-else class="dash">—</span>
          </td>

          <td class="col-main">
            <span class="main-name link" @click="goDetail(row)">{{ row.name || '—' }}</span>
            <span v-if="row.artistId" class="main-artist link" @click.stop="goArtist(row)">{{ row.artist || '—' }}</span>
            <span v-else class="main-artist">{{ row.artist || '—' }}</span>
          </td>

          <td class="col-type">
            <span v-if="row.genre" class="badge badge-subtle">{{ row.genre }}</span>
            <span v-else class="dash">—</span>
          </td>

          <td class="col-year">
            <span v-if="row.year" class="badge badge-accent">{{ row.year }}</span>
            <span v-else class="dash">—</span>
          </td>

          <td class="col-num">{{ row.songCount ?? '—' }}</td>

          <td class="col-act">
            <div class="actions">
              <button class="act-btn act-play" @click.stop="$emit('play', row)" title="播放">
                <n-icon :size="15"><PlayOutline /></n-icon>
              </button>
              <button class="act-btn act-fav" @click.stop="$emit('toggleFav', row)" title="收藏">
                <n-icon :size="14" :color="isStarred(row.id) ? '#EF4444' : undefined">
                  <Heart v-if="isStarred(row.id)" />
                  <HeartOutline v-else />
                </n-icon>
              </button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { PlayOutline, HeartOutline, Heart } from '@vicons/ionicons5'
import { subsonicGetCoverArtUrl } from '@/api/playback/subsonic.js'

const router = useRouter()
const { t } = useI18n()

const props = defineProps({
  albums: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 60 },
  isStarred: { type: Function, default: () => false },
})

defineEmits(['play', 'toggleFav'])

const startIdx = computed(() => (props.page - 1) * props.pageSize + 1)

function cover(row, size) {
  const artId = row.coverArt || row.id
  return artId ? subsonicGetCoverArtUrl(artId, size) : ''
}

function goDetail(row) {
  if (row.id) router.push(`/player/albums/${row.id}`)
}
function goArtist(row) {
  if (row.artistId) router.push(`/player/artists/${row.artistId}`)
}
</script>

<style scoped>
/* ═══════════════════════════════════════════════════════════════
   Claymorphism Table — 双层阴影凸起行
   ═══════════════════════════════════════════════════════════════ */

/* ── 表格基础 ── */
.clay-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  table-layout: fixed;
}

/* 列宽 */
.col-idx   { width: 36px;  text-align: center; }
.col-cv    { width: 45px;  text-align: center; }
.col-type  { width: 56px;  text-align: center; }
.col-year  { width: 56px;  text-align: center; }
.col-num   { width: 56px;  text-align: center; }
.col-act   { width: 72px;  text-align: center; }
.col-main  { text-align: left; }

/* ── 表头：凸起横条（convex extrude）── */
.clay-table th {
  background: var(--gradient-button);
  box-shadow:
    var(--effect-button-inner),
    2px 2px 6px rgba(22 163 74 / 0.06),
    -1px -1px 3px rgba(255 255 255 / 0.3);
  border-bottom: 2px solid var(--color-border);
  font-weight: 600;
  font-size: 12px;
  letter-spacing: 0.3px;
  color: var(--color-text-secondary);
  padding: 6px 10px;
  vertical-align: middle;
}
.clay-table th:first-child { border-radius: 10px 0 0 10px; }
.clay-table th:last-child  { border-radius: 0 10px 10px 0; }

/* ── 数据行：凸起药丸（convex pill）── */
.clay-table td {
  background: var(--gradient-table-row);
  box-shadow:
    var(--effect-card-inner),
    2px 2px 6px rgba(22 163 74 / 0.05),
    -1px -1px 3px rgba(255 255 255 / 0.3);
  border: none;
  border-bottom: 1px solid var(--color-border-light);
  padding: 6px 10px;
  font-size: 13px;
  color: var(--color-text);
  vertical-align: middle;
  transition: transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1),
              box-shadow 0.2s ease;
}
.clay-table td:first-child { border-radius: 12px 0 0 12px; }
.clay-table td:last-child  { border-radius: 0 12px 12px 0; }

/* hover：弹簧抬升 + 阴影加深 */
.clay-table tbody tr:hover td {
  background: var(--gradient-card);
  box-shadow:
    var(--effect-card-inner),
    3px 4px 12px rgba(22 163 74 / 0.1),
    -1px -1px 4px rgba(255 255 255 / 0.35);
  transform: translateY(-2px);
}

/* ── 暗色主题 ── */
:root.dark .clay-table th {
  box-shadow:
    var(--effect-button-inner),
    3px 3px 8px rgba(0 0 0 / 0.35);
}
:root.dark .clay-table td {
  box-shadow:
    var(--effect-card-inner),
    2px 3px 8px rgba(0 0 0 / 0.3);
}
:root.dark .clay-table tbody tr:hover td {
  box-shadow:
    var(--effect-card-inner),
    4px 6px 18px rgba(0 0 0 / 0.5);
}

/* ── 封面缩略图 ── */
.cv-img {
  width: 42px; height: 42px;
  border-radius: 7px;
  object-fit: cover;
  display: block;
  margin: 0 auto;
}

/* ── 合并列：专辑名 + 艺术家 ── */
.col-main {
  line-height: 1.4;
}
.main-name {
  display: block;
  color: var(--ct-text);
  font-weight: 600;
  font-size: 13px;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.main-name:hover { color: var(--ct-accent); }
.main-artist {
  display: block;
  color: var(--ct-text-3);
  font-size: 11px;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.main-artist.link {
  cursor: pointer;
}
.main-artist.link:hover {
  color: var(--ct-accent);
}

/* ── 徽章 ── */
.badge {
  display: inline-block;
  font-size: 11px;
  padding: 1px 7px;
  border-radius: 5px;
  font-weight: 500;
}
.badge-accent {
  color: var(--ct-accent);
  background: rgba(var(--ct-accent-rgb) / 0.1);
  letter-spacing: 0.5px;
}
.badge-subtle {
  color: var(--ct-text-3);
  background: var(--ct-bg-secondary);
}

/* ── 占位符 ── */
.dash {
  font-size: 12px;
  color: var(--ct-text-3);
}

/* 序号等宽数字 */
.col-idx {
  font-size: 12px;
  color: var(--ct-text-3);
  font-variant-numeric: tabular-nums;
}

/* ── 操作按钮 ── */
.actions {
  display: flex;
  gap: 5px;
  align-items: center;
  justify-content: center;
}
.act-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px; height: 30px;
  border-radius: 50%;
  border: none;
  cursor: pointer;
  transition: transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1),
              opacity 0.15s,
              box-shadow 0.15s;
  opacity: 0.7;
}
.act-btn:hover {
  opacity: 1;
  transform: scale(1.12);
}
.act-btn:active {
  transform: scale(0.92);
}
.act-play {
  background: var(--ct-accent);
  color: #fff;
}
.act-fav {
  background: transparent;
  color: var(--ct-text-3);
}
.act-fav:hover { color: #EF4444; }
</style>
