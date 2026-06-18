package com.gjl.music.playback.service.impl;

import com.gjl.music.mapper.MusicMapper;
import com.gjl.music.model.Album;
import com.gjl.music.model.Artist;
import com.gjl.music.model.Song;
import com.gjl.music.playback.infra.subsonic.SubsonicRequestParams;
import com.gjl.music.playback.model.Playlist;
import com.gjl.music.playback.service.PlayCountService;
import com.gjl.music.playback.service.PlaylistService;
import com.gjl.music.playback.service.SearchService;
import com.gjl.music.playback.service.SubsonicService;
import com.gjl.music.playback.service.CoverArtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Slf4j
@Service
public class SubsonicServiceImpl implements SubsonicService {

    private final MusicMapper musicMapper;
    private final SearchService searchService;
    private final PlaylistService playlistService;
    private final CoverArtService coverArtService;
    private final PlayCountService playCountService;

    public SubsonicServiceImpl(MusicMapper musicMapper,
                               SearchService searchService,
                               PlaylistService playlistService,
                               CoverArtService coverArtService,
                               PlayCountService playCountService) {
        this.musicMapper = musicMapper;
        this.searchService = searchService;
        this.playlistService = playlistService;
        this.coverArtService = coverArtService;
        this.playCountService = playCountService;
    }


    private final ThreadLocal<Map<Long, String>> genrePreload = new ThreadLocal<>();


    private final ThreadLocal<Map<Long, Integer>> playCountPreload = new ThreadLocal<>();


    private final ThreadLocal<Map<Long, Integer>> ratingPreload = new ThreadLocal<>();


    private final ThreadLocal<Map<String, Long>> userIdCache =
            ThreadLocal.withInitial(HashMap::new);


    private final ThreadLocal<Map<Integer, String>> artistNameCache =
            ThreadLocal.withInitial(HashMap::new);


    private final ThreadLocal<Map<Integer, String>> albumNameCache =
            ThreadLocal.withInitial(HashMap::new);


    private final Cache<String, List<Map<String, Object>>> songLettersCache =
            Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(1).build();


    public void cleanupRequest() {
        userIdCache.remove();
        artistNameCache.remove();
        albumNameCache.remove();
    }


    public Map<String, Object> ping() {
        return Map.of();
    }

    public Map<String, Object> getLicense() {
        return Map.of("license", Map.of("valid", true));
    }


    public Map<String, Object> getMusicFolders() {
        return Map.of("musicFolders", Map.of("musicFolder", List.of(
                Map.of("id", "1", "name", "Music Library")
        )));
    }

    public Map<String, Object> getIndexes(SubsonicRequestParams p) {
        List<Map<String, Object>> idxList = musicMapper.findArtistIndex();
        List<Map<String, Object>> indexEntries = new ArrayList<>();
        for (Map<String, Object> row : idxList) {
            String letter = (String) row.get("letter");
            List<Artist> artists = musicMapper.findArtists(null, null, 0, 10000);
            List<Map<String, Object>> artistMaps = artists.stream()
                    .filter(a -> a.getArtistName() != null
                            && a.getArtistName().toUpperCase().startsWith(letter))
                    .limit(100)
                    .map(this::toArtistMap)
                    .toList();
            if (!artistMaps.isEmpty()) {
                indexEntries.add(Map.of("name", letter, "artist", artistMaps));
            }
        }
        Map<String, Object> indexes = new LinkedHashMap<>();
        indexes.put("lastModified", System.currentTimeMillis() / 1000);
        indexes.put("ignoredArticles", "The El La Los Las Le Les");
        indexes.put("index", indexEntries);
        return indexes;
    }

