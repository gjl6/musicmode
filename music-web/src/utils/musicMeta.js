

export function songField(meta, field) {
  const s = meta?.songs?.[0]
  if (!s) return null
  return s[field] ?? null
}


export function albumField(meta, field) {
  const a = meta?.albums?.[0]
  if (!a) return null
  return a[field] ?? null
}


export function lyricField(meta, field) {
  const l = meta?.lyrics?.[0]
  if (!l) return null
  return l[field] ?? null
}


export function styleField(meta, field) {
  const s = meta?.styles?.[0]
  if (!s) return null
  return s[field] ?? null
}


export function artistNames(meta, separator = ' / ') {
  const arr = meta?.artists
  if (!arr || arr.length === 0) return null
  return arr.map(a => a.artistName || '').filter(n => n).join(separator) || null
}


export function hasCover(meta) {
  return !!songField(meta, 'coverPath')
}


export function hasTags(meta) {
  return !!songField(meta, 'title') && !!artistNames(meta)
}


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

    const a = meta.albums?.[0]
  if (a) {
    Object.assign(result.album, {
      albumName: a.albumName || '', albumType: a.albumType || 'ALBUM',
      albumCover: a.albumCover || '', introduction: a.introduction || '',
      albumYear: a.albumYear || 0, company: a.company || '',
      language: a.language || '',
    })
  }

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

    const l = meta.lyrics?.[0]
  if (l) {
    Object.assign(result.lyric, {
      type: l.type || 'NONE', content: l.content || '',
      lrcPath: l.lrcPath || '',
    })
  }

    const st = meta.styles?.[0]
  if (st) {
    Object.assign(result.style, {
      styleName: st.styleName || '', description: st.description || '',
    })
  }

  return result
}


export function toMusicMetadata(editMeta) {
  if (!editMeta) return null

  const meta = {}

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

    if (editMeta.artists && editMeta.artists.length > 0) {
    meta.artists = editMeta.artists.map(a => ({
      artistName: a.artistName || null,
      gender: a.gender != null ? a.gender : null,
      country: a.country || null,
      introduction: a.introduction || null,
      artistCover: a.artistCover || null,
    }))
  }

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

    const l = editMeta.lyric
  if (l && l.content) {
    meta.lyrics = [{
      type: l.type || 'METADATA',
      content: l.content || null,
      lrcPath: l.lrcPath || null,
    }]
  }

    const st = editMeta.style
  if (st && st.styleName) {
    meta.styles = [{
      styleName: st.styleName || null,
      description: st.description || null,
    }]
  }

  return meta
}
