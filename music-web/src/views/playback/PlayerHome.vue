<template>
  <div class="player-home">
    <!-- ═══ Hero：欢迎 + 搜索 + 随机播放 ═══ -->
    <div class="home-hero">
      <div class="hero-text">
        <p class="hero-greeting">{{ $t('dashboard.welcome') }}</p>
        <h1 class="hero-name">{{ username }}</h1>
      </div>
      <div class="hero-actions">
        <n-input
          v-model:value="searchQuery"
          :placeholder="$t('dashboard.quickSearch')"
          size="large"
          clearable
          round
          class="hero-search"
          @keyup.enter="doSearch"
        >
          <template #prefix><n-icon :size="20"><SearchOutline /></n-icon></template>
        </n-input>
        <n-button size="large" round type="primary" class="hero-shuffle-btn" @click="randomPlayAll">
          <template #icon><n-icon :size="20"><ShuffleOutline /></n-icon></template>
          {{ $t('dashboard.shortcuts.randomPlay') }}
        </n-button>
      </div>
    </div>

    <!-- ═══ 统计卡片 ═══ -->
    <section class="stats-row">
      <div
        v-for="stat in stats"
        :key="stat.key"
        class="stat-card"
        :style="{ '--stat-color': stat.color, '--stat-rgb': stat.rgb }"
        @click="goStat(stat.key)"
      >
        <div class="stat-icon-wrap">
          <n-icon :size="24"><component :is="stat.icon" /></n-icon>
        </div>
        <div class="stat-body">
          <span class="stat-value">{{ fmtNum(stat.value) }}</span>
          <span class="stat-label">{{ stat.label }}</span>
        </div>
      </div>
    </section>

    <!-- ═══ 加载状态 ═══ -->
    <n-spin :show="library.loading" size="medium">
      <div class="home-sections">
        <!-- ── 推荐歌曲 ── -->
        <section class="home-section">
          <div class="section-head">
            <h2 class="section-title">
              <n-icon :size="20" color="var(--ct-accent)"><MusicalNotesOutline /></n-icon>
              {{ $t('dashboard.randomPicks') }}
            </h2>
            <div class="section-actions">
              <n-button size="small" text @click="refreshRecommend">
                <template #icon><n-icon :size="16"><RefreshOutline /></n-icon></template>
                {{ $t('dashboard.refreshRecommend') }}
              </n-button>
              <n-button size="small" round type="primary" @click="playAllRecommend">
                <template #icon><n-icon :size="16"><PlayOutline /></n-icon></template>
                {{ $t('dashboard.playAll') }}
              </n-button>
            </div>
          </div>
          <div v-if="library.randomPicks.length" class="section-grid">
            <SongCard
              v-for="song in library.randomPicks.slice(0, 10)"
              :key="song.id"
              :song="song"
              :size="160"
              @play="playSong(song)"
            />
          </div>
          <div v-else class="section-empty">
            <n-icon :size="36" color="var(--ct-text-3)"><MusicalNotesOutline /></n-icon>
            <span>{{ $t('player.noData') }}</span>
          </div>
        </section>

        <!-- ── 最新添加 ── -->
        <section class="home-section">
          <div class="section-head">
            <h2 class="section-title">
              <n-icon :size="20" color="var(--ct-accent)"><TimeOutline /></n-icon>
              {{ $t('dashboard.newestSongs') }}
            </h2>
            <div class="section-actions">
              <n-button size="small" round type="primary" @click="playAllNewest">
                <template #icon><n-icon :size="16"><PlayOutline /></n-icon></template>
                {{ $t('dashboard.playAll') }}
              </n-button>
            </div>
          </div>
          <div v-if="library.newestSongs.length" class="section-grid">
            <SongCard
              v-for="song in library.newestSongs.slice(0, 10)"
              :key="song.id"
              :song="song"
              :size="160"
              @play="playSong(song)"
            />
          </div>
          <div v-else class="section-empty">
            <n-icon :size="36" color="var(--ct-text-3)"><MusicalNotesOutline /></n-icon>
            <span>{{ $t('player.noData') }}</span>
          </div>
        </section>

        <!-- ── 最新专辑 ── -->
        <section class="home-section">
          <div class="section-head">
            <h2 class="section-title">
              <n-icon :size="20" color="var(--ct-accent)"><DiscOutline /></n-icon>
              {{ $t('dashboard.newestAlbums') }}
            </h2>
            <n-button text type="primary" @click="$router.push('/player/albums')">
              {{ $t('dashboard.viewAll') }}
              <template #icon><n-icon :size="14"><ArrowForwardOutline /></n-icon></template>
            </n-button>
          </div>
          <div v-if="library.recentlyAdded.length" class="section-grid">
            <AlbumCard
              v-for="album in library.recentlyAdded.slice(0, 6)"
              :key="album.id"
              :album="album"
              :size="160"
              @click="goAlbum(album.id)"
            />
          </div>
          <div v-else class="section-empty">
            <n-icon :size="36" color="var(--ct-text-3)"><DiscOutline /></n-icon>
            <span>{{ $t('player.noData') }}</span>
          </div>
        </section>

        <!-- ── 热门艺术家 ── -->
        <section class="home-section">
          <div class="section-head">
            <h2 class="section-title">
              <n-icon :size="20" color="var(--ct-accent)"><PeopleOutline /></n-icon>
              {{ $t('dashboard.popularArtists') }}
            </h2>
            <n-button text type="primary" @click="$router.push('/player/artists')">
              {{ $t('dashboard.viewAll') }}
              <template #icon><n-icon :size="14"><ArrowForwardOutline /></n-icon></template>
            </n-button>
          </div>
          <div v-if="library.topArtists.length" class="section-grid">
            <ArtistCard
              v-for="artist in library.topArtists.slice(0, 6)"
              :key="artist.id"
              :artist="artist"
              :size="160"
              @click="goArtist(artist.id)"
            />
          </div>
          <div v-else class="section-empty">
            <n-icon :size="36" color="var(--ct-text-3)"><PeopleOutline /></n-icon>
            <span>{{ $t('player.noData') }}</span>
          </div>
        </section>
      </div>
    </n-spin>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  SearchOutline, RefreshOutline, MusicalNotesOutline,
  PeopleOutline, DiscOutline, ListOutline,
  ShuffleOutline, TimeOutline, PlayOutline, ArrowForwardOutline,
} from '@vicons/ionicons5'
import { useLibraryStore } from '@/store/playback/library.js'
import { usePlayerStore } from '@/store/playback/player.js'
import { useAuthStore } from '@/store/auth.js'
import AlbumCard from '@/components/playback/AlbumCard.vue'
import ArtistCard from '@/components/playback/ArtistCard.vue'
import SongCard from '@/components/playback/SongCard.vue'

