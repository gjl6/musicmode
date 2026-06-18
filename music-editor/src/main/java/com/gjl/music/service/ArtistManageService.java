package com.gjl.music.service;

import com.gjl.music.mapper.MusicMapper;
import com.gjl.music.model.Artist;
import com.gjl.music.module.artist.artistmerge.MergeGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
public class ArtistManageService {

    private final MusicMapper musicMapper;

    public ArtistManageService(MusicMapper musicMapper) {
        this.musicMapper = musicMapper;
    }


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
            artists = musicMapper.findArtistsByIds(pageIds);
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

        List<Map<String, Object>> rows = artists.stream()
                .map(this::toArtistMap)
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
            case "all"        -> musicMapper.findAllArtistIds(letter, keyword, minSongs, maxSongs, style, country);
            case "letter"     -> musicMapper.findArtistIdsByLetter(letter, keyword, minSongs, maxSongs, style, country);
            case "keyword"    -> musicMapper.findArtistIdsByKeyword(keyword, letter, minSongs, maxSongs, style, country);
            case "incomplete" -> musicMapper.findIncompleteArtistIds(letter, keyword, minSongs, maxSongs, style, country);
            case "unenriched" -> musicMapper.findUnenrichedArtistIds(letter, keyword, minSongs, maxSongs, style, country);
            case "nonstandard"-> musicMapper.findNonstandardArtistIds(letter, keyword, minSongs, maxSongs, style, country);
            case "naked"      -> musicMapper.findNakedArtistIds(letter, keyword, minSongs, maxSongs, style, country);
            case "duplicates" -> musicMapper.findDuplicateArtistIds(letter, keyword, minSongs, maxSongs, style, country);
            default -> {
                log.warn("ArtistManageService: 未知 mode '{}'，回退 all", mode);
                yield musicMapper.findAllArtistIds(letter, keyword, minSongs, maxSongs, style, country);
            }
        };
    }


    public List<Map<String, Object>> findDuplicates() {
        List<Map<String, Object>> dups = musicMapper.findDuplicateArtistNames();
        if (dups == null || dups.isEmpty()) {
            return List.of();
        }

        return dups.stream().map(row -> {
            String normName = (String) row.get("normName");
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("normName", normName);
            group.put("count", row.get("cnt"));

            List<Artist> artists = musicMapper.findArtistsByNormalizedName(normName);
            if (artists != null) {
                group.put("artists", artists.stream()
                        .map(this::toArtistMap)
                        .collect(Collectors.toList()));
            }
            return group;
        }).collect(Collectors.toList());
    }


    private Map<String, Object> toArtistMap(Artist a) {
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
}
