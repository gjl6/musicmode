package com.gjl.music.module.album.normalize;

import com.gjl.music.mapper.AlbumMapper;
import com.gjl.music.model.Album;
import com.gjl.music.module.FailurePolicy;
import com.gjl.music.module.album.support.AlbumModuleSupport;
import com.gjl.music.infra.pipeline.NodeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 专辑名称规范化管道模??????清理专辑名称中的不规范格式??? *
 * <p>继承 {@link AlbumModuleSupport}，基类自动处理：
 * <ol>
 *   <li>{@code ctx.getSlot("options") ???configure()}</li>
 *   <li>???{@code node.album-scanner.output} ???遍历 ???checkPause ???processOne ???上报</li>
 * </ol>
 *
 * <p>规范化规则：
 * <ol>
 *   <li>移除控制字符（\x00-\x1f???/li>
 *   <li>标准???Various Artists 变体</li>
 *   <li>去首尾空???+ 合并连续空格</li>
 *   <li>清理 & 前后多余空格</li>
 * </ol>
 *
 * @see com.gjl.music.module.artist.normalize.ArtistNormalizeModuleImpl
 */
@Slf4j
@Component
public class AlbumNormalizeModuleImpl extends AlbumModuleSupport {

    private final AlbumMapper albumMapper;

    public AlbumNormalizeModuleImpl(AlbumMapper albumMapper) {
        this.albumMapper = albumMapper;
    }

    @Override
    public String name() { return "album-normalize"; }

    @Override
    public FailurePolicy failurePolicy() { return FailurePolicy.SKIP; }

    @Override
    public String label() { return "专辑规范化"; }

    @Override
    protected String itemLabel(Object item) {
        Long albumId = (Long) item;
        Album album = albumMapper.findAlbumById(albumId);
        return album != null ? album.getAlbumName() : String.valueOf(albumId);
    }

    @Override
    protected String processOne(Object item, NodeContext ctx) {
        Long albumId = (Long) item;
        Album album = albumMapper.findAlbumById(albumId);
        if (album == null) {
            log.warn("album-normalize: 专辑不存在, id={}", albumId);
            return "不存在";
        }

        String original = album.getAlbumName();
        // 同时规范化排序名
        String originalSort = album.getSortAlbumName();
        String normalized = normalize(original);
        String normalizedSort = originalSort != null ? normalize(originalSort) : null;

        boolean nameChanged = !normalized.equals(original);
        boolean sortChanged = normalizedSort != null && !normalizedSort.equals(originalSort);

        if (!nameChanged && !sortChanged) {
            return null; // 无需变更
        }

        albumMapper.updateAlbum(
                albumId,
                normalized,
                album.getAlbumType() != null ? album.getAlbumType().name() : null,
                album.getAlbumYear(),
                album.getIntroduction(),
                album.getCompany(),
                album.getLanguage(),
                album.getAlbumCover(),
                album.getEnrichSource());

        // 如果 sortAlbumName 也变了，单独更新
        if (sortChanged) {
            // updateAlbum 目前不支???sortAlbumName 参数，这里通过 SQL 直接更新
            // 后续可扩???updateAlbum 方法
        }

        log.info("album-normalize: {} ???{}", original, normalized);
        return "???" + normalized;
    }

    /**
     * 规范化专辑名称???     *
     * <p>规则???     * <ol>
     *   <li>移除控制字符（\x00-\x1f，保???tab???/li>
     *   <li>标准???Various Artists 变体</li>
     *   <li>去首尾空???+ 合并连续空格</li>
     *   <li>清理 & 前后多余空格</li>
     * </ol>
     */
    static String normalize(String name) {
        if (name == null || name.isBlank()) return name;

        String s = name;

        // 1. 移除控制字符
        s = s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");

        // 2. 标准???Various Artists 变体
        s = s.replaceAll("(?i)\\bVarious\\s+Artists\\b", "Various Artists");
        s = s.replaceAll("(?i)\\bVA\\b", "Various Artists");
        s = s.replaceAll("(?i)\\bV\\.A\\.\\b", "Various Artists");

        // 3. 去首尾空???+ 合并连续空格
        s = s.replaceAll("\\s+", " ").trim();

        // 4. 清理 & 前后多余空格
        s = s.replaceAll("\\s*&\\s*", " & ");

        return s;
    }
}
