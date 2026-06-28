/**
 * MusicMetadata 工具函数 —— 统一从 Jackson 序列化的 MusicMetadata 嵌套结构中提取字段。
 *
 * 后端 MusicMetadata 结构（Jackson 序列化）：
 *   { songs: Song[], artists: Artist[], albums: Album[], lyrics: Lyric[], styles: Style[] }
 *
 * 所有组件/Store 统一导入此文件，避免分散重复的 `meta.songs[0].title` 取值逻辑。
 */

/** 从 meta.songs[0] 提取字段 */
export function songField(meta, field) {
  const s = meta?.songs?.[0]
  if (!s) return null
  return s[field] ?? null
}

/** 从 meta.albums[0] 提取字段 */
export function albumField(meta, field) {
  const a = meta?.albums?.[0]
  if (!a) return null
  return a[field] ?? null
}

/** 从 meta.lyrics[0] 提取字段 */
export function lyricField(meta, field) {
  const l = meta?.lyrics?.[0]
  if (!l) return null
  return l[field] ?? null
}

/** 从 meta.styles[0] 提取字段 */
export function styleField(meta, field) {
  const s = meta?.styles?.[0]
  if (!s) return null
  return s[field] ?? null
}

/** 连接所有艺术家名，可指定连接符（默认 ' / '，可从 DB 配置读取） */
export function artistNames(meta, separator = ' / ') {
  const arr = meta?.artists
  if (!arr || arr.length === 0) return null
  return arr.map(a => a.artistName || '').filter(n => n).join(separator) || null
}

/** 是否有封面 */
export function hasCover(meta) {
  return !!songField(meta, 'coverPath')
}

/** 是否有基本标签 */
export function hasTags(meta) {
  return !!songField(meta, 'title') && !!artistNames(meta)
}

/**
 * 将 Jackson 序列化的 MusicMetadata 转为 edit store 内部结构。
 * 返回 { song, album, artists[], lyric, style, activeArtistIndex }
 * 其中 song/album/lyric/style = 取首个元素，artist = artists[activeArtistIndex]
 */
export function toEditMeta(meta) {
  const result = {
    song: {
      title: '', fileName: '', year: '', language: '',
      trackNumber: 0, discNumber: 0,
      composer: '', lyricist: '', coverPath: '',
      _readonly: {
        duration: 0, bitrate: 0, sampleRate: 0,
        channels: 0, bitsPerSample: 0,
        fileSize: 0, fileFormat: '', filePath: '',
      },
    },
    album: {
      albumName: '', albumType: 'ALBUM', albumCover: '',
      introduction: '', albumYear: 0, company: '', language: '',
    },
    artists: [],
    activeArtistIndex: 0,
    artist: {
      artistName: '', artistCover: '',
      introduction: '', gender: 0, country: '',
    },
    lyric: { type: 'NONE', content: '', lrcPath: '' },
    style: { styleName: '', description: '' },
  }

  if (!meta) return result

  // song
  const s = meta.songs?.[0]
  if (s) {
    result.song = {
      title: s.title || '', fileName: s.fileName || '', year: s.year || '',
      language: s.language || '', trackNumber: s.trackNumber || 0,
      discNumber: s.discNumber || 0, composer: s.composer || '',
      lyricist: s.lyricist || '', coverPath: s.coverPath || '',
      _readonly: {
        duration: s.duration || 0, bitrate: s.bitrate || 0,
        sampleRate: s.sampleRate || 0, channels: s.channels || 0,
        bitsPerSample: s.bitsPerSample || 0, fileSize: s.fileSize || 0,
        fileFormat: s.fileFormat || '', filePath: s.filePath || '',
      },
    }
  }

  // album
  const a = meta.albums?.[0]
  if (a) {
    Object.assign(result.album, {
      albumName: a.albumName || '', albumType: a.albumType || 'ALBUM',
      albumCover: a.albumCover || '', introduction: a.introduction || '',
      albumYear: a.albumYear || 0, company: a.company || '',
      language: a.language || '',
    })
  }

  // artists
  if (meta.artists && meta.artists.length > 0) {
    result.artists = meta.artists.map(a => ({
      artistName: a.artistName || '', artistCover: a.artistCover || '',
      introduction: a.introduction || '', gender: a.gender ?? 0,
      country: a.country || '',
    }))
    Object.assign(result.artist, result.artists[0])
  } else {
    result.artists = [{ artistName: '', artistCover: '', introduction: '', gender: 0, country: '' }]
  }

  // lyric
  const l = meta.lyrics?.[0]
  if (l) {
    Object.assign(result.lyric, {
      type: l.type || 'NONE', content: l.content || '',
      lrcPath: l.lrcPath || '',
    })
  }

  // style
  const st = meta.styles?.[0]
  if (st) {
    Object.assign(result.style, {
      styleName: st.styleName || '', description: st.description || '',
    })
  }

  return result
}

/**
 * 将 editStore 内部结构转为后端 MusicMetadata 格式用于保存。
 * 输入: { song, album, artists[], lyric, style }
 * 输出: { songs: [...], albums: [...], artists: [...], lyrics: [...], styles: [...] }
 */
export function toMusicMetadata(editMeta) {
  if (!editMeta) return null

  const meta = {}

  // song
  const s = editMeta.song
  if (s) {
    meta.songs = [{
      title: s.title || null,
      year: s.year || null,
      language: s.language || null,
      trackNumber: s.trackNumber != null ? s.trackNumber : null,
      discNumber: s.discNumber != null ? s.discNumber : null,
      composer: s.composer || null,
      lyricist: s.lyricist || null,
      coverPath: s.coverPath || null,
      fileName: s.fileName || null,
      filePath: s._readonly?.filePath || s.filePath || null,
      fileFormat: s._readonly?.fileFormat || null,
      duration: s._readonly?.duration ?? 0,
      bitrate: s._readonly?.bitrate ?? 0,
      sampleRate: s._readonly?.sampleRate ?? 0,
      channels: s._readonly?.channels ?? 0,
      bitsPerSample: s._readonly?.bitsPerSample ?? 0,
      fileSize: s._readonly?.fileSize ?? 0,
    }]
  }

  // artists
  if (editMeta.artists && editMeta.artists.length > 0) {
    meta.artists = editMeta.artists.map(a => ({
      artistName: a.artistName || null,
      gender: a.gender != null ? a.gender : null,
      country: a.country || null,
      introduction: a.introduction || null,
      artistCover: a.artistCover || null,
    }))
  }

  // album
  const al = editMeta.album
  if (al) {
    meta.albums = [{
      albumName: al.albumName || null,
      albumType: al.albumType || null,
      albumYear: al.albumYear != null ? al.albumYear : null,
      company: al.company || null,
      introduction: al.introduction || null,
      language: al.language || null,
    }]
  }

  // lyric
  const l = editMeta.lyric
  if (l && l.content) {
    meta.lyrics = [{
      type: l.type || 'METADATA',
      content: l.content || null,
      lrcPath: l.lrcPath || null,
    }]
  }

  // style
  const st = editMeta.style
  if (st && st.styleName) {
    meta.styles = [{
      styleName: st.styleName || null,
      description: st.description || null,
    }]
  }

  return meta
}