    public Map<String, Object> getArtists(SubsonicRequestParams p) {
        String letter = p.get("letter");
        String sort = p.get("sort");
        int count = Math.min(p.getInt("count", 50), 500);
        int offset = p.getInt("offset", 0);
        List<Artist> artists = musicMapper.findArtists(letter, sort, offset, count);
        int total = musicMapper.countArtists(letter);
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Artist a : artists) {
            String firstLetter = a.getArtistName() != null && !a.getArtistName().isEmpty()
                    ? a.getArtistName().substring(0, 1).toUpperCase() : "#";
            if (!firstLetter.matches("[A-Z]")) firstLetter = "#";
            grouped.computeIfAbsent(firstLetter, k -> new ArrayList<>())
                    .add(toArtistMap(a));
        }
        List<Map<String, Object>> indexList = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            indexList.add(Map.of("name", entry.getKey(), "artist", entry.getValue()));
        }
        List<Map<String, Object>> letters = (letter == null)
                ? musicMapper.getArtistLetters() : List.of();
        return Map.of("artists", Map.of("index", indexList, "letters", letters, "total", total));
    }

    public Map<String, Object> getMusicDirectory(SubsonicRequestParams p) {
        String id = p.get("id");
        if (id == null) throw new IllegalArgumentException("缺少 id 参数");

        try {
            Long albumId = Long.parseLong(id);
            List<Song> songs = musicMapper.findSongsByAlbumId(albumId);
            Album album = musicMapper.findAlbumById(albumId);
            return buildDirectory(id, album != null ? album.getAlbumName() : null,
                    songs.stream().map(this::toSongChildMap).toList());
        } catch (NumberFormatException e) {
            return buildDirectory(id, null, List.of());
        }
    }

    public Map<String, Object> getArtist(SubsonicRequestParams p) {
        String id = p.get("id");
        if (id == null) return null;
        try {
            Artist artist = musicMapper.findArtistById(Long.parseLong(id));
            if (artist == null) return null;
            return Map.of("artist", toArtistMap(artist));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Map<String, Object> getAlbum(SubsonicRequestParams p) {
        String id = p.get("id");
        if (id == null) return null;
        try {
            Album album = musicMapper.findAlbumById(Long.parseLong(id));
            if (album == null) return null;
            return Map.of("album", toAlbumMap(album));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Map<String, Object> getSong(SubsonicRequestParams p) {
        String id = p.get("id");
        if (id == null) return null;
        try {
            Song song = musicMapper.findSongById(Long.parseLong(id));
            if (song == null) return null;
            return Map.of("song", toSongMap(song));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Map<String, Object> getGenres() {
        List<Map<String, Object>> dbGenres = musicMapper.findDistinctGenres();
        List<Map<String, Object>> genreList = new ArrayList<>();
        for (Map<String, Object> row : dbGenres) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("value", getCaseInsensitive(row, "genre", "GENRE"));
            g.put("songCount", getCaseInsensitive(row, "songCount", "SONGCOUNT"));
            if (row.containsKey("image") || row.containsKey("IMAGE")) {
                Object img = getCaseInsensitive(row, "image", "IMAGE");
                if (img != null) g.put("image", img);
            }
            genreList.add(g);
        }
        return Map.of("genres", Map.of("genre", genreList));
    }


    public Map<String, Object> getAlbumList(SubsonicRequestParams p) {
        String type = p.get("type", "newest");
        int size = Math.min(p.getInt("size", 10), 500);
        int offset = p.getInt("offset", 0);

        Long userId = getUserIdAsLong();
        List<Album> albums = musicMapper.findAlbumsByType(type, offset, size, userId, null, null, null, null);
        return Map.of("albumList", Map.of("album",
                albums.stream().map(this::toAlbumMap).toList()));
    }

    public Map<String, Object> getRandomSongs(SubsonicRequestParams p) {
        int size = Math.min(p.getInt("size", 10), 500);
        List<Song> songs = musicMapper.findRandomSongs(size);
        List<Long> songIds = songs.stream()
                .map(s -> toLong(s.getId())).filter(Objects::nonNull).toList();
        Long userId = getUserIdAsLong();
        genrePreload.set(buildGenreMap(songIds));
        if (userId != null) {
            playCountPreload.set(playCountService.getSongPlayCounts(userId, songIds));
            ratingPreload.set(playCountService.getUserRatings(userId, songIds));
        }
        try {
            return Map.of("randomSongs", Map.of("song",
                    songs.stream().map(this::toSongMap).toList()));
        } finally {
            genrePreload.remove();
            playCountPreload.remove();
            ratingPreload.remove();
        }
    }

    public Map<String, Object> getSongsByGenre(SubsonicRequestParams p) {
        String genre = p.get("genre");
        if (genre == null) throw new IllegalArgumentException("缺少 genre 参数");
        int count = Math.min(p.getInt("count", 10), 500);
        int offset = p.getInt("offset", 0);
        Long userId = getUserIdAsLong();
        List<Song> songs = musicMapper.findSongsByGenre(genre, null, "alphabetical", offset, count, userId);
        List<Long> songIds = songs.stream()
                .map(s -> toLong(s.getId())).filter(Objects::nonNull).toList();
        genrePreload.set(buildGenreMap(songIds));
        if (userId != null) {
            playCountPreload.set(playCountService.getSongPlayCounts(userId, songIds));
            ratingPreload.set(playCountService.getUserRatings(userId, songIds));
        }
        try {
            return Map.of("songsByGenre", Map.of("song",
                    songs.stream().map(this::toSongMap).toList()));
        } finally {
            genrePreload.remove();
            playCountPreload.remove();
            ratingPreload.remove();
        }
    }

    public Map<String, Object> getSongs(SubsonicRequestParams p) {
        String letter = p.get("letter");
        String sort = p.get("sort");
        int count = Math.min(p.getInt("count", 50), 500);
        int offset = p.getInt("offset", 0);
        Long userId = getUserIdAsLong();
        List<Song> songs = musicMapper.findSongsPaginated(offset, count, letter, sort, userId, null);
        int total = musicMapper.countSongs(letter, null);
        List<Long> songIds = songs.stream()
                .map(s -> toLong(s.getId())).filter(Objects::nonNull).toList();
        genrePreload.set(buildGenreMap(songIds));
        if (userId != null) {
            playCountPreload.set(playCountService.getSongPlayCounts(userId, songIds));
            ratingPreload.set(playCountService.getUserRatings(userId, songIds));
        }
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("song", songs.stream().map(this::toSongMap).toList());
            result.put("total", total);
            return Map.of("songs", result);
        } finally {
            genrePreload.remove();
            playCountPreload.remove();
            ratingPreload.remove();
        }
    }

    public Map<String, Object> getSongLetters() {
        List<Map<String, Object>> letters = songLettersCache.get("all",
                k -> musicMapper.getSongLetters());
        return Map.of("songLetters", letters);
    }


    public Map<String, Object> search(SubsonicRequestParams p) {
        String query = p.get("query");
        if (query == null || query.isBlank()) {
            return Map.of("searchResult3", Map.of(
                    "artist", List.of(), "album", List.of(), "song", List.of()));
        }

        int artistCount = Math.min(p.getInt("artistCount", 20), 100);
        int artistOffset = p.getInt("artistOffset", 0);
        int albumCount = Math.min(p.getInt("albumCount", 20), 100);
        int albumOffset = p.getInt("albumOffset", 0);
        int songCount = Math.min(p.getInt("songCount", 20), 100);
        int songOffset = p.getInt("songOffset", 0);

        List<Long> songIds = searchService.searchSongs(query, songOffset, songCount);
        List<Long> albumIds = searchService.searchAlbums(query, albumOffset, albumCount);
        List<Long> artistIds = searchService.searchArtists(query, artistOffset, artistCount);

        List<Song> songs = songIds.isEmpty() ? List.of() : musicMapper.findSongsByIds(songIds);
        List<Album> albums = albumIds.isEmpty() ? List.of() : musicMapper.findAlbumsByIds(albumIds);
        List<Artist> artists = artistIds.isEmpty() ? List.of() : musicMapper.findArtistsByIds(artistIds);

        Long userId = getUserIdAsLong();
        genrePreload.set(buildGenreMap(songIds));
        if (userId != null) {
            playCountPreload.set(playCountService.getSongPlayCounts(userId, songIds));
            ratingPreload.set(playCountService.getUserRatings(userId, songIds));
        }
        try {
            return Map.of("searchResult3", Map.of(
                    "artist", artists.stream().map(this::toArtistMap).toList(),
                    "album", albums.stream().map(this::toAlbumMap).toList(),
                    "song", songs.stream().map(this::toSongMap).toList()
            ));
        } finally {
            genrePreload.remove();
            playCountPreload.remove();
            ratingPreload.remove();
        }
    }


    public Map<String, Object> getPlaylists(SubsonicRequestParams p) {
        String username = getUsername();
        if (username == null) return Map.of("playlists", Map.of("playlist", List.of()));
        var pls = playlistService.listByUsername(username);

        final Map<Long, List<Song>> firstSongsMap;
        if (!pls.isEmpty()) {
            List<Long> allIds = pls.stream().map(Playlist::getId).toList();
            firstSongsMap = playlistService.getFirstSongsBatch(allIds, 4);
        } else {
            firstSongsMap = Map.of();
        }

        return Map.of("playlists", Map.of("playlist",
                pls.stream().map(pl -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", pl.getId());
                    m.put("name", pl.getName());
                    m.put("comment", pl.getComment() != null ? pl.getComment() : "");
                    m.put("owner", pl.getOwner());
                    m.put("public", pl.isPublic());
                    m.put("songCount", pl.getSongCount());
                    m.put("duration", (int) pl.getDuration());
                    m.put("created", pl.getCreatedAt() != null ? pl.getCreatedAt().toString() : "");
                    m.put("coverArt", "playlist-" + pl.getId());
                    List<Song> firstSongs = firstSongsMap.getOrDefault(pl.getId(), List.of());
                    m.put("entry", firstSongs.stream()
                            .map(s -> {
                                Map<String, Object> em = new LinkedHashMap<>();
                                em.put("id", s.getId());
                                em.put("coverArt", "song-" + s.getId());
                                return (Object) em;
                            })
                            .toList());
                    return m;
                }).toList()));
    }

    public Map<String, Object> getPlaylist(SubsonicRequestParams p) {
        String id = p.get("id");
        if (id == null) return null;
        Long plId = Long.parseLong(id);
        var pl = playlistService.getById(plId);
        if (pl == null) return null;
        List<Song> songs = playlistService.getSongs(plId);
        List<Long> songIds = songs.stream()
                .map(s -> toLong(s.getId())).filter(Objects::nonNull).toList();
        Long userId = getUserIdAsLong();
        genrePreload.set(buildGenreMap(songIds));
        if (userId != null) {
            playCountPreload.set(playCountService.getSongPlayCounts(userId, songIds));
            ratingPreload.set(playCountService.getUserRatings(userId, songIds));
        }
        try {
            return Map.of("playlist", Map.of(
                    "id", pl.getId(),
                    "name", pl.getName(),
                    "entry", songs.stream().map(this::toSongMap).toList()
            ));
        } finally {
            genrePreload.remove();
            playCountPreload.remove();
            ratingPreload.remove();
        }
    }

    public Map<String, Object> createPlaylist(SubsonicRequestParams p,
                                               HttpServletRequest request) {
        String name = p.get("name");
        if (name == null) {
            String plIdStr = p.get("playlistId");
            if (plIdStr == null) throw new IllegalArgumentException("缺少 name 或 playlistId");
            Long plId = Long.parseLong(plIdStr);
            String[] songIds = request.getParameterValues("songId");
            if (songIds != null) {
                List<Long> ids = Arrays.stream(songIds).map(Long::parseLong).toList();
                playlistService.addSongs(plId, ids);
            }
            return Map.of();
        }
        String username = getUsername();
        if (username == null) throw new IllegalArgumentException("未认证");
        Long userId = getUserIdAsLong();
        var pl = playlistService.createByUsername(name, "", username, userId, false);

        String[] songIds = request.getParameterValues("songId");
        if (songIds != null && songIds.length > 0) {
            List<Long> ids = Arrays.stream(songIds).map(Long::parseLong).toList();
            playlistService.addSongs(pl.getId(), ids);
        }
        return Map.of();
    }

    public Map<String, Object> deletePlaylist(SubsonicRequestParams p) {
        String id = p.get("id");
        if (id == null) throw new IllegalArgumentException("缺少 id");
        playlistService.delete(Long.parseLong(id));
        return Map.of();
    }

    public Map<String, Object> updatePlaylist(SubsonicRequestParams p,
                                               HttpServletRequest request) {
        String idStr = p.get("playlistId");
        if (idStr == null) throw new IllegalArgumentException("缺少 playlistId");
        Long id = Long.parseLong(idStr);

        String[] toRemove = request.getParameterValues("songIndexToRemove");
        if (toRemove != null && toRemove.length > 0) {
            List<Integer> indices = Arrays.stream(toRemove)
                    .map(Integer::parseInt)
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (int idx : indices) {
                playlistService.removeSong(id, idx + 1);
            }
        }

        String[] toAdd = request.getParameterValues("songIdToAdd");
        if (toAdd != null && toAdd.length > 0) {
            List<Long> songIds = Arrays.stream(toAdd)
                    .map(Long::parseLong).toList();
            playlistService.addSongs(id, songIds);
        }

        String name = p.get("name");
        String comment = p.get("comment");
        String pub = p.get("public");
        if (name != null || comment != null || pub != null) {
            Playlist existing = playlistService.getById(id);
            if (existing != null) {
                playlistService.update(id,
                        name != null ? name : existing.getName(),
                        comment != null ? comment : existing.getComment(),
                        pub != null ? "true".equals(pub) : existing.isPublic(),
                        existing.getCoverPath());
            }
        }

        return Map.of();
    }


    public void getCoverArt(SubsonicRequestParams p,
                            HttpServletResponse response) throws IOException {
        String id = p.get("id");
        if (id == null) throw new IllegalArgumentException("缺少 id");

        java.nio.file.Path coverPath = coverArtService.resolveCoverPath(id);
        if (coverPath != null && java.nio.file.Files.exists(coverPath)) {
            String mime = com.gjl.music.infra.util.ContentTypeResolver
                    .resolve(coverPath.getFileName().toString());
            response.setContentType(mime);
            java.nio.file.Files.copy(coverPath, response.getOutputStream());
        } else {
            response.setContentType("image/png");
            response.setStatus(404);
        }
    }


    public Map<String, Object> getLyricsBySongId(SubsonicRequestParams p) {
        String id = p.get("id");
        if (id == null) throw new IllegalArgumentException("缺少 id 参数");

        Song song = null;
        try {
            song = musicMapper.findSongById(Long.parseLong(id));
        } catch (NumberFormatException ignored) {}
        if (song == null) return null;

        Map<String, Object> lyricRow = musicMapper.findLyricBySongId(Long.parseLong(id));
        if (lyricRow == null || lyricRow.isEmpty()) return null;

        String content = (String) lyricRow.get("CONTENT");
        String type = (String) lyricRow.get("TYPE");
        if (content == null || content.isBlank()) return null;

        boolean isLrc = "LRC".equalsIgnoreCase(type) || looksLikeLrc(content);
        List<Map<String, Object>> lines = isLrc
                ? parseLrcLines(content)
                : splitPlainLines(content);

        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("displayArtist", resolveArtistName(song.getArtistId()));
        structured.put("displayTitle", song.getTitle() != null ? song.getTitle() : "");
        structured.put("lang", song.getLanguage() != null ? song.getLanguage() : "zho");
        structured.put("synced", isLrc);
        structured.put("line", lines);

        return Map.of("lyricsList", Map.of("structuredLyrics", List.of(structured)));
    }


    public Map<String, Object> scrobble(SubsonicRequestParams p) {
        String id = p.get("id");
        if (id == null) throw new IllegalArgumentException("缺少 id");
        if (!"true".equals(p.get("submission", "true"))) return Map.of();

        Long userId = getUserIdAsLong();
        if (userId == null) throw new IllegalArgumentException("未认证");

        try {
            Long songId = Long.parseLong(id);

            Song song = musicMapper.findSongById(songId);
            if (song == null) return Map.of();
            Long albumId = song.getAlbumId() != null ? song.getAlbumId().longValue() : null;
            List<Long> artistIds = musicMapper.findArtistIdsBySongId(songId);
            List<Long> styleIds = musicMapper.findStyleIdsBySongId(songId);

            playCountService.scrobbleTransactional(userId, songId, albumId, artistIds, styleIds, "subsonic");
        } catch (NumberFormatException ignored) {}
        return Map.of();
    }

    public Map<String, Object> reportPlayback(SubsonicRequestParams p) {
        if (!"stopped".equals(p.get("state"))) return Map.of();
        String mediaId = p.get("mediaId");
        Long userId = getUserIdAsLong();
        if (mediaId == null || userId == null) return Map.of();

        try {
            Long songId = Long.parseLong(mediaId);

            Song song = musicMapper.findSongById(songId);
            if (song == null || song.getDuration() == null) return Map.of();
            int posSec = p.getInt("positionMs", 0) / 1000;
            if (posSec < thresholdSeconds(song.getDuration())) return Map.of();

            Long albumId = song.getAlbumId() != null ? song.getAlbumId().longValue() : null;
            List<Long> artistIds = musicMapper.findArtistIdsBySongId(songId);
            List<Long> styleIds = musicMapper.findStyleIdsBySongId(songId);

            playCountService.scrobbleTransactional(userId, songId, albumId, artistIds, styleIds, "reportPlayback");
        } catch (NumberFormatException ignored) {}
        return Map.of();
    }

    public Map<String, Object> star(SubsonicRequestParams p) {
        Long userId = getUserIdAsLong();
        if (userId == null) throw new IllegalArgumentException("未认证");
        boolean isAlbum  = p.get("albumId") != null;
        boolean isArtist = p.get("artistId") != null;
        if (p.get("id") != null && !isAlbum && !isArtist)
            playCountService.star(userId, Long.parseLong(p.get("id")), "song");
        if (isAlbum)    playCountService.star(userId, Long.parseLong(p.get("albumId")), "album");
        if (isArtist)   playCountService.star(userId, Long.parseLong(p.get("artistId")), "artist");
        if (p.get("playlistId") != null) playCountService.star(userId, Long.parseLong(p.get("playlistId")), "playlist");
        return Map.of();
    }

    public Map<String, Object> unstar(SubsonicRequestParams p) {
        Long userId = getUserIdAsLong();
        if (userId == null) throw new IllegalArgumentException("未认证");
        boolean isAlbum  = p.get("albumId") != null;
        boolean isArtist = p.get("artistId") != null;
        if (p.get("id") != null && !isAlbum && !isArtist)
            playCountService.unstar(userId, Long.parseLong(p.get("id")), "song");
        if (isAlbum)    playCountService.unstar(userId, Long.parseLong(p.get("albumId")), "album");
        if (isArtist)   playCountService.unstar(userId, Long.parseLong(p.get("artistId")), "artist");
        if (p.get("playlistId") != null) playCountService.unstar(userId, Long.parseLong(p.get("playlistId")), "playlist");
        return Map.of();
    }

    public Map<String, Object> getStarred(SubsonicRequestParams p) {
        return getStarred2(p);
    }

    public Map<String, Object> getStarred2(SubsonicRequestParams p) {
        Long userId = getUserIdAsLong();
        if (userId == null) return Map.of("starred2", Map.of("song", List.of(), "album", List.of(), "artist", List.of(), "playlist", List.of()));

        List<Long> songIds = playCountService.getStarredIds(userId, "song");
        List<Song> songs = songIds.isEmpty() ? List.of() : musicMapper.findSongsByIds(songIds);

        List<Long> albumIds = playCountService.getStarredIds(userId, "album");
        List<Album> starredAlbums = albumIds.isEmpty() ? List.of() : musicMapper.findAlbumsByIds(albumIds);

        List<Long> artistIds = playCountService.getStarredIds(userId, "artist");
        List<Artist> starredArtists = artistIds.isEmpty() ? List.of() : musicMapper.findArtistsByIds(artistIds);

        List<Long> playlistIds = playCountService.getStarredIds(userId, "playlist");
        List<Map<String, Object>> playlistMaps = List.of();
        if (!playlistIds.isEmpty()) {
            var pls = playlistService.listByUsername(getUsername());
            playlistMaps = pls.stream()
                    .filter(pl -> playlistIds.contains(pl.getId()))
                    .map(pl -> Map.<String, Object>of(
                            "id", pl.getId(),
                            "name", pl.getName(),
                            "comment", pl.getComment() != null ? pl.getComment() : "",
                            "owner", pl.getOwner(),
                            "public", pl.isPublic(),
                            "songCount", pl.getSongCount(),
                            "duration", (int) pl.getDuration(),
                            "created", pl.getCreatedAt() != null ? pl.getCreatedAt().toString() : ""
                    )).toList();
        }

        try {
            genrePreload.set(buildGenreMap(songIds));
            if (userId != null) {
                playCountPreload.set(playCountService.getSongPlayCounts(userId, songIds));
                ratingPreload.set(playCountService.getUserRatings(userId, songIds));
            }

            return Map.of("starred2", Map.of(
                    "song", songs.stream().map(this::toSongMap).toList(),
                    "album", starredAlbums.stream().map(this::toAlbumMap).toList(),
                    "artist", starredArtists.stream().map(this::toArtistMap).toList(),
                    "playlist", playlistMaps));
        } finally {
            genrePreload.remove();
            playCountPreload.remove();
            ratingPreload.remove();
        }
    }

    public Map<String, Object> setRating(SubsonicRequestParams p) {
        Long userId = getUserIdAsLong();
        String id = p.get("id");
        int rating = p.getInt("rating", 0);
        if (userId == null || id == null) throw new IllegalArgumentException("缺少参数");
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("评分范围 1-5");
        playCountService.rate(userId, Long.parseLong(id), "song", rating);
        return Map.of();
    }


    private String getUsername() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private Long getUserIdAsLong() {
        String username = getUsername();
        if (username == null) return null;
        Map<String, Long> cache = userIdCache.get();
        return cache.computeIfAbsent(username, u -> musicMapper.findUserIdByUsername(u));
    }

    private int resolvePlayCount(String songId) {
        try {
            Long id = Long.parseLong(songId);
            Map<Long, Integer> preload = playCountPreload.get();
            if (preload != null) return preload.getOrDefault(id, 0);
            Long userId = getUserIdAsLong();
            if (userId == null) return 0;
            return playCountService.getSongPlayCounts(userId, List.of(id)).getOrDefault(id, 0);
        } catch (NumberFormatException e) { return 0; }
    }

    private int resolveUserRating(String songId) {
        try {
            Long id = Long.parseLong(songId);
            Map<Long, Integer> preload = ratingPreload.get();
            if (preload != null) return preload.getOrDefault(id, 0);
            Long userId = getUserIdAsLong();
            if (userId == null) return 0;
            return playCountService.getUserRatings(userId, List.of(id)).getOrDefault(id, 0);
        } catch (NumberFormatException e) { return 0; }
    }

    private Map<Long, String> buildGenreMap(List<Long> songIds) {
        if (songIds.isEmpty()) return Map.of();
        List<Map<String, Object>> rows = musicMapper.findGenresBySongIds(songIds);
        Map<Long, String> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object songIdObj = row.get("SONGID");
            if (songIdObj == null) songIdObj = row.get("songId");
            Object styleObj = row.get("STYLENAME");
            if (styleObj == null) styleObj = row.get("styleName");
            Long songId = toLong(songIdObj);
            if (songId != null && !result.containsKey(songId)) {
                result.put(songId, styleObj != null ? styleObj.toString() : "");
            }
        }
        return result;
    }

    private String resolveGenre(String songId) {
        try {
            if (songId == null || songId.isBlank()) return "";
            Map<Long, String> preload = genrePreload.get();
            Long id = Long.parseLong(songId);
            if (preload != null) return preload.getOrDefault(id, "");
            String genre = musicMapper.findFirstGenreBySongId(id);
            return genre != null ? genre : "";
        } catch (NumberFormatException e) {
            return "";
        }
    }

    private String resolveArtistName(Integer artistId) {
        if (artistId == null) return "";
        Map<Integer, String> cache = artistNameCache.get();
        return cache.computeIfAbsent(artistId, id -> {
            Artist a = musicMapper.findArtistById(id.longValue());
            return a != null && a.getArtistName() != null ? a.getArtistName() : "";
        });
    }

    private String resolveAlbumName(Integer albumId) {
        if (albumId == null) return "";
        Map<Integer, String> cache = albumNameCache.get();
        return cache.computeIfAbsent(albumId, id -> {
            Album a = musicMapper.findAlbumById(id.longValue());
            return a != null && a.getAlbumName() != null ? a.getAlbumName() : "";
        });
    }

    private Map<String, Object> toSongMap(Song s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("title", sanitize(s.getTitle()));
        m.put("albumId", s.getAlbumId() != null ? String.valueOf(s.getAlbumId()) : "");
        Integer artistId = s.getArtistId();
        if (artistId == null && s.getId() != null) {
            artistId = musicMapper.findFirstArtistIdBySongId(Long.parseLong(s.getId()));
        }
        m.put("artistId", artistId != null ? String.valueOf(artistId) : "");
        m.put("artist", artistId != null
                ? resolveArtistName(artistId)
                : (s.getArtistName() != null ? s.getArtistName() : ""));
        m.put("album", s.getAlbumName() != null
                ? s.getAlbumName()
                : resolveAlbumName(s.getAlbumId()));
        m.put("genre", resolveGenre(s.getId()));
        m.put("duration", s.getDuration() != null ? s.getDuration() : 0);
        m.put("track", s.getTrackNumber() != null ? s.getTrackNumber() : 0);
        m.put("discNumber", s.getDiscNumber() != null ? s.getDiscNumber() : 1);
        m.put("year", parseYear(s.getYear()));
        m.put("bitRate", s.getBitrate() != null ? s.getBitrate() : 0);
        m.put("size", s.getFileSize());
        m.put("contentType", com.gjl.music.infra.util.FfmpegUtil
                .mimeTypeForFormat(s.getFileFormat()));
        m.put("suffix", s.getFileFormat());
        m.put("path", s.getFilePath());
        m.put("coverArt", "song-" + s.getId());
        m.put("isVideo", false);
        m.put("playCount", resolvePlayCount(s.getId()));
        m.put("userRating", resolveUserRating(s.getId()));
        return m;
    }

    private Map<String, Object> toAlbumMap(Album a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("name", a.getAlbumName() != null ? a.getAlbumName() : "");
        m.put("artist", resolveArtistName(a.getArtistId()));
        m.put("artistId", a.getArtistId() != null ? String.valueOf(a.getArtistId()) : "");
        m.put("songCount", a.getSongCount() != null ? a.getSongCount() : 0);
        m.put("year", a.getAlbumYear() != null ? a.getAlbumYear() : 0);
        m.put("coverArt", "album-" + a.getId());
        return m;
    }

    private Map<String, Object> toArtistMap(Artist a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("name", a.getArtistName() != null ? a.getArtistName() : "");
        m.put("albumCount", a.getAlbumCount() != null ? a.getAlbumCount() : 0);
        m.put("songCount", a.getSongCount() != null ? a.getSongCount() : 0);
        m.put("gender", a.getGender());
        m.put("country", a.getCountry());
        m.put("coverArt", "artist-" + a.getId());
        return m;
    }

    private Map<String, Object> toSongChildMap(Song s) {
        Map<String, Object> m = toSongMap(s);
        m.put("isDir", false);
        m.put("type", "music");
        m.put("parent", s.getAlbumId() != null ? String.valueOf(s.getAlbumId()) : "");
        return m;
    }

    private Map<String, Object> buildDirectory(String id, String name, List<Map<String, Object>> children) {
        Map<String, Object> dir = new LinkedHashMap<>();
        dir.put("id", id);
        dir.put("name", name != null ? name : id);
        dir.put("child", children);
        return Map.of("directory", dir);
    }


    private static String sanitize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.indexOf('\0') >= 0 ? s.replace("\0", "") : s;
    }

    private static int parseYear(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        String s = sanitize(raw).trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d{4}").matcher(s);
        if (m.find()) {
            try { return Integer.parseInt(m.group()); }
            catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private static Object getCaseInsensitive(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) return map.get(key);
        }
        return null;
    }

    private static Long toLong(Object obj) {
        if (obj instanceof Number n) return n.longValue();
        if (obj instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private static int thresholdSeconds(int totalSec) {
        if (totalSec < 60) return (int) (totalSec * 0.8);
        if (totalSec < 480) return Math.min((int) (totalSec * 0.5), 120);
        return 240;
    }

    private static List<Map<String, Object>> parseLrcLines(String lrc) {
        List<Map<String, Object>> lines = new ArrayList<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\\[(\\d{2}):(\\d{2})(?:\\.(\\d{2,3}))?\\](.*)");
        for (String raw : lrc.split("\\n")) {
            String line = raw.strip();
            if (line.isEmpty()) continue;
            java.util.regex.Matcher m = pattern.matcher(line);
            if (m.matches()) {
                int min = Integer.parseInt(m.group(1));
                int sec = Integer.parseInt(m.group(2));
                String msStr = m.group(3);
                int ms = msStr != null
                        ? Integer.parseInt(msStr.length() == 2 ? msStr + "0" : msStr)
                        : 0;
                long startMs = (min * 60L + sec) * 1000 + ms;
                String text = m.group(4).strip();
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("start", startMs);
                entry.put("value", text.isEmpty() ? "♪" : text);
                lines.add(entry);
            }
        }
        return lines;
    }

    private static boolean looksLikeLrc(String text) {
        if (text == null || text.isBlank()) return false;
        String firstLine = text.strip().lines().findFirst().orElse("").strip();
        return firstLine.matches("\\[\\d{2}:\\d{2}[.:]\\d{2,3}\\].*");
    }

    private static List<Map<String, Object>> splitPlainLines(String text) {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (String raw : text.split("\\n")) {
            String line = raw.strip();
            if (line.isEmpty()) continue;
            lines.add(Map.of("value", line));
        }
        return lines;
    }
}
