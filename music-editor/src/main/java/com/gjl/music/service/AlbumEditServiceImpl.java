package com.gjl.music.service;

import com.gjl.music.mapper.MusicMapper;
import com.gjl.music.model.Album;
import com.gjl.music.model.AlbumType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;


@Slf4j
@Service
public class AlbumEditServiceImpl {

    private final MusicMapper musicMapper;
    private final TagSyncService tagSyncService;

    public AlbumEditServiceImpl(MusicMapper musicMapper, TagSyncService tagSyncService) {
        this.musicMapper = musicMapper;
        this.tagSyncService = tagSyncService;
    }


    @Transactional
    public Map<String, Object> updateAlbum(Long id, Map<String, Object> body) {
        Album album = musicMapper.findAlbumById(id);
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

        musicMapper.updateAlbum(id, albumName, albumType, albumYear, introduction, company, language);

        Album updated = musicMapper.findAlbumById(id);

                tagSyncService.syncAlbumChange(id);

        return Map.of("album", toAlbumMap(updated));
    }


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
        var artist = musicMapper.findArtistById(artistId.longValue());
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
