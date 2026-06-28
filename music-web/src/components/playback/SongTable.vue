<template>
  <table class="clay-table">
    <thead>
      <tr>
        <th class="col-idx">#</th>
        <th class="col-cv"></th>
        <th class="col-main">{{ t('song.cols.title') }} / {{ t('song.cols.artist') }}</th>
        <th class="col-album">{{ t('song.cols.album') }}</th>
        <th class="col-fmt">{{ t('song.cols.format') }}</th>
        <th class="col-dur">{{ t('song.cols.duration') }}</th>
        <th class="col-size">{{ t('song.cols.size') }}</th>
        <th class="col-br">{{ t('song.cols.bitrate') }}</th>
        <th class="col-rate">{{ t('song.cols.rating') }}</th>
        <th class="col-act"></th>
      </tr>
    </thead>
    <tbody>
      <tr
        v-for="(row, idx) in songs"
        :key="row.id"
        class="clay-row"
        @dblclick="$emit('play', row)"
      >
        <td class="col-idx">{{ idx + 1 }}</td>

        <!-- 封面 -->
        <td class="col-cv">
          <img
            v-if="(row.coverArt || row.id)"
            :src="coverUrl(row)"
            class="cv-img"
          />
          <span v-else class="dash">—</span>
        </td>

        <!-- 标题 + 艺术家（合并列） -->
        <td class="col-main">
          <span class="main-name link" :title="row.title" @click.stop="goSong(row)">{{ row.title || '—' }}</span>
          <span
            v-if="row.artists && row.artists.length > 1"
            class="main-artist"
          >
            <span
              v-for="(a, i) in row.artists"
              :key="a.id"
              class="main-artist-chip"
              :class="{ link: a.id }"
              :title="a.name"
              @click.stop="goArtist(a.id)"
            >{{ a.name }}<span v-if="i < row.artists.length - 1"> / </span></span>
          </span>
          <span
            v-else-if="row.artistId"
            class="main-artist link"
            :title="row.displayArtist || row.artist"
            @click.stop="goArtist(row.artistId)"
          >{{ row.displayArtist || row.artist || '—' }}</span>
          <span v-else class="main-artist" :title="row.displayArtist || row.artist">{{ row.displayArtist || row.artist || '—' }}</span>
        </td>

        <!-- 专辑 -->
        <td class="col-album">
          <span
            v-if="row.albumId"
            class="link"
            :title="row.album"
            @click.stop="goAlbum(row)"
          >{{ row.album || '—' }}</span>
          <span v-else :title="row.album">{{ row.album || '—' }}</span>
        </td>

        <!-- 格式 -->
        <td class="col-fmt">
          <span
            v-if="row.suffix || row.fileFormat"
            class="badge badge-fmt"
            :class="'badge-fmt--' + (row.suffix || row.fileFormat || '').toString().toLowerCase()"
          >{{ (row.suffix || row.fileFormat || '').toString().toUpperCase() }}</span>
          <span v-else class="dash">—</span>
        </td>

        <!-- 时长 -->
        <td class="col-dur">{{ formatDuration(row.duration) }}</td>

        <!-- 大小 -->
        <td class="col-size">
          <span v-if="formatSize(row.size || row.fileSize)">
            {{ formatSize(row.size || row.fileSize).val }}<span class="unit">{{ formatSize(row.size || row.fileSize).unit }}</span>
          </span>
          <span v-else class="dash">—</span>
        </td>

        <!-- 比特率 -->
        <td class="col-br">
          <span v-if="formatBitrate(row.bitRate)">
            {{ formatBitrate(row.bitRate).val }}<span class="unit">{{ formatBitrate(row.bitRate).unit }}</span>
          </span>
          <span v-else class="dash">—</span>
        </td>

        <!-- 评分 -->
        <td class="col-rate" @click.stop>
          <StarRatingComp
            :rating="Number(row.userRating) || 0"
            :song-id="Number(row.id)"
            :size="12"
            @rated="(val) => $emit('rate', { songId: Number(row.id), rating: val })"
          />
        </td>

        <!-- 操作 -->
        <td class="col-act">
          <div class="actions">
            <button class="act-btn act-play" @click.stop="$emit('play', row)" title="播放">
              <n-icon :size="15"><PlayOutline /></n-icon>
            </button>
            <button class="act-btn act-fav" @click.stop="$emit('toggleFav', row)" title="收藏">
              <n-icon :size="14" :color="row._starred ? '#EF4444' : undefined">
                <Heart v-if="row._starred" />
                <HeartOutline v-else />
              </n-icon>
            </button>
            <button class="act-btn act-add" @click.stop="$emit('addToQueue', row)" title="添加到队列">
              <n-icon :size="14"><AddOutline /></n-icon>
            </button>
            <button class="act-btn act-pl" @click.stop="$emit('addToPlaylist', row)" title="添加到歌单">
              <n-icon :size="14"><ListOutline /></n-icon>
            </button>
          </div>
        </td>
      </tr>
    </tbody>
  </table>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { PlayOutline, HeartOutline, Heart, AddOutline, ListOutline } from '@vicons/ionicons5'
