package com.gjl.music.service.album.impl;

import com.gjl.music.dto.AlbumResult;
import com.gjl.music.mapper.AlbumMapper;
import com.gjl.music.editor.mapper.AlbumManageMapper;
import com.gjl.music.mapper.ArtistMapper;
import com.gjl.music.model.Album;
import com.gjl.music.search.EntityChangeEvent;
import com.gjl.music.service.album.AlbumManageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AlbumManageServiceImpl implements AlbumManageService {

    private final AlbumMapper albumMapper;
    private final AlbumManageMapper albumManageMapper;
    private final ArtistMapper artistMapper;
    private final ApplicationEventPublisher eventPublisher;

    public AlbumManageServiceImpl(AlbumMapper albumMapper,
                                   AlbumManageMapper albumManageMapper,
                                   ArtistMapper artistMapper,
                                   ApplicationEventPublisher eventPublisher) {
        this.albumMapper = albumMapper;
        this.albumManageMapper = albumManageMapper;
        this.artistMapper = artistMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Map<String, Object> listAlbums(String mode, String keyword, String letter,
                                           Long artistId,
                                           Integer minSongs, Integer maxSongs,
                                           List<Long> ids,
                                           String sort, int offset, int limit) {
        List<Long> albumIds;
        int total;

        String effectiveMode = (mode != null && !mode.isBlank()) ? mode : "all";

        if ("ids".equals(effectiveMode) && ids != null && !ids.isEmpty()) {
            albumIds = ids;
            total = ids.size();
        } else {
            albumIds = queryAlbumIdsByMode(effectiveMode, keyword, letter,
                    artistId, minSongs, maxSongs);
            total = albumIds.size();
        }

        int fromIndex = Math.min(offset, total);
        int toIndex = Math.min(offset + limit, total);
        List<Long> pageIds = albumIds.subList(fromIndex, toIndex);

        List<Album> albums;
        if (!pageIds.isEmpty()) {
            albums = albumMapper.findAlbumsByIds(pageIds);
            Map<Long, Album> idMap = new LinkedHashMap<>();
            for (Album a : albums) {
                idMap.put(Long.parseLong(a.getId()), a);
            }
            albums = pageIds.stream()
                    .map(idMap::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } else {
            albums = List.of();
        }

        List<AlbumResult> rows = albums.stream()
                .map(this::toAlbumResult)
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("albums", rows);
        result.put("total", total);
        return result;
    }

    private List<Long> queryAlbumIdsByMode(String mode, String keyword, String letter,
                                            Long artistId,
                                            Integer minSongs, Integer maxSongs) {
        return switch (mode) {
            case "all"        -> albumManageMapper.findAllAlbumIds(letter, keyword, artistId, minSongs, maxSongs);
            case "letter"     -> albumManageMapper.findAlbumIdsByLetter(letter, keyword, artistId, minSongs, maxSongs);
            case "keyword"    -> albumManageMapper.findAlbumIdsByKeyword(keyword, letter, artistId, minSongs, maxSongs);
            case "incomplete" -> albumManageMapper.findIncompleteAlbumIds(letter, keyword, artistId, minSongs, maxSongs);
            case "unenriched" -> albumManageMapper.findUnenrichedAlbumIds(letter, keyword, artistId, minSongs, maxSongs);
            case "naked"      -> albumManageMapper.findNakedAlbumIds(letter, keyword, artistId, minSongs, maxSongs);
            case "duplicates" -> albumManageMapper.findDuplicateAlbumIds(letter, keyword, artistId, minSongs, maxSongs);
            default -> {
                log.warn("AlbumManageService: 未知 mode '{}'，回退 all", mode);
                yield albumManageMapper.findAllAlbumIds(letter, keyword, artistId, minSongs, maxSongs);
            }
        };
    }

    @Override
    public List<Map<String, Object>> findDuplicates() {
        List<Map<String, Object>> dups = albumManageMapper.findDuplicateAlbumNames();
        if (dups == null || dups.isEmpty()) return List.of();
        return dups.stream().map(row -> {
            String normName = (String) row.get("normName");
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("normName", normName);
            group.put("count", row.get("cnt"));
            List<Album> albums = albumManageMapper.findAlbumsByNormalizedName(normName);
            if (albums != null) {
                group.put("albums", albums.stream()
                        .map(this::toAlbumResult)
                        .collect(Collectors.toList()));
            }
            return group;
        }).collect(Collectors.toList());
    }

    @Override
    public Album findAlbumById(Long id) {
        return albumMapper.findAlbumById(id);
    }

    @Override
    public void updateAlbum(Long id, String albumName, String albumType,
                            Integer albumYear, String introduction,
                            String company, String language,
                            String albumCover, String enrichSource) {
        albumMapper.updateAlbum(id, albumName, albumType, albumYear,
                introduction, company, language, albumCover, enrichSource);
        // ★ 发布索引更新事件
        eventPublisher.publishEvent(EntityChangeEvent.albumUpdated(id));
    }

    @Override
    public String resolveArtistName(Integer artistId) {
        if (artistId == null) return null;
        var artist = artistMapper.findArtistById(artistId.longValue());
        return artist != null ? artist.getArtistName() : null;
    }

    @Override
    public AlbumResult toAlbumResult(Album a) {
        return AlbumResult.fromAlbum(a).toBuilder()
                .coverArt(a.getAlbumCover())
                .build();
    }
}
