package com.gjl.music.service;

import com.gjl.music.mapper.MusicMapper;
import com.gjl.music.model.Artist;
import com.gjl.music.module.enrich.core.EnrichHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;


@Slf4j
@Service
public class ArtistEditServiceImpl {

    private final MusicMapper musicMapper;
    private final TagSyncService tagSyncService;
    private final EnrichHelper enrichHelper;
    private final Path coversDir;

    public ArtistEditServiceImpl(MusicMapper musicMapper, TagSyncService tagSyncService,
                                  EnrichHelper enrichHelper,
                                  @Value("${music.covers-dir:../covers}") String coversDir) {
        this.musicMapper = musicMapper;
        this.tagSyncService = tagSyncService;
        this.enrichHelper = enrichHelper;
        this.coversDir = Path.of(coversDir).toAbsolutePath().normalize();
    }


    @Transactional
    public Map<String, Object> updateArtist(Long id, Map<String, Object> body) {
        Artist artist = musicMapper.findArtistById(id);
        if (artist == null) {
            throw new IllegalArgumentException("艺术家不存在: " + id);
        }

        String artistName = body.containsKey("name") ? (String) body.get("name") : null;
        String introduction = body.containsKey("introduction") ? (String) body.get("introduction") : null;
        Integer gender = body.containsKey("gender") ? toInt(body.get("gender")) : null;
        String country = body.containsKey("country") ? (String) body.get("country") : null;
        String artistCover = body.containsKey("artistCover") ? (String) body.get("artistCover") : null;

                if (artistCover != null && (artistCover.startsWith("http://") || artistCover.startsWith("https://"))) {
            String rawArtistName = artistName != null ? artistName : artist.getArtistName();
            String local = enrichHelper.downloadArtistCover(artistCover, rawArtistName);
            if (local != null) {
                artistCover = local;
                log.info("封面已下载到本地: {} → {}", rawArtistName, local);
            } else {
                log.warn("封面下载失败，保留网络 URL: {} → {}", rawArtistName, artistCover);
            }
        }

                if (gender != null && (gender < 0 || gender > 3)) {
            throw new IllegalArgumentException("无效的性别值: " + gender + " (有效范围 0-3)");
        }

        musicMapper.updateArtist(id, artistName, introduction, gender, country, artistCover, null);

        Artist updated = musicMapper.findArtistById(id);

                tagSyncService.syncArtistChange(id);

        return Map.of("artist", toArtistMap(updated));
    }


    Map<String, Object> toArtistMap(Artist a) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", a.getId());
        map.put("name", a.getArtistName());
        map.put("coverArt", a.getArtistCover());
        map.put("albumCount", a.getAlbumCount());
        map.put("songCount", a.getSongCount());
        map.put("introduction", a.getIntroduction());
        map.put("gender", a.getGender());
        map.put("country", a.getCountry());
        map.put("enrichSource", a.getEnrichSource());
        map.put("created", a.getCreateTime());
        return map;
    }


    public String uploadCover(Long artistId, MultipartFile file) throws IOException {
        Artist artist = musicMapper.findArtistById(artistId);
        if (artist == null) {
            throw new IllegalArgumentException("艺术家不存在: " + artistId);
        }

        Files.createDirectories(coversDir);

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }
        String fileName = "artist-" + artistId + "-" + System.currentTimeMillis() + ext;
        Path dest = coversDir.resolve(fileName);

                if (!dest.normalize().startsWith(coversDir)) {
            throw new IOException("封面路径非法: " + fileName);
        }

        file.transferTo(dest.toFile());

                String coverUrl = fileName;

                musicMapper.updateArtist(artistId, null, null, null, null, coverUrl, null);

        log.info("艺术家封面上传: artistId={}, path={}", artistId, fileName);
        return coverUrl;
    }

    private Integer toInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
