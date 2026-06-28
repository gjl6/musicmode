package com.gjl.music.service.artist.impl;

import com.gjl.music.dto.ArtistResult;
import com.gjl.music.mapper.ArtistMapper;
import com.gjl.music.editor.mapper.ArtistManageMapper;
import com.gjl.music.model.Artist;
import com.gjl.music.search.EntityChangeEvent;
import com.gjl.music.service.artist.ArtistManageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ArtistManageServiceImpl implements ArtistManageService {

    private final ArtistMapper artistMapper;
    private final ArtistManageMapper artistManageMapper;
    private final ApplicationEventPublisher eventPublisher;

    public ArtistManageServiceImpl(ArtistMapper artistMapper,
                                    ArtistManageMapper artistManageMapper,
                                    ApplicationEventPublisher eventPublisher) {
        this.artistMapper = artistMapper;
        this.artistManageMapper = artistManageMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Map<String, Object> listArtists(String mode, String keyword, String letter,
                                           Integer minSongs, Integer maxSongs,
                                           String style, String country,
                                           List<Long> ids,
                                           String sort, int offset, int limit) {
        List<Long> artistIds;
        int total;

        String effectiveMode = (mode != null && !mode.isBlank()) ? mode : "all";

        if ("ids".equals(effectiveMode) && ids != null && !ids.isEmpty()) {
            artistIds = ids;
            total = ids.size();
        } else {
            artistIds = queryArtistIdsByMode(effectiveMode, keyword, letter,
                    minSongs, maxSongs, style, country);
            total = artistIds.size();
        }

        int fromIndex = Math.min(offset, total);
        int toIndex = Math.min(offset + limit, total);
        List<Long> pageIds = artistIds.subList(fromIndex, toIndex);

        List<Artist> artists;
        if (!pageIds.isEmpty()) {
            artists = artistMapper.findArtistsByIds(pageIds);
            Map<Long, Artist> idMap = new LinkedHashMap<>();
            for (Artist a : artists) {
                idMap.put(Long.parseLong(a.getId()), a);
            }
            artists = pageIds.stream()
                    .map(idMap::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } else {
            artists = List.of();
        }

        List<ArtistResult> rows = artists.stream()
                .map(this::toArtistResult)
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artists", rows);
        result.put("total", total);
        return result;
    }

    private List<Long> queryArtistIdsByMode(String mode, String keyword, String letter,
                                            Integer minSongs, Integer maxSongs,
                                            String style, String country) {
        return switch (mode) {
            case "all"        -> artistManageMapper.findAllArtistIds(letter, keyword, minSongs, maxSongs, style, country);
            case "letter"     -> artistManageMapper.findArtistIdsByLetter(letter, keyword, minSongs, maxSongs, style, country);
            case "keyword"    -> artistManageMapper.findArtistIdsByKeyword(keyword, letter, minSongs, maxSongs, style, country);
            case "incomplete" -> artistManageMapper.findIncompleteArtistIds(letter, keyword, minSongs, maxSongs, style, country);
            case "unenriched" -> artistManageMapper.findUnenrichedArtistIds(letter, keyword, minSongs, maxSongs, style, country);
            case "nonstandard"-> artistManageMapper.findNonstandardArtistIds(letter, keyword, minSongs, maxSongs, style, country);
            case "naked"      -> artistManageMapper.findNakedArtistIds(letter, keyword, minSongs, maxSongs, style, country);
            case "duplicates" -> artistManageMapper.findDuplicateArtistIds(letter, keyword, minSongs, maxSongs, style, country);
            default -> {
                log.warn("ArtistManageService: 未知 mode '{}'，回退 all", mode);
                yield artistManageMapper.findAllArtistIds(letter, keyword, minSongs, maxSongs, style, country);
            }
        };
    }

    @Override
    public Artist findArtistById(Long id) {
        return artistMapper.findArtistById(id);
    }

    @Override
    public void updateArtist(Long id, String artistName, String introduction,
                             Integer gender, String country,
                             String artistCover, String enrichSource) {
        artistMapper.updateArtist(id, artistName, introduction, gender,
                country, artistCover, enrichSource);
        // ★ 发布索引更新事件
        eventPublisher.publishEvent(EntityChangeEvent.artistUpdated(id));
    }

    @Override
    public List<Map<String, Object>> findDuplicates() {
        List<Map<String, Object>> dups = artistManageMapper.findDuplicateArtistNames();
        if (dups == null || dups.isEmpty()) return List.of();
        return dups.stream().map(row -> {
            String normName = (String) row.get("normName");
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("normName", normName);
            group.put("count", row.get("cnt"));
            List<Artist> artists = artistManageMapper.findArtistsByNormalizedName(normName);
            if (artists != null) {
                group.put("artists", artists.stream()
                        .map(this::toArtistResult)
                        .collect(Collectors.toList()));
            }
            return group;
        }).collect(Collectors.toList());
    }

    @Override
    public ArtistResult toArtistResult(Artist a) {
        return ArtistResult.fromArtist(a).toBuilder()
                .coverArt(a.getArtistCover())
                .build();
    }
}