import { subsonicGetCoverArtUrl } from '@/api/playback/subsonic.js'
import StarRatingComp from '@/components/playback/StarRating.vue'

const router = useRouter()
const { t } = useI18n()

defineProps({
  songs: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
})

defineEmits(['play', 'addToQueue', 'toggleFav', 'rate', 'addToPlaylist'])

// ── 格式化 ──

function formatDuration(s) {
  if (!s || !isFinite(s)) return '—'
  const m = Math.floor(s / 60)
  const sec = Math.floor(s % 60)
  return `${m}:${String(sec).padStart(2, '0')}`
}

function formatSize(bytes) {
  if (!bytes || bytes === 0) return null
  const b = Number(bytes)
  if (b < 1048576) return { val: String(Math.round(b / 1024)), unit: 'KB' }
  return { val: (b / 1048576).toFixed(1), unit: 'MB' }
}

function formatBitrate(kbps) {
  if (!kbps || kbps === 0) return null
  return { val: String(kbps), unit: 'kbps' }
}

// ── 封面 ──

function coverUrl(row) {
  const artId = row.coverArt || row.id
  return artId ? subsonicGetCoverArtUrl(artId, 64) : ''
}

// ── 导航 ──

function goSong(row) {
  if (row.id) router.push(`/player/songs/${row.id}`)
}
function goArtist(artistId) {
  if (artistId) router.push(`/player/artists/${artistId}`)
}
function goAlbum(row) {
  if (row.albumId) router.push(`/player/albums/${row.albumId}`)
}
</script>

<style scoped>
/* ═══════════════════════════════════════════════════════════════
   Claymorphism Song Table — 9 列原生 table
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
.col-main  { text-align: left; }
.col-album { text-align: left; }
.col-fmt   { width: 44px;  text-align: center; }
.col-dur   { width: 46px;  text-align: center; }
.col-size  { width: 56px;  text-align: center; }
.col-br    { width: 58px;  text-align: center; }
.col-rate  { width: 90px;  text-align: center; }
.col-act   { width: 120px; text-align: center; }

/* 修正 th 默认居中对齐 */
th.col-dur, th.col-fmt, th.col-size, th.col-br, th.col-rate { text-align: center; }

/* ── 表头 ── */
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
  white-space: nowrap;
}
.clay-table th:first-child { border-radius: 10px 0 0 10px; }
.clay-table th:last-child  { border-radius: 0 10px 10px 0; }

/* ── 数据行 ── */
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
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.clay-table td:first-child { border-radius: 12px 0 0 12px; }
.clay-table td:last-child  { border-radius: 0 12px 12px 0; }

/* hover */
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

/* ── 合并列：标题 + 艺术家 ── */
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
/* 多艺术家 chip 模式 */
.main-artist:has(.main-artist-chip) {
  white-space: normal;
  line-height: 1.5;
}
.main-artist-chip {
  display: inline;
}
.main-artist-chip.link {
  cursor: pointer;
}
.main-artist-chip.link:hover {
  color: var(--ct-accent);
}
.main-artist.link {
  cursor: pointer;
}
.main-artist.link:hover {
  color: var(--ct-accent);
}

/* ── 可点击链接（专辑列）── */
.link {
  cursor: pointer;
}
.link:hover {
  color: var(--ct-accent);
}

/* ── 占位符 ── */
.dash {
  font-size: 12px;
  color: var(--ct-text-3);
}

/* 序号 */
.col-idx {
  font-size: 12px;
  color: var(--ct-text-3);
  font-variant-numeric: tabular-nums;
}

/* 时长/大小/比特率 */
.col-dur, .col-size, .col-br {
  font-size: 11px;
  color: var(--ct-text-3);
  font-variant-numeric: tabular-nums;
}
.unit {
  font-size: 9px;
  opacity: 0.7;
  margin-left: 1px;
}

/* ── 格式徽章 ── */
.badge {
  display: inline-block;
  font-size: 10px;
  font-weight: 600;
  padding: 1px 4px;
  border-radius: 3px;
  letter-spacing: 0.3px;
}
.badge-fmt { color: var(--ct-accent); background: rgb(var(--ct-accent-rgb) / 0.1); }
.badge-fmt--flac { color: #16A34A; background: rgba(22 163 74 / 0.1); }
.badge-fmt--wav  { color: #CA8A40; background: rgba(202 138 64 / 0.1); }
.badge-fmt--ape  { color: #EF4444; background: rgba(239 68 68 / 0.1); }
.badge-fmt--alac { color: #16A34A; background: rgba(22 163 74 / 0.1); }
.badge-fmt--aac  { color: #7C3AED; background: rgba(124 58 237 / 0.1); }
.badge-fmt--ogg  { color: #CA8A40; background: rgba(202 138 64 / 0.1); }

/* ── 操作按钮 ── */
.actions {
  display: flex;
  gap: 3px;
  align-items: center;
  justify-content: center;
}
.act-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px; height: 26px;
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
.act-add {
  background: transparent;
  color: var(--ct-text-3);
}
.act-add:hover { color: var(--ct-text); }
.act-pl {
  background: transparent;
  color: var(--ct-text-3);
}
.act-pl:hover { color: var(--ct-accent); }
</style>
