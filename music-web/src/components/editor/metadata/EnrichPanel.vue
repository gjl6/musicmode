<template>
  <aside v-if="visible" class="enrich-panel">

    <div class="ep-top">
      <span class="ep-label">{{ t('enrich.labelSource') }}</span>
      <n-select
        v-model:value="enrich.provider.value"
        :options="providerOptions"
        size="small"
        multiple
        style="width: 180px"
      />
      <button class="ep-search-btn" :disabled="enrich.loading.value" @click="doSearch">
        {{ enrich.loading.value ? '...' : t('enrich.search') }}
      </button>
    </div>


    <div v-if="enrich.error.value" class="ep-error">{{ enrich.error.value }}</div>


    <div v-if="enrich.results.value.length > 0" class="ep-scroll-area">
      <div class="ep-table-wrap" ref="vScrollRef">
        <table class="ep-table">
          <thead>
            <tr>
              <th class="col-seq">#</th>
              <th class="col-cover">{{ t('enrich.colCover') }}</th>
              <th class="col-title">{{ t('enrich.colTitle') }}</th>
              <th class="col-artist">{{ t('enrich.colArtist') }}</th>
              <th class="col-album">{{ t('enrich.colAlbum') }}</th>
              <th class="col-year">{{ t('enrich.colYear') }}</th>
              <th class="col-lang">{{ t('enrich.colLanguage') }}</th>
              <th class="col-track">{{ t('enrich.colTrack') }}</th>
              <th class="col-disc">{{ t('enrich.colDisc') }}</th>
              <th class="col-album-year">{{ t('enrich.colAlbumYear') }}</th>
              <th class="col-company">{{ t('enrich.colCompany') }}</th>
              <th class="col-desc">{{ t('enrich.colDesc') }}</th>
              <th class="col-genre">{{ t('enrich.colGenre') }}</th>
              <th class="col-lyric">{{ t('enrich.colLyric') }}</th>
              <th class="col-action">
                {{ t('enrich.colAction') }}:
                <n-button size="tiny" @click="toggleOverwriteMode">
                  {{ enrich.overwriteMode.value === 'fill' ? t('enrich.fillOnly') : t('enrich.overwriteAll') }}
                </n-button>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, i) in enrich.results.value" :key="row.songId || i">
              <td class="col-seq">{{ i + 1 }}</td>
              <td class="col-cover clickable" @click="fillCover(row)">
                <img
                  v-if="getCover(row)"
                  :src="getCover(row)"
                  class="cover-thumb"
                  :title="t('enrich.clickToReplaceCover')"
                />
                <span v-else class="no-cover" :title="t('enrich.noCover')">—</span>
              </td>
              <td class="col-title clickable" :title="metaVal(row, 'song', 'title')" @click="fillField('song.title', metaVal(row, 'song', 'title'))">{{ metaVal(row, 'song', 'title') || '—' }}</td>
              <td class="col-artist clickable" :title="artistStr(row)" @click="fillArtist(row)">{{ artistStr(row) || '—' }}</td>
              <td class="col-album clickable" :title="metaVal(row, 'album', 'albumName')" @click="fillField('album.albumName', metaVal(row, 'album', 'albumName'))">{{ metaVal(row, 'album', 'albumName') || '—' }}</td>
              <td class="col-year clickable" @click="fillField('song.year', metaVal(row, 'song', 'year'))">{{ metaVal(row, 'song', 'year') || '—' }}</td>
              <td class="col-lang clickable" @click="fillField('song.language', getDetail(row, 'language'))">{{ getDetail(row, 'language') || '—' }}</td>
              <td class="col-track clickable" @click="fillField('song.trackNumber', metaVal(row, 'song', 'trackNumber'))">{{ metaVal(row, 'song', 'trackNumber') ?? '—' }}</td>
              <td class="col-disc clickable" @click="fillField('song.discNumber', metaVal(row, 'song', 'discNumber'))">{{ metaVal(row, 'song', 'discNumber') ?? '—' }}</td>
              <td class="col-album-year clickable" @click="fillField('album.albumYear', getDetail(row, 'year'))">{{ getDetail(row, 'year') || '—' }}</td>
              <td class="col-company clickable" :title="getDetail(row, 'company')" @click="fillField('album.company', getDetail(row, 'company'))">{{ getDetail(row, 'company') || '—' }}</td>
              <td class="col-desc clickable" :title="getDetail(row, 'description')" @click="fillField('album.introduction', getDetail(row, 'description'))">
                <span class="desc-text">{{ getDetail(row, 'description') || '—' }}</span>
              </td>
              <td class="col-genre clickable" :title="getDetail(row, 'genre')" @click="fillField('style.styleName', getDetail(row, 'genre'))">{{ getDetail(row, 'genre') || '—' }}</td>
              <td class="col-lyric clickable" @click="fillLyric(row)">
                <n-popover v-if="getDetail(row, '_lyric')" trigger="hover" placement="left" style="max-width: 360px; max-height: 440px;">
                  <template #trigger>
                    <span class="lyric-indicator">{{ t('enrich.hasLyric') }}</span>
                  </template>
                  <div class="lyric-pop">{{ enrich.cleanLyric(getDetail(row, '_lyric')) }}</div>
                </n-popover>
                <span v-else class="no-lyric">—</span>
              </td>
              <td class="col-action">
                <n-button size="tiny" quaternary @click="enrich.loadDetail(i)" :title="t('enrich.loadDetail')">…</n-button>
                <n-button size="tiny" type="primary" @click="fillAll(row)">{{ t('enrich.apply') }}</n-button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="ep-hscroll" ref="hScrollRef">
        <div class="ep-hscroll-inner"></div>
      </div>
    </div>


    <div v-else-if="!enrich.loading.value && searched" class="ep-empty">
      {{ t('enrich.noResults') }}
    </div>

  </aside>
