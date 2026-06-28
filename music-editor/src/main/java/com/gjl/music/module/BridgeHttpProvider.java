package com.gjl.music.module;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjl.music.model.Album;
import com.gjl.music.model.Artist;
import com.gjl.music.model.MusicMetadata;
import com.gjl.music.model.Song;
import com.gjl.music.infra.enrich.ProviderRateLimiter;
import com.gjl.music.module.song.enrich.SongProvider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * BRIDGE 妯″紡鑷畾涔?Provider 鈥斺€?POST {title, artist} 鍒扮敤鎴锋寚瀹?URL锛岃В鏋愯繑鍥炵粨鏋溿€? *
 * <p>鍚堢害锛? * <pre>{@code
 * 璇锋眰: POST {bridgeUrl}  Content-Type: application/json
 *       {"title": "...", "artist": "..."}
 *
 * 鍝嶅簲: {
 *         "results": [
 *           {
 *             "title": "...", "artist": "...", "album": "...",
 *             "year": "2010", "trackNumber": 1, "discNumber": 1,
 *             "coverUrl": "https://...", "lyric": "...",
 *             "language": "zh", "genre": "...",
 *             "composer": "...", "lyricist": "..."
 *           }
 *         ]
 *       }
 * }</pre>
 */
public class BridgeHttpProvider implements SongProvider {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http;

    private final String name;
    private final String bridgeUrl;
    private final String authHeader;
    private final int timeoutSeconds;
    private final ProviderRateLimiter limiter;

    public BridgeHttpProvider(String name, String label, String bridgeUrl,
                               String authHeader, int timeoutSeconds,
                               long rateLimitMs, int rateLimitRetries, long rateLimitBackoffMs) {
        this.name = name;
        this.bridgeUrl = bridgeUrl;
        this.authHeader = authHeader;
        this.timeoutSeconds = Math.max(5, timeoutSeconds);
        this.limiter = new ProviderRateLimiter(rateLimitMs, 6, timeoutSeconds, rateLimitRetries, rateLimitBackoffMs);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String name() { return name; }

    @Override
    public List<MusicMetadata> search(String title, String artist) {
        int maxRetries = limiter.rateLimitRetries();
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            limiter.acquire();
            try {
                return doSearch(title, artist);
            } catch (Exception e) {
                // bridge errors typically aren't rate-limit related, but retry once
                if (attempt < maxRetries) continue;
                return List.of();
            } finally {
                limiter.release();
            }
        }
        return List.of();
    }

    private List<MusicMetadata> doSearch(String title, String artist) throws Exception {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("title", title != null ? title : "");
        if (artist != null && !artist.isBlank()) body.put("artist", artist);

        var builder = HttpRequest.newBuilder()
                .uri(URI.create(bridgeUrl))
                .POST(HttpRequest.BodyPublishers.ofString(
                        JSON.writeValueAsString(body), StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(timeoutSeconds));

        if (authHeader != null && !authHeader.isBlank()) {
            builder.header("Authorization", authHeader);
        }

        HttpResponse<String> resp = http.send(builder.build(),
                HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            return List.of();
        }

        Map<String, Object> data = JSON.readValue(resp.body(),
                new TypeReference<Map<String, Object>>() {});

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) data.get("results");
        if (results == null || results.isEmpty()) return List.of();

        List<MusicMetadata> list = new ArrayList<>();
        for (Map<String, Object> r : results) {
            list.add(mapToMetadata(r));
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private MusicMetadata mapToMetadata(Map<String, Object> r) {
        MusicMetadata m = new MusicMetadata();

        String title = str(r, "title");
        String artist = str(r, "artist");
        String album = str(r, "album");

        if (title != null && !title.isBlank()) {
            Song s = new Song();
            s.setTitle(title);
            s.setFileName(sid(r));
            String year = str(r, "year"); if (year != null) s.setYear(year);
            Object tn = r.get("trackNumber"); if (tn != null) s.setTrackNumber(((Number) tn).intValue());
            Object dn = r.get("discNumber"); if (dn != null) s.setDiscNumber(((Number) dn).intValue());
            s.setCoverPath(str(r, "coverUrl"));
            s.setLanguage(str(r, "language"));
            s.setComposer(str(r, "composer"));
            s.setLyricist(str(r, "lyricist"));
            m.addSong(s);
        }

        if (artist != null && !artist.isBlank()) {
            Artist a = new Artist();
            a.setArtistName(artist);
            m.addArtist(a);
        }

        if (album != null && !album.isBlank()) {
            Album al = new Album();
            al.setAlbumName(album);
            m.addAlbum(al);
        }

        // 姝岃瘝
        String lyric = str(r, "lyric");
        if (lyric != null && !lyric.isBlank()) {
            com.gjl.music.model.Lyric l = new com.gjl.music.model.Lyric();
            l.setContent(lyric);
            m.addLyric(l);
        }

        // 椋庢牸
        String genre = str(r, "genre");
        if (genre != null && !genre.isBlank()) {
            com.gjl.music.model.Style st = new com.gjl.music.model.Style();
            st.setStyleName(genre);
            m.addStyle(st);
        }

        return m;
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    private static String sid(Map<String, Object> r) {
        return str(r, "id") != null ? "custom:" + str(r, "id") : "custom:" + str(r, "title");
    }
}