const { t } = useI18n()
const router = useRouter()
const library = useLibraryStore()
const player = usePlayerStore()
const auth = useAuthStore()

const searchQuery = ref('')
const username = computed(() => auth.user?.username || '')

const stats = computed(() => [
  { key: 'artists',   icon: PeopleOutline,        value: library.artistCount,   label: t('dashboard.stats.artists'),   color: '#22C55E', rgb: '34,197,94' },
  { key: 'albums',    icon: DiscOutline,           value: library.albumCount,    label: t('dashboard.stats.albums'),    color: '#3B82F6', rgb: '59,130,246' },
  { key: 'songs',     icon: MusicalNotesOutline,   value: library.songCount,     label: t('dashboard.stats.songs'),     color: '#F59E0B', rgb: '245,158,11' },
  { key: 'playlists', icon: ListOutline,           value: library.playlistCount, label: t('dashboard.stats.playlists'), color: '#EF4444', rgb: '239,68,68' },
])

function fmtNum(n) {
  if (n == null) return '—'
  if (n >= 10000) return (n / 10000).toFixed(1).replace(/\.0$/, '') + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k'
  return String(n)
}

function goStat(key) { router.push(`/player/${key}`) }
function goAlbum(id) { router.push(`/player/albums/${id}`) }
function goArtist(id) { router.push(`/player/artists/${id}`) }

async function randomPlayAll() {
  await library.loadRandomSongs(50)
  if (library.songs.length > 0) player.play(library.songs[0])
}

async function refreshRecommend() {
  await library.loadRandomSongs(20)
}

function doSearch() {
  const q = searchQuery.value.trim()
  if (!q) return
  router.push(`/player/search?q=${encodeURIComponent(q)}`)
}

function playSong(song) {
  if (song.path || song.filePath) player.play(song)
}

/** 播放一组歌曲的第一首 */
function playFirstOf(list) {
  if (list && list.length > 0) player.play(list[0])
}

function playAllRecommend() { playFirstOf(library.randomPicks) }
function playAllNewest() { playFirstOf(library.newestSongs) }

onMounted(() => { library.loadDashboard() })
</script>

<style scoped>
.player-home {
  width: min(100% - var(--content-padding-x) * 2, var(--content-max-width));
  margin: 0 auto;
  padding: 32px 0 48px;
}

/* ═══════════════════════════════════════════════
   Hero
   ═══════════════════════════════════════════════ */
