package com.gjl.music.service.song.impl;

import com.gjl.music.service.song.StyleEditService;
import com.gjl.music.service.song.TagSyncService;
import com.gjl.music.mapper.StyleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 风格编辑服务 — 更新 DB 并触发文件标签后台同步。
 * <p>
 * 这是全新能力：此前 Style/Genre 完全只读，没有任何编辑端点。
 */
@Slf4j
@Service
public class StyleEditServiceImpl implements StyleEditService {

    private final StyleMapper styleMapper;
    private final TagSyncService tagSyncService;

    public StyleEditServiceImpl(StyleMapper styleMapper, TagSyncService tagSyncService) {
        this.styleMapper = styleMapper;
        this.tagSyncService = tagSyncService;
    }

    /**
     * 更新风格元数据。DB 提交后异步同步所有关联歌曲的文件标签。
     *
     * @param name 当前风格名称（用于查找）
     * @param body 请求体，可含 name(新名称), description, styleImage
     * @return 更新后的风格 Map
     */
    @Override
    @Transactional
    public Map<String, Object> updateStyle(String name, Map<String, Object> body) {
        Map<String, Object> info = styleMapper.selectStyleInfoByName(name);
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

        styleMapper.updateStyle(id, styleName, description, styleImage);

        // 后台同步文件标签（保留完整 style 列表）
        tagSyncService.syncStyleChange(id);

        // 重新查询返回最新数据
        Map<String, Object> updated = styleMapper.selectStyleInfoByName(
                styleName != null ? styleName : name);
        if (updated == null) {
            // 如果改了名字，用 ID 回退查询
            var style = styleMapper.findStyleById(id);
            if (style != null) {
                return Map.of("style", toStyleMap(style));
            }
        }
        return Map.of("style", updated != null ? updated : Map.of());
    }

    // ── 映射辅助 ──

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