</template>

<script setup>
import { computed, ref, watch, onMounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { NSelect, NButton, NPopover, NRadioGroup, NRadio } from 'naive-ui'
import { useEnrich } from '@/composables/editor/useEnrich.js'
import { useEditStore } from '@/store/editor/edit.js'
import { songField, albumField, styleField, lyricField, artistNames } from '@/utils/musicMeta.js'
import { useArtistConfig } from '@/composables/editor/useArtistConfig.js'
import { getCoverUrl } from '@/utils/mediaUrl.js'

const { t } = useI18n()

const props = defineProps({
  visible: { type: Boolean, default: false },
})

const editStore = useEditStore()
const enrich = useEnrich()
const { joinSeparator } = useArtistConfig()
const searched = ref(false)


function artistStr(meta) {
  return artistNames(meta, joinSeparator.value || undefined)
}

const vScrollRef = ref(null)
const hScrollRef = ref(null)

function setupScrollSync() {
  const v = vScrollRef.value
  const h = hScrollRef.value
  if (!v || !h) return
  h.onscroll = () => { v.scrollLeft = h.scrollLeft }
  v.onscroll = () => { h.scrollLeft = v.scrollLeft }
}

const providerOptions = computed(() =>
  enrich.providerList.value.map(p => ({ label: p.label, value: p.name }))
)

onMounted(() => {
  enrich.loadProviders()
  setupScrollSync()
})

watch(() => enrich.results.value.length, async () => {
  await nextTick()
  setupScrollSync()
})

let _searchTimer = null
watch(() => props.visible, (v) => {
  clearTimeout(_searchTimer)
  if (v) {
    _searchTimer = setTimeout(() => doSearch(), 300)
  }
})

function doSearch() {
  if (!editStore.currentMeta?.song?.title) return
  searched.value = true
  enrich.search()
}


function metaVal(row, group, field) {
  const fn = { song: songField, album: albumField, style: styleField, lyric: lyricField }[group]
  return fn ? fn(row, field) : null
}

function getDetail(row, field) {
  if (field === '_lyric') return lyricField(row, 'content')
  return songField(row, field) || albumField(row, field) || styleField(row, field)
}

function getCover(row) {
  return getCoverUrl(songField(row, 'coverPath'))
}


function currentVal(path) {
  return path.split('.').reduce((o, k) => (o || {})[k], editStore.currentMeta)
}

function isEmpty(val) {
  return val == null || val === '' || val === 0
}

function toggleOverwriteMode() {
  enrich.overwriteMode.value = enrich.overwriteMode.value === 'fill' ? 'overwrite' : 'fill'
}

function fillField(path, value) {
  if (value == null || value === '') return
  editStore.setField(path, String(value))
}

function fillArtist(row) {
  const hasArtists = row.artists && row.artists.length > 0
  if (!hasArtists) {
    fillField('artist.artistName', artistStr(row))
    return
  }
    if (enrich.overwriteMode.value === 'overwrite' || !editStore.currentMeta.artists || editStore.currentMeta.artists.length === 0 || !editStore.currentMeta.artists[0].artistName) {
    const mapped = row.artists.map(a => ({
      artistName: a.artistName || '', artistCover: a.artistCover || '',
      introduction: a.introduction || '', gender: a.gender ?? 0,
      country: a.country || '',
    }))
    editStore.currentMeta.artists = mapped
    Object.assign(editStore.currentMeta.artist, mapped[0])
    editStore.currentMeta.activeArtistIndex = 0
  }
}

const FIELD_MAP = [
  ['song.title',       'title'],
  ['artist.artistName', 'artistName'],
  ['album.albumName',   'albumName'],
  ['song.year',         'year'],
  ['song.trackNumber',  'trackNumber'],
  ['song.discNumber',   'discNumber'],
]

const ALBUM_FIELD_MAP = [
  ['song.language',      'language'],
  ['album.albumYear',     'year'],
  ['album.company',       'company'],
  ['album.introduction',  'description'],
  ['style.styleName',     'genre'],
  ['artist.artistName',   'singerName'],
]

function fillLyric(row) {
  const lyric = row.lyrics?.[0]?.content
  if (!lyric) return
  editStore.setField('lyric.content', enrich.cleanLyric(lyric))
}

function fillCover(row) {
  const p = row.songs?.[0]?.coverPath
  if (!p) return
  editStore.setField('song.coverPath', p)
}

function fillAll(row) {
    const hasArtists = row.artists && row.artists.length > 0
  if (hasArtists) {
    if (enrich.overwriteMode.value === 'overwrite' || !editStore.currentMeta.artists || editStore.currentMeta.artists.length === 0 || !editStore.currentMeta.artists[0].artistName) {
      const mapped = row.artists.map(a => ({
        artistName: a.artistName || '', artistCover: a.artistCover || '',
        introduction: a.introduction || '', gender: a.gender ?? 0,
        country: a.country || '',
      }))
      editStore.currentMeta.artists = mapped
      editStore.currentMeta.activeArtistIndex = 0
      Object.assign(editStore.currentMeta.artist, mapped[0])
    }
  }

    for (const [path, key] of FIELD_MAP) {
    if (path === 'artist.artistName' && hasArtists) continue
    const val = metaVal(row, 'song', key) || metaVal(row, 'artist', key) || metaVal(row, 'album', key)
    if (val == null || val === '') continue
    const strVal = String(val)
    if (enrich.overwriteMode.value === 'fill' && !isEmpty(currentVal(path))) continue
    editStore.setField(path, strVal)
  }
    for (const [path, key] of ALBUM_FIELD_MAP) {
    if (path === 'artist.artistName' && hasArtists) continue
    const val = metaVal(row, 'song', key) || metaVal(row, 'album', key) || metaVal(row, 'style', key) || metaVal(row, 'artist', key)
    if (val == null || val === '') continue
    const strVal = String(val)
    if (enrich.overwriteMode.value === 'fill' && !isEmpty(currentVal(path))) continue
    editStore.setField(path, strVal)
  }
    const lyric = row.lyrics?.[0]?.content
  if (lyric) {
    if (enrich.overwriteMode.value === 'overwrite' || isEmpty(currentVal('lyric.content'))) {
      editStore.setField('lyric.content', enrich.cleanLyric(lyric))
    }
  }
    const coverPath = row.songs?.[0]?.coverPath
  if (coverPath) {
    if (enrich.overwriteMode.value === 'overwrite' || isEmpty(currentVal('song.coverPath'))) {
      editStore.setField('song.coverPath', coverPath)
    }
  }
}
</script>

<style scoped>
.enrich-panel {
  width: 600px;
  flex-shrink: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--ct-bg);
}


