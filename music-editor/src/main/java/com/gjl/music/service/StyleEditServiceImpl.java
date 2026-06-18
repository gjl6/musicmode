package com.gjl.music.service;

import com.gjl.music.mapper.MusicMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;


@Slf4j
@Service
public class StyleEditServiceImpl {

    private final MusicMapper musicMapper;
    private final TagSyncService tagSyncService;

    public StyleEditServiceImpl(MusicMapper musicMapper, TagSyncService tagSyncService) {
        this.musicMapper = musicMapper;
        this.tagSyncService = tagSyncService;
    }


    @Transactional
    public Map<String, Object> updateStyle(String name, Map<String, Object> body) {
        Map<String, Object> info = musicMapper.selectStyleInfoByName(name);
        if (info == null || info.isEmpty()) {
            throw new IllegalArgumentException("风格不存在: " + name);
        }

        Long id = toLong(info.get("id"));
        if (id == null) {
            throw new IllegalArgumentException("无法获取风格 ID: " + name);
        }

        String styleName = body.containsKey("name") ? (String) body.get("name") : null;
        String description = body.containsKey("description") ? (String) body.get("description") : null;
        String styleImage = body.containsKey("styleImage") ? (String) body.get("styleImage") : null;

        musicMapper.updateStyle(id, styleName, description, styleImage);

                tagSyncService.syncStyleChange(id);

                Map<String, Object> updated = musicMapper.selectStyleInfoByName(
                styleName != null ? styleName : name);
        if (updated == null) {
                        var style = musicMapper.findStyleById(id);
            if (style != null) {
                return Map.of("style", toStyleMap(style));
            }
        }
        return Map.of("style", updated != null ? updated : Map.of());
    }


    Map<String, Object> toStyleMap(com.gjl.music.model.Style s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", s.getId());
        map.put("name", s.getStyleName());
        map.put("description", s.getDescription());
        map.put("styleImage", s.getStyleImage());
        map.put("songCount", s.getSongCount());
        map.put("created", s.getCreateTime());
        return map;
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
