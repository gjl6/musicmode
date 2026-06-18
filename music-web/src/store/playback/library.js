

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
import { getArtist, getArtistAlbums, getArtistSongs } from '@/api/playback/artist.js'
import { getGenres, getGenreLetters, getGenreSongs, updateStyle } from '@/api/playback/genre.js'

function unwrap(data, path) {
  if (!data) return null
    const resp = data['subsonic-response'] || data
  if (!path) return resp
  return path.split('.').reduce((obj, key) => obj?.[key], resp)
}

export const useLibraryStore = defineStore('library', () => {

  const artists = ref([])
  const albums = ref([])
  const songs = ref([])
  const genres = ref([])
  const playlists = ref([])

    const songLetters = ref([])
  const songTotal = ref(0)
  const currentLetter = ref(null)
  const songSortType = ref('alphabetical')
  const songPage = ref(1)

    const albumLetters = ref([])
  const albumTotal = ref(0)


  const artistDetail = ref(null)
  const artistAlbums = ref([])
  const artistSongs = ref([])
  const albumDetail = ref(null)
  const playlistDetail = ref(null)
  const albumSongs = ref([])

    const artistLetters = ref([])
  const artistTotal = ref(0)

    const artistAlbumTotal = ref(0)
  const artistSongTotal = ref(0)
  const artistAlbumsLoading = ref(false)
  const artistSongsLoading = ref(false)

    const genreTotal = ref(0)
  const genreLetters = ref([])
  const genreSortType = ref('name')

    const genreSongs = ref([])
  const genreSongTotal = ref(0)
  const genreSongsLoading = ref(false)
  const genreDetail = ref(null)


  const artistCount = computed(() => artists.value.length)
  const albumCount = computed(() => albums.value.length)
  const songCount = ref(0)
  const playlistCount = computed(() => playlists.value.length)


  const loading = ref(false)
  const currentView = ref('grid')
  const albumSortType = ref('newest')
  const page = ref(0)
  const pageSize = ref(50)
  const totalCount = ref(0)


  const recentlyAdded = ref([])
  const randomPicks = ref([])

  async function loadDashboard() {
    loading.value = true
    try {
      const [albumData, randomData, playlistData] = await Promise.all([
        getAlbums({ sort: 'newest', limit: 12 }),
        subsonicGetRandomSongs(20),
        subsonicGetPlaylists(),
      ])
      recentlyAdded.value = albumData?.albums || []
      const randomSongs = unwrap(randomData, 'randomSongs.song') || []
      randomPicks.value = Array.isArray(randomSongs) ? randomSongs : []
      playlists.value = unwrap(playlistData, 'playlists.playlist') || []
    } catch (e) {
      console.error('[library] 仪表盘加载失败:', e)
    } finally {
      loading.value = false
    }
  }


  async function loadArtists({ letter = null, sort = null, count = 50, offset = 0 } = {}) {
    loading.value = true
    try {
      const params = { count, offset }
      if (letter) params.letter = letter
      if (sort) params.sort = sort
      const data = await subsonicGetArtists(params)
      const resp = unwrap(data, 'artists') || {}
      const index = resp.index || []
            const all = []
      for (const group of (Array.isArray(index) ? index : [])) {
        const list = group.artist || []
        for (const a of list) all.push(a)
      }
      artists.value = all
      artistTotal.value = resp.total || 0
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
    } catch (e) {
      console.error('[library] 专辑列表加载失败:', e)
    } finally {
      loading.value = false
    }
  }


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


  async function loadGenreLetters() {
    try {
      const res = await getGenreLetters()
      genreLetters.value = res?.letters || []
    } catch (e) {
      console.error('[library] 风格字母统计加载失败:', e)
    }
  }


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


  async function updateGenreDetail(name, data) {
    const res = await updateStyle(name, data)
    if (res?.style) {
      genreDetail.value = res.style
    }
    return res
  }


  async function loadRandomSongs(size = 50) {
    loading.value = true
    try {
      const data = await subsonicGetRandomSongs(size)
      songs.value = unwrap(data, 'randomSongs.song') || []
    } catch (e) {
      console.error('[library] 随机歌曲加载失败:', e)
    } finally {
      loading.value = false
    }
  }


  async function loadSongs({ letter = null, sort = null, count = 50, offset = 0 } = {}) {
    loading.value = true
    currentLetter.value = letter
    if (sort) songSortType.value = sort
    try {
      const data = await subsonicGetSongs({ letter, sort: sort || songSortType.value, count, offset })
      const result = unwrap(data, 'songs') || {}
      songs.value = result.song || []
      songTotal.value = result.total || 0
    } catch (e) {
      console.error('[library] 歌曲列表加载失败:', e)
    } finally {
      loading.value = false
    }
  }


  async function loadSongLetters() {
    try {
      const data = await subsonicGetSongLetters()
      songLetters.value = unwrap(data, 'songLetters') || []
    } catch (e) {
      console.error('[library] 歌曲字母统计加载失败:', e)
    }
  }


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


  function setView(view) {
    currentView.value = view
  }

  return {
        artists, albums, songs, genres, playlists,
    artistDetail, artistAlbums, artistSongs, albumDetail, playlistDetail, albumSongs,
    recentlyAdded, randomPicks,
    songLetters, songTotal, currentLetter, songSortType, songPage,
    albumLetters, albumTotal,
    artistLetters, artistTotal,
    artistAlbumTotal, artistSongTotal,
    artistAlbumsLoading, artistSongsLoading,
    genreTotal, genreLetters, genreSortType,
    genreSongs, genreSongTotal, genreSongsLoading, genreDetail,
        artistCount, albumCount, songCount, playlistCount,
        loading, currentView, albumSortType, page, pageSize, totalCount,
        loadDashboard,
    loadArtists, loadArtist, loadArtistAlbums, loadArtistSongs,
    loadAlbums, loadAlbum, loadAlbumLetters,
    loadAllGenres, loadGenres, loadGenreLetters, loadGenreDetail, loadSongsByGenre, updateGenreDetail,
    loadRandomSongs,
    loadSongs,
    loadSongLetters,
    loadPlaylists, loadPlaylist,
    search,
    setView,
  }
})
