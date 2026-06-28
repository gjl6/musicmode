/**
 * useLibraryStore — 音乐库浏览状态管理
 *
 * 职责：
 *   1. 管理艺术家/专辑/歌曲/风格/歌单列表数据
 *   2. 管理浏览视图状态（网格/列表、排序、过滤）
 *   3. 管理分页和加载状态
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  subsonicGetArtists,
  subsonicGetRandomSongs,
  subsonicGetGenres, subsonicGetSongsByGenre,
  subsonicGetPlaylists, subsonicGetPlaylist,
  subsonicSearch,
  subsonicGetSongs,
  subsonicGetSongLetters,
} from '@/api/playback/subsonic.js'
import { getAlbums, getAlbumLetters, getAlbum } from '@/api/playback/album.js'
import { getArtist, getArtistAlbums, getArtistSongs, getTopArtists } from '@/api/playback/artist.js'
import { getGenres, getGenreLetters, getGenreSongs, updateStyle } from '@/api/playback/genre.js'

function unwrap(data, path) {
  if (!data) return null
  // Subsonic 响应嵌套在 'subsonic-response' 下
  const resp = data['subsonic-response'] || data
  if (!path) return resp
  return path.split('.').reduce((obj, key) => obj?.[key], resp)
}

export const useLibraryStore = defineStore('library', () => {
  // ============ 数据状态 ============

  const artists = ref([])
  const albums = ref([])
  const songs = ref([])
  const genres = ref([])
  const playlists = ref([])

  // ── 歌曲分页/字母导航 ──
  const songLetters = ref([])       // [{letter: "A", cnt: 120}, ...]
  const songTotal = ref(0)          // 当前字母下的歌曲总数
  const currentLetter = ref(null)   // 当前选中的字母（null=全部）
  const songSortType = ref('alphabetical')  // 'alphabetical' | 'newest'
  const songPage = ref(1)                  // 当前页码

  // ── 专辑字母导航 ──
  const albumLetters = ref([])       // [{letter: "A", cnt: 12}, ...]
  const albumTotal = ref(0)

  /** 单个实体详情缓存 */
  const artistDetail = ref(null)
  const artistAlbums = ref([])
  const artistSongs = ref([])
  const albumDetail = ref(null)
  const playlistDetail = ref(null)
  const albumSongs = ref([])

  // ── 艺术家字母导航 ──
  const artistLetters = ref([])       // [{letter: "A", cnt: 12}, ...]
  const artistTotal = ref(0)

  // ── 艺术家详情分区加载 ──
  const artistAlbumTotal = ref(0)
  const artistSongTotal = ref(0)
  const artistAlbumsLoading = ref(false)
  const artistSongsLoading = ref(false)

  // ── 风格列表 ──
  const genreTotal = ref(0)
  const genreLetters = ref([])
  const genreSortType = ref('name')

  // ── 风格详情 ──
  const genreSongs = ref([])
  const genreSongTotal = ref(0)
  const genreSongsLoading = ref(false)
  const genreDetail = ref(null)  // { name, image, description }

  // ============ 统计 ============

  const artistCount = ref(0)
  const albumCount = ref(0)
  const songCount = ref(0)
  const playlistCount = computed(() => playlists.value.length)

  // ============ UI 状态 ============

  const loading = ref(false)
  const currentView = ref('grid')           // 'grid' | 'list'
  const albumSortType = ref('newest')       // 'newest' | 'alphabetical' | 'random' | 'byYear' | 'recent'
  const page = ref(0)
  const pageSize = ref(50)
  const totalCount = ref(0)

  // ============ 仪表盘数据 ============

  const recentlyAdded = ref([])
  const randomPicks = ref([])
  const newestSongs = ref([])
  const topArtists = ref([])

  async function loadDashboard() {
    loading.value = true
    try {
      const [albumData, randomData, playlistData, newestData, topArtistData] = await Promise.all([
        getAlbums({ sort: 'newest', limit: 12 }),
        subsonicGetRandomSongs(20),
        subsonicGetPlaylists(),
        subsonicGetSongs({ sort: 'newest', count: 12 }),
        getTopArtists(12),
      ])
      recentlyAdded.value = albumData?.albums || []
      const randomSongs = unwrap(randomData, 'randomSongs.song') || []
      randomPicks.value = Array.isArray(randomSongs) ? randomSongs : []
      playlists.value = unwrap(playlistData, 'playlists.playlist') || []
      const newSongs = unwrap(newestData, 'songs') || {}
      newestSongs.value = newSongs.song || []
      topArtists.value = topArtistData?.artists || []
    } catch (e) {
      console.error('[library] 仪表盘加载失败:', e)
    } finally {
      loading.value = false
    }
  }

  /** 轻量统计查询 — 仅获取各实体总量，不加载完整列表 */
  async function loadStats() {
    try {
      const [artistData, albumData, songData] = await Promise.all([
        subsonicGetArtists({ count: 1 }),
        getAlbums({ limit: 1 }),
        subsonicGetSongs({ count: 1 }),
      ])
      const artistResp = unwrap(artistData, 'artists') || {}
      artistCount.value = artistResp.total || 0
      const albumResp = albumData || {}
      albumCount.value = albumResp.total || 0
      const songResp = unwrap(songData, 'songs') || {}
      songCount.value = songResp.total || 0
    } catch (e) {
      console.error('[library] 统计加载失败:', e)
    }
  }

  // ============ 艺术家 ============

  async function loadArtists({ letter = null, sort = null, count = 50, offset = 0 } = {}) {
    loading.value = true
    try {
      const params = { count, offset }
      if (letter) params.letter = letter
      if (sort) params.sort = sort
      const data = await subsonicGetArtists(params)
      const resp = unwrap(data, 'artists') || {}
      const index = resp.index || []
      // 展开所有首字母分组为扁平的艺术家列表
      const all = []
      for (const group of (Array.isArray(index) ? index : [])) {
        const list = group.artist || []
        for (const a of list) all.push(a)
      }
      artists.value = all
      artistTotal.value = resp.total || 0
      // 同步全局计数（无过滤时用 total，否则保留上次已知值）
      if (!letter && resp.total != null) artistCount.value = resp.total
      // 仅在无过滤时更新字母统计
      if (!letter) {
        artistLetters.value = resp.letters || []
      }
    } catch (e) {
      console.error('[library] 艺术家列表加载失败:', e)
    } finally {
      loading.value = false
    }
  }

  async function loadArtist(id) {
    try {
      const res = await getArtist(id)
      artistDetail.value = res?.artist || null
    } catch (e) {
      console.error('[library] 艺术家详情加载失败:', e)
    }
  }

  async function loadArtistAlbums(id, { sort = 'newest', letter = null, limit = 20, offset = 0 } = {}) {
    artistAlbumsLoading.value = true
    try {
      const params = { sort, limit, offset }
      if (letter) params.letter = letter
      const res = await getArtistAlbums(id, params)
      artistAlbums.value = res?.albums || []
      artistAlbumTotal.value = res?.total || 0
    } catch (e) {
      console.error('[library] 艺术家专辑加载失败:', e)
    } finally {
      artistAlbumsLoading.value = false
    }
  }

  async function loadArtistSongs(id, { letter = null, sort = 'alphabetical', limit = 50, offset = 0 } = {}) {
    artistSongsLoading.value = true
    try {
      const params = { sort, limit, offset }
      if (letter) params.letter = letter
      const res = await getArtistSongs(id, params)
      artistSongs.value = res?.songs || []
      artistSongTotal.value = res?.total || 0
    } catch (e) {
      console.error('[library] 艺术家歌曲加载失败:', e)
    } finally {
      artistSongsLoading.value = false
    }
  }

  // ============ 专辑 ============

  async function loadAlbums({ sort, letter, starred, offset = 0, limit = 60 } = {}) {
    loading.value = true
    if (sort) albumSortType.value = sort
    try {
      const res = await getAlbums({
        sort: sort || albumSortType.value,
        letter,
        starred,
        limit,
        offset,
      })
      albums.value = res?.albums || []
      albumTotal.value = res?.total || 0
      // 同步全局计数（无过滤时用 total）
      if (!letter && !starred && res?.total != null) albumCount.value = res.total
    } catch (e) {
      console.error('[library] 专辑列表加载失败:', e)
    } finally {
      loading.value = false
    }
  }

  /** 加载各首字母专辑数量统计 */
  async function loadAlbumLetters() {
    try {
      const res = await getAlbumLetters()
      albumLetters.value = res?.letters || []
    } catch (e) {
      console.error('[library] 专辑字母统计加载失败:', e)
    }
  }

  async function loadAlbum(id) {
    try {
      const res = await getAlbum(id)
      albumDetail.value = res?.album || null
      albumSongs.value = res?.songs || []
    } catch (e) {
      console.error('[library] 专辑详情加载失败:', e)
    }
  }

  // ============ 风格 ============

  /** 旧版 Subsonic 兼容：一次性加载全部风格 */
  async function loadAllGenres() {
    loading.value = true
    try {
      const data = await subsonicGetGenres()
      genres.value = unwrap(data, 'genres.genre') || []
    } catch (e) {
      console.error('[library] 风格列表加载失败:', e)
    } finally {
      loading.value = false
    }
  }

  /** 分页加载风格列表，支持按首字母过滤 + 排序 */
  async function loadGenres({ sort = 'name', letter = null, count = 60, offset = 0 } = {}) {
    loading.value = true
    genreSortType.value = sort
    try {
      const res = await getGenres({ sort, letter, limit: count, offset })
      genres.value = res?.genres || []
      genreTotal.value = res?.total || 0
    } catch (e) {
      console.error('[library] 风格列表加载失败:', e)
    } finally {
      loading.value = false
    }
  }

  /** 加载风格字母统计 */
  async function loadGenreLetters() {
    try {
      const res = await getGenreLetters()
      genreLetters.value = res?.letters || []
    } catch (e) {
      console.error('[library] 风格字母统计加载失败:', e)
    }
  }

  /** 加载风格下的歌曲（分页 + 排序 + 字母过滤） */
  async function loadGenreDetail(name, { letter = null, sort = 'alphabetical', count = 50, offset = 0 } = {}) {
    genreSongsLoading.value = true
    try {
      const res = await getGenreSongs(name, { letter, sort, limit: count, offset })
      genreSongs.value = res?.songs || []
      genreSongTotal.value = res?.total || 0
      if (res?.genre) genreDetail.value = res.genre
    } catch (e) {
      console.error('[library] 风格歌曲加载失败:', e)
    } finally {
      genreSongsLoading.value = false
    }
  }

  /** Subsonic 兼容：一次性加载（用于旧 modal 或 fallback） */
  async function loadSongsByGenre(genre, count = 50, offset = 0) {
    loading.value = true
    try {
      const data = await subsonicGetSongsByGenre(genre, count, offset)
      songs.value = unwrap(data, 'songsByGenre.song') || []
    } catch (e) {
      console.error('[library] 风格歌曲加载失败:', e)
    } finally {
      loading.value = false
    }
  }

  /** 更新风格元数据 */
  async function updateGenreDetail(name, data) {
    const res = await updateStyle(name, data)
    if (res?.style) {
      genreDetail.value = res.style
    }
    return res
  }

  // ============ 歌曲 ============

  async function loadRandomSongs(size = 50) {
    loading.value = true
    try {
      const data = await subsonicGetRandomSongs(size)
      songs.value = unwrap(data, 'randomSongs.song') || []
      randomPicks.value = Array.isArray(songs.value) ? songs.value : []
    } catch (e) {
      console.error('[library] 随机歌曲加载失败:', e)
    } finally {
      loading.value = false
    }
  }

  /** 加载热门艺术家（按全局播放量排序） */
  async function loadTopArtists(limit = 12) {
    try {
      const data = await getTopArtists(limit)
      topArtists.value = data?.artists || []
    } catch (e) {
      console.error('[library] 热门艺术家加载失败:', e)
    }
  }

  /** 加载最新添加歌曲 */
  async function loadNewestSongs(limit = 12) {
    try {
      const data = await subsonicGetSongs({ sort: 'newest', count: limit })
      const result = unwrap(data, 'songs') || {}
      newestSongs.value = result.song || []
    } catch (e) {
      console.error('[library] 最新歌曲加载失败:', e)
    }
  }

  /** 分页加载歌曲，支持按首字母过滤 + 排序 */
  async function loadSongs({ letter = null, sort = null, count = 50, offset = 0 } = {}) {
    loading.value = true
    currentLetter.value = letter
    if (sort) songSortType.value = sort
    try {
      const data = await subsonicGetSongs({ letter, sort: sort || songSortType.value, count, offset })
      const result = unwrap(data, 'songs') || {}
      songs.value = result.song || []
      songTotal.value = result.total || 0
      // 同步全局计数（无过滤时用 total）
      if (!letter && result.total != null) songCount.value = result.total
    } catch (e) {
      console.error('[library] 歌曲列表加载失败:', e)
    } finally {
      loading.value = false
    }
  }

  /** 加载各首字母歌曲数量统计 */
  async function loadSongLetters() {
    try {
      const data = await subsonicGetSongLetters()
      songLetters.value = unwrap(data, 'songLetters') || []
    } catch (e) {
      console.error('[library] 歌曲字母统计加载失败:', e)
    }
  }

  // ============ 歌单 ============

  async function loadPlaylists() {
    loading.value = true
    try {
      const data = await subsonicGetPlaylists()
      playlists.value = unwrap(data, 'playlists.playlist') || []
    } catch (e) {
      console.error('[library] 歌单列表加载失败:', e)
    } finally {
      loading.value = false
    }
  }

  async function loadPlaylist(id) {
    try {
      const data = await subsonicGetPlaylist(id)
      playlistDetail.value = unwrap(data, 'playlist')
    } catch (e) {
      console.error('[library] 歌单详情加载失败:', e)
    }
  }

  // ============ 搜索 ============

  async function search(query, opts = {}) {
    loading.value = true
    try {
      const data = await subsonicSearch(query, {
        artistCount: opts.artistCount || 20,
        albumCount: opts.albumCount || 20,
        songCount: opts.songCount || 20,
      })
      const result = unwrap(data, 'searchResult3') || {}
      return {
        artists: result.artist || [],
        albums: result.album || [],
        songs: result.song || [],
      }
    } catch (e) {
      console.error('[library] 搜索失败:', e)
      return { artists: [], albums: [], songs: [] }
    } finally {
      loading.value = false
    }
  }

  // ============ 视图切换 ============

  function setView(view) {
    currentView.value = view
  }

  return {
    // 数据
    artists, albums, songs, genres, playlists,
    artistDetail, artistAlbums, artistSongs, albumDetail, playlistDetail, albumSongs,
    recentlyAdded, randomPicks, newestSongs, topArtists,
    songLetters, songTotal, currentLetter, songSortType, songPage,
    albumLetters, albumTotal,
    artistLetters, artistTotal,
    artistAlbumTotal, artistSongTotal,
    artistAlbumsLoading, artistSongsLoading,
    genreTotal, genreLetters, genreSortType,
    genreSongs, genreSongTotal, genreSongsLoading, genreDetail,
    // 统计
    artistCount, albumCount, songCount, playlistCount,
    // UI
    loading, currentView, albumSortType, page, pageSize, totalCount,
    // 方法
    loadDashboard,
    loadStats,
    loadArtists, loadArtist, loadArtistAlbums, loadArtistSongs,
    loadAlbums, loadAlbum, loadAlbumLetters,
    loadAllGenres, loadGenres, loadGenreLetters, loadGenreDetail, loadSongsByGenre, updateGenreDetail,
    loadRandomSongs,
    loadTopArtists,
    loadNewestSongs,
    loadSongs,
    loadSongLetters,
    loadPlaylists, loadPlaylist,
    search,
    setView,
  }
})