.home-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 32px;
  margin-bottom: 36px;
  flex-wrap: wrap;
}

.hero-text { flex-shrink: 0; }

.hero-greeting {
  font-size: var(--text-md);
  font-weight: 400;
  color: var(--ct-text-3);
  margin: 0 0 4px;
  letter-spacing: 0.02em;
}

.hero-name {
  font-size: var(--text-3xl);
  font-weight: 700;
  margin: 0;
  color: var(--ct-text);
  letter-spacing: -1px;
  line-height: 1.1;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  max-width: 560px;
  min-width: 280px;
}

.hero-search { flex: 1; }
.hero-shuffle-btn { flex-shrink: 0; }

/* ═══════════════════════════════════════════════
   Stats
   ═══════════════════════════════════════════════ */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 28px;
}

.stat-card {
  --stat-color: var(--ct-accent);
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 18px;
  border-radius: var(--radius-lg);
  border: var(--border-width-default) solid var(--ct-border);
  background: var(--gradient-card);
  box-shadow: var(--effect-card-inner), var(--effect-card-outer);
  cursor: pointer;
  transition:
    transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1),
    box-shadow var(--transition-base),
    border-color var(--transition-base);
  user-select: none;
  position: relative;
  overflow: hidden;
}

.stat-card::before {
  content: '';
  position: absolute;
  top: 0; left: 16px; right: 16px;
  height: 3px;
  border-radius: 0 0 3px 3px;
  background: var(--stat-color);
  opacity: 0.6;
  transition: opacity var(--transition-fast), box-shadow var(--transition-fast);
}

.stat-card:hover {
  transform: translateY(-4px);
  border-color: var(--stat-color);
  box-shadow:
    var(--effect-card-inner),
    var(--effect-card-outer),
    0 8px 24px rgba(var(--stat-rgb) / 0.15);
}

.stat-card:hover::before {
  opacity: 1;
  box-shadow: 0 0 12px rgba(var(--stat-rgb) / 0.4);
}

.stat-card:active {
  transform: scale(0.97);
  box-shadow: var(--effect-press);
}

.stat-icon-wrap {
  width: 48px; height: 48px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  background: rgba(var(--stat-rgb) / 0.1);
  color: var(--stat-color);
  transition: background var(--transition-fast), box-shadow var(--transition-fast);
}

.stat-card:hover .stat-icon-wrap {
  box-shadow: 0 0 16px rgba(var(--stat-rgb) / 0.2);
}

.stat-body { min-width: 0; }

.stat-value {
  display: block;
  font-size: 26px;
  font-weight: 700;
  color: var(--ct-text);
  font-feature-settings: 'tnum';
  letter-spacing: -1px;
  line-height: 1.1;
}

.stat-label {
  font-size: var(--text-xs);
  font-weight: 500;
  color: var(--ct-text-3);
  letter-spacing: 0.02em;
}

/* ═══════════════════════════════════════════════
   Sections
   ═══════════════════════════════════════════════ */
.home-sections {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.home-section {
  min-width: 0;
  padding: 20px;
  border-radius: var(--radius-xl);
  border: var(--border-width-default) solid var(--ct-border);
  background: var(--gradient-card);
  box-shadow: var(--effect-card-inner);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 8px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--text-md);
  font-weight: 600;
  margin: 0;
  color: var(--ct-text);
  letter-spacing: -0.3px;
}

.section-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ── 卡片网格（歌曲/专辑/艺术家通用）── */
.section-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 14px;
}

/* ── 空状态 ── */
.section-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 48px 16px;
  border-radius: var(--radius-md);
  background: var(--gradient-input);
  color: var(--ct-text-3);
  font-size: var(--text-xs);
}

/* ═══════════════════════════════════════════════
   Responsive
   ═══════════════════════════════════════════════ */
@media (max-width: 1200px) {
  .stats-row { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 900px) {
  .home-hero {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
  }
  .hero-actions { max-width: 100%; }
  .section-grid { grid-template-columns: repeat(3, 1fr); }
}

@media (max-width: 600px) {
  .player-home { padding: 20px 0 32px; }
  .hero-name { font-size: var(--text-2xl); }
  .hero-actions { flex-direction: column; }
  .stats-row { grid-template-columns: repeat(2, 1fr); gap: 10px; }
  .stat-card { padding: 14px 12px; gap: 10px; }
  .stat-value { font-size: 22px; }
  .stat-icon-wrap { width: 40px; height: 40px; }
  .section-grid { grid-template-columns: repeat(2, 1fr); }
  .section-head { flex-direction: column; align-items: flex-start; }
}
</style>
