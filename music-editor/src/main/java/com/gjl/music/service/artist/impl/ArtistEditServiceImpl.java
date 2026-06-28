package com.gjl.music.service.artist.impl;

import com.gjl.music.mapper.ArtistMapper;
import com.gjl.music.model.Artist;
import com.gjl.music.infra.enrich.EnrichHelper;
import com.gjl.music.service.artist.ArtistEditService;
import com.gjl.music.service.song.TagSyncService;
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

/**
 * 艺术家编辑服务 — 更新 DB 并触发文件标签后台同步。
 */
@Slf4j
@Service
public class ArtistEditServiceImpl implements ArtistEditService {

    private final ArtistMapper artistMapper;
    private final TagSyncService tagSyncService;
    private final EnrichHelper enrichHelper;
    private final Path coversDir;

    public ArtistEditServiceImpl(ArtistMapper artistMapper, TagSyncService tagSyncService,
                                  EnrichHelper enrichHelper,
                                  @Value("${music.covers-dir:../covers}") String coversDir) {
        this.artistMapper = artistMapper;
        this.tagSyncService = tagSyncService;
        this.enrichHelper = enrichHelper;
        this.coversDir = Path.of(coversDir).toAbsolutePath().normalize();
    }

    /**
     * 更新艺术家元数据。DB 提交后异步同步所有关联歌曲的文件标签。
     *
     * @param id   艺术家 ID
     * @param body 请求体，可含 name, introduction, gender, country
     * @return 更新后的艺术家 Map
     */
    @Override
    @Transactional
    public Map<String, Object> updateArtist(Long id, Map<String, Object> body) {
        Artist artist = artistMapper.findArtistById(id);
        if (artist == null) {
            throw new IllegalArgumentException("艺术家不存在: " + id);
        }

        String artistName = body.containsKey("name") ? (String) body.get("name") : null;
        String introduction = body.containsKey("introduction") ? (String) body.get("introduction") : null;
        Integer gender = body.containsKey("gender") ? toInt(body.get("gender")) : null;
        String country = body.containsKey("country") ? (String) body.get("country") : null;
        String artistCover = body.containsKey("artistCover") ? (String) body.get("artistCover") : null;

        // 封面: 如果是网络 URL，下载到本地再写库
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

        // 性别校验: 0=未知, 1=男, 2=女, 3=团体
        if (gender != null && (gender < 0 || gender > 3)) {
            throw new IllegalArgumentException("无效的性别值: " + gender + " (有效范围 0-3)");
        }

        artistMapper.updateArtist(id, artistName, introduction, gender, country, artistCover, null);

        Artist updated = artistMapper.findArtistById(id);

        // 后台同步文件标签（保留完整 artist 列表）
        tagSyncService.syncArtistChange(id);

        return Map.of("artist", toArtistMap(updated));
    }

    // ── 映射辅助 ──

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

    /**
     * 上传艺术家封面，保存到 coversDir，返回显示 URL。
     *
     * @param artistId 艺术家 ID
     * @param file     上传的图片文件
     * @return 封面显示 URL（/api/browse/covers/artist-{id}-{ts}.{ext}）
     */
    @Override
    public String uploadCover(Long artistId, MultipartFile file) throws IOException {
        Artist artist = artistMapper.findArtistById(artistId);
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

        // 安全检查：防止路径穿越
        if (!dest.normalize().startsWith(coversDir)) {
            throw new IOException("封面路径非法: " + fileName);
        }

        file.transferTo(dest.toFile());

        // 构建封面相对路径（前端通过 getCoverUrl() 补全 API 前缀）
        String coverUrl = fileName;

        // 更新 DB
        artistMapper.updateArtist(artistId, null, null, null, null, coverUrl, null);

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