.ep-top {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--ct-border);
  flex-shrink: 0;
}
.ep-label {
  font-size: 12px;
  white-space: nowrap;
  color: var(--ct-text-2);
}
.ep-search-btn {
  padding: 4px 14px;
  border: none;
  border-radius: 999px;
  background: var(--color-secondary);
  color: var(--color-on-primary);
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}
.ep-search-btn:hover { background: var(--color-primary); }
.ep-search-btn:disabled { opacity: 0.6; cursor: not-allowed; }

.ep-error {
  padding: 6px 12px;
  font-size: 11px;
  color: var(--n-color-error);
  flex-shrink: 0;
}


.ep-scroll-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}


.ep-table-wrap {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  min-height: 0;
}

.ep-hscroll {
  flex-shrink: 0;
  height: 8px;
  overflow-x: auto;
  overflow-y: hidden;
}

.ep-hscroll-inner {
  height: 1px;
  min-width: 1400px;
}
.ep-table {
  border-collapse: collapse;
  font-size: 11px;
  white-space: nowrap;
  min-width: 1400px;
}
.ep-table th {
  position: sticky;
  top: 0;
  background: var(--ct-bg-secondary);
  padding: 6px 5px;
  text-align: left;
  font-weight: 600;
  border-bottom: 1px solid var(--ct-border);
  z-index: 1;
  font-size: 10px;
  letter-spacing: 0.02em;
  color: var(--ct-text-2);
}
.ep-table td {
  padding: 12px;
  border-bottom: 1px solid var(--ct-border);
  vertical-align: middle;
}
.ep-table tr:hover td { background: var(--ct-bg-secondary); }
.ep-table tr:hover td.col-action { background: var(--ct-bg-secondary); }


