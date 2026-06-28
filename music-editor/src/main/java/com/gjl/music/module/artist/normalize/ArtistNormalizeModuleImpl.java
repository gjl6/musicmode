package com.gjl.music.module.artist.normalize;

import com.gjl.music.mapper.ArtistMapper;
import com.gjl.music.model.Artist;
import com.gjl.music.module.FailurePolicy;
import com.gjl.music.module.artist.support.ArtistModuleSupport;
import com.gjl.music.infra.pipeline.NodeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 艺术家名称规范化管道模块 — 清理艺术家名称中的不规范格式。
 *
 * <p>继承 {@link ArtistModuleSupport}，基类自动处理：
 * <ol>
 *   <li>{@code ctx.getSlot("options") → configure()}</li>
 *   <li>读 {@code node.artist-scanner.output} → 遍历 → checkPause → processOne → 上报</li>
 * </ol>
 *
 * <p>规范化规则：
 * <ol>
 *   <li>移除控制字符（\x00-\x1f）</li>
 *   <li>标准化 feat. / ft. / Featuring 变体</li>
 *   <li>去首尾空格 + 合并连续空格</li>
 *   <li>清理 & 前后多余空格</li>
 * </ol>
 */
@Slf4j
@Component
public class ArtistNormalizeModuleImpl extends ArtistModuleSupport {

    private final ArtistMapper musicMapper;

    public ArtistNormalizeModuleImpl(ArtistMapper musicMapper) {
        this.musicMapper = musicMapper;
    }

    @Override
    public String name() { return "artist-normalize"; }

    @Override
    public FailurePolicy failurePolicy() { return FailurePolicy.SKIP; }

    @Override
    public String label() { return "艺术家规范化"; }

    @Override
    protected String itemLabel(Object item) {
        Long artistId = (Long) item;
        Artist artist = musicMapper.findArtistById(artistId);
        return artist != null ? artist.getArtistName() : String.valueOf(artistId);
    }

    @Override
    protected String processOne(Object item, NodeContext ctx) {
        Long artistId = (Long) item;
        Artist artist = musicMapper.findArtistById(artistId);
        if (artist == null) {
            log.warn("artist-normalize: 艺术家不存在 id={}", artistId);
            return "不存在";
        }

        String original = artist.getArtistName();
        String normalized = normalize(original);

        if (normalized.equals(original)) {
            return null; // 无需变更
        }

        musicMapper.updateArtist(
                artistId,
                normalized,
                artist.getIntroduction(),
                artist.getGender(),
                artist.getCountry(),
                artist.getArtistCover(),
                artist.getEnrichSource());

        log.info("artist-normalize: {} → {}", original, normalized);
        return "→ " + normalized;
    }

    /**
     * 规范化艺术家名称。
     *
     * <p>规则：
     * <ol>
     *   <li>移除控制字符（\x00-\x1f，保留 tab）</li>
     *   <li>标准化 feat. / ft. / Featuring 等常见分隔符 → "ft."</li>
     *   <li>去首尾空格 + 合并连续空格</li>
     *   <li>清理 & 前后多余空格</li>
     * </ol>
     */
    static String normalize(String name) {
        if (name == null || name.isBlank()) return name;

        String s = name;

        // 1. 移除控制字符
        s = s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");

        // 2. 标准化 feat. 变体 → ft.
        s = s.replaceAll("(?i)\\bFeat\\.?\\b", "ft.");
        s = s.replaceAll("(?i)\\bFeaturing\\b", "ft.");
        s = s.replaceAll("(?i)\\bFt\\.?\\b", "ft.");

        // 3. 去首尾空格 + 合并连续空格
        s = s.replaceAll("\\s+", " ").trim();

        // 4. 清理 & 前后多余空格
        s = s.replaceAll("\\s*&\\s*", " & ");

        return s;
    }
}
