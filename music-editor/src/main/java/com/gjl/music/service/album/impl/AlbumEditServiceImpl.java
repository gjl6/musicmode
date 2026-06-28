package com.gjl.music.service.album.impl;

import com.gjl.music.mapper.AlbumMapper;
import com.gjl.music.mapper.ArtistMapper;
import com.gjl.music.model.Album;
import com.gjl.music.model.AlbumType;
import com.gjl.music.infra.enrich.EnrichHelper;
import com.gjl.music.service.album.AlbumEditService;
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
 * 专辑编辑服务 — 更新 DB 并触发文件标签后台同步。
 */
@Slf4j
@Service
public class AlbumEditServiceImpl implements AlbumEditService {

    private final AlbumMapper albumMapper;
    private final ArtistMapper artistMapper;
    private final TagSyncService tagSyncService;
    private final EnrichHelper enrichHelper;
    private final Path coversDir;

    public AlbumEditServiceImpl(AlbumMapper albumMapper, ArtistMapper artistMapper,
                                 TagSyncService tagSyncService,
                                 EnrichHelper enrichHelper,
                                 @Value("${music.covers-dir:../covers}") String coversDir) {
        this.albumMapper = albumMapper;
        this.artistMapper = artistMapper;
        this.tagSyncService = tagSyncService;
        this.enrichHelper = enrichHelper;
        this.coversDir = Path.of(coversDir).toAbsolutePath().normalize();
    }

    /**
     * 更新专辑元数据。DB 提交后异步同步所有关联歌曲的文件标签。
     *
     * @param id   专辑 ID
     * @param body 请求体，可含 name, genre(albumType), year, introduction, company, language
     * @return 更新后的专辑 Map
     */
    @Override
    @Transactional
    public Map<String, Object> updateAlbum(Long id, Map<String, Object> body) {
        Album album = albumMapper.findAlbumById(id);
        if (album == null) {
            throw new IllegalArgumentException("专辑不存在: " + id);
        }

        String albumName = body.containsKey("name") ? (String) body.get("name") : null;
        String albumType = null;
        if (body.containsKey("genre")) {
            String raw = ((String) body.get("genre")).toUpperCase();
            try {
                AlbumType.valueOf(raw);
                albumType = raw;
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("无效的专辑类型: " + body.get("genre"));
            }
        }
        Integer albumYear = body.containsKey("year") ? toInt(body.get("year")) : null;
        String introduction = body.containsKey("introduction") ? (String) body.get("introduction") : null;
        String company = body.containsKey("company") ? (String) body.get("company") : null;
        String language = body.containsKey("language") ? (String) body.get("language") : null;

        albumMapper.updateAlbum(id, albumName, albumType, albumYear, introduction, company, language, null, null);

        Album updated = albumMapper.findAlbumById(id);

        // 后台同步文件标签
        tagSyncService.syncAlbumChange(id);

        return Map.of("album", toAlbumMap(updated));
    }

    /**
     * 上传专辑封面，保存到 coversDir，返回显示 URL。
     *
     * @param albumId 专辑 ID
     * @param file    上传的图片文件
     * @return 封面显示 URL
     */
    @Override
    public String uploadCover(Long albumId, MultipartFile file) throws IOException {
        Album album = albumMapper.findAlbumById(albumId);
        if (album == null) {
            throw new IllegalArgumentException("专辑不存在: " + albumId);
        }

        Files.createDirectories(coversDir);

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }
        String fileName = "album-" + albumId + "-" + System.currentTimeMillis() + ext;
        Path dest = coversDir.resolve(fileName);

        // 安全检查：防止路径穿越
        if (!dest.normalize().startsWith(coversDir)) {
            throw new IOException("封面路径非法: " + fileName);
        }

        file.transferTo(dest.toFile());

        String coverUrl = fileName;

        // 更新 DB
        albumMapper.updateAlbum(albumId, null, null, null, null, null, null, coverUrl, null);

        log.info("专辑封面上传: albumId={}, path={}", albumId, fileName);
        return coverUrl;
    }

    // ── 映射辅助 ──

    Map<String, Object> toAlbumMap(Album a) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", a.getId());
        map.put("name", a.getAlbumName());
        map.put("artist", resolveArtistName(a.getArtistId()));
        map.put("artistId", a.getArtistId());
        map.put("coverArt", a.getAlbumCover());
        map.put("songCount", a.getSongCount());
        map.put("year", a.getAlbumYear());
        map.put("genre", a.getAlbumType() != null ? a.getAlbumType().getDisplayName() : null);
        map.put("albumType", a.getAlbumType() != null ? a.getAlbumType().name() : null);
        map.put("introduction", a.getIntroduction());
        map.put("company", a.getCompany());
        map.put("language", a.getLanguage());
        map.put("created", a.getCreateTime());
        return map;
    }

    private String resolveArtistName(Integer artistId) {
        if (artistId == null) return null;
        var artist = artistMapper.findArtistById(artistId.longValue());
        return artist != null ? artist.getArtistName() : null;
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