.col-seq { width: 28px; text-align: center; }
.col-cover { width: 40px; }
.col-title { width: 140px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.col-artist { width: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.col-album { width: 140px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.col-year { width: 55px; }
.col-lang { width: 55px; }
.col-track { width: 60px; }
.col-disc { width: 55px; }
.col-album-year { width: 75px; }
.col-company { width: 110px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.col-desc { width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.col-genre { width: 80px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.col-lyric { width: 50px; text-align: center; }
.col-action {
  width: 80px;
  text-align: center;
  position: sticky;
  right: 0;
  background: var(--ct-bg);
}
.ep-table th.col-action {
  z-index: 2;
}
.ep-table .clickable {
  text-decoration: none !important;
  cursor: pointer;
}


.clickable {
  cursor: pointer;
  text-decoration: underline;
  text-decoration-color: var(--n-color-primary);
  text-underline-offset: 2px;
  text-decoration-thickness: 1px;
}
.clickable:hover { color: var(--n-color-primary); }


.cover-thumb {
  width: 30px;
  height: 30px;
  border-radius: 4px;
  object-fit: cover;
}


.desc-text {
  display: block;
}


.lyric-indicator {
  color: var(--n-color-primary);
  cursor: pointer;
  font-size: 11px;
}
.no-lyric { color: var(--ct-text-3); }
.lyric-pop {
  font-size: 11px;
  line-height: 1.6;
  white-space: pre-wrap;
  max-height: 350px;
  overflow-y: auto;
}


.ep-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ct-text-2);
  font-size: 12px;
}

</style>
