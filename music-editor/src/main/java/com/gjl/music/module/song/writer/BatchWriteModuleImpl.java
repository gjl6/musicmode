package com.gjl.music.module.song.writer;

import com.gjl.music.editor.mapper.SongManageMapper;
import com.gjl.music.model.*;
import com.gjl.music.module.FailurePolicy;
import com.gjl.music.module.song.support.MetadataGapFillingModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 批量写入模块：GapFillingModule 自动处理"DB 缓存 → 补缺 → processItem → 写库 → 写文件"，
 * 我们只需覆写 {@link #processItem} 把前端模板的非空字段覆盖到已有元数据上。
 */
@Slf4j
@Component
public class BatchWriteModuleImpl extends MetadataGapFillingModule implements BatchWriteModule {

    private MusicMetadata template;

    public BatchWriteModuleImpl(SongManageMapper songManageMapper) {
        super(songManageMapper);
    }

    @Override public String name() { return "batch-write"; }
    @Override public FailurePolicy failurePolicy() { return FailurePolicy.SKIP; }
    @Override public String label() { return "批量写入"; }

    @SuppressWarnings("unchecked")
    @Override
    public void configure(Map<String, Object> options) {
        if (options != null && options.get("metadata") instanceof Map<?, ?> m) {
            this.template = buildTemplate((Map<String, Object>) m);
            log.debug("batch-write 模板: songs={}, albums={}, artists={}, styles={}, lyrics={}",
                    template.getImmutableSongs().size(),
                    template.getImmutableAlbums().size(),
                    template.getImmutableArtists().size(),
                    template.getImmutableStyles().size(),
                    template.getImmutableLyrics().size());
        }
    }

    @Override
    protected MusicMetadata processItem(MusicMetadata existing) {
        if (template == null) return existing;
        return merge(template, existing);
    }

    // ── 模板构建：只添加有实际数据的实体 ──

    @SuppressWarnings("unchecked")
    static MusicMetadata buildTemplate(Map<String, Object> metaMap) {
        MusicMetadata meta = new MusicMetadata();

        // song
        if (metaMap.get("songs") instanceof List<?> list && !list.isEmpty()
                && list.get(0) instanceof Map<?, ?> s) {
            Song.SongBuilder sb = Song.builder();
            boolean has = false;
            if (v(s, "title"))       { sb.title(str(s, "title"));             has = true; }
            if (v(s, "year"))        { sb.year(str(s, "year"));               has = true; }
            if (v(s, "language"))    { sb.language(str(s, "language"));       has = true; }
            if (v(s, "composer"))    { sb.composer(str(s, "composer"));       has = true; }
            if (v(s, "lyricist"))    { sb.lyricist(str(s, "lyricist"));       has = true; }
            if (s.get("trackNumber") instanceof Number n) { sb.trackNumber(n.intValue()); has = true; }
            if (s.get("discNumber")  instanceof Number n) { sb.discNumber(n.intValue());  has = true; }
            if (v(s, "coverPath"))   { sb.coverPath(str(s, "coverPath"));     has = true; }
            if (has) meta.addSong(sb.build());
        }

        // artists
        if (metaMap.get("artists") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> a && v(a, "artistName")) {
                    Artist.ArtistBuilder ab = Artist.builder().artistName(str(a, "artistName"));
                    if (a.get("gender")       instanceof Number n) ab.gender(n.intValue());
                    if (v(a, "country"))       ab.country(str(a, "country"));
                    if (v(a, "introduction"))  ab.introduction(str(a, "introduction"));
                    if (v(a, "artistCover"))   ab.artistCover(str(a, "artistCover"));
                    meta.addArtist(ab.build());
                }
            }
        }

        // albums
        if (metaMap.get("albums") instanceof List<?> list && !list.isEmpty()
                && list.get(0) instanceof Map<?, ?> a && v(a, "albumName")) {
            Album.AlbumBuilder ab = Album.builder().albumName(str(a, "albumName"));
            if (a.get("albumType") instanceof String t) {
                try { ab.albumType(AlbumType.valueOf(t)); } catch (Exception ignored) {}
            }
            if (a.get("albumYear")    instanceof Number n) ab.albumYear(n.intValue());
            if (v(a, "company"))       ab.company(str(a, "company"));
            if (v(a, "introduction"))  ab.introduction(str(a, "introduction"));
            if (v(a, "language"))      ab.language(str(a, "language"));
            meta.addAlbum(ab.build());
        }

        // styles
        if (metaMap.get("styles") instanceof List<?> list && !list.isEmpty()
                && list.get(0) instanceof Map<?, ?> st && v(st, "styleName")) {
            Style.StyleBuilder sb = Style.builder().styleName(str(st, "styleName"));
            if (v(st, "description")) sb.description(str(st, "description"));
            meta.addStyle(sb.build());
        }

        // lyrics
        if (metaMap.get("lyrics") instanceof List<?> list && !list.isEmpty()
                && list.get(0) instanceof Map<?, ?> l && v(l, "content")) {
            Lyric.LyricBuilder lb = Lyric.builder().content(str(l, "content"));
            if (l.get("type")    instanceof String t) lb.type(LyricType.valueOf(t));
            if (v(l, "lrcPath")) lb.lrcPath(str(l, "lrcPath"));
            meta.addLyric(lb.build());
        }

        return meta;
    }

    // ── 合并：用 toBuilder 保留已有字段，模板非空字段覆盖 ──

    static MusicMetadata merge(MusicMetadata template, MusicMetadata existing) {
        MusicMetadata result = new MusicMetadata();

        // songs: toBuilder 保留全部已有字段
        for (Song es : existing.getImmutableSongs()) {
            Song.SongBuilder sb = es.toBuilder();
            if (!template.getImmutableSongs().isEmpty()) {
                Song ts = template.getImmutableSongs().get(0);
                if (nns(ts.getTitle()))      sb.title(ts.getTitle());
                if (nns(ts.getYear()))       sb.year(ts.getYear());
                if (nns(ts.getLanguage()))   sb.language(ts.getLanguage());
                if (nns(ts.getComposer()))   sb.composer(ts.getComposer());
                if (nns(ts.getLyricist()))   sb.lyricist(ts.getLyricist());
                if (ts.getTrackNumber() != null) sb.trackNumber(ts.getTrackNumber());
                if (ts.getDiscNumber()  != null) sb.discNumber(ts.getDiscNumber());
                if (nns(ts.getCoverPath()))  sb.coverPath(ts.getCoverPath());
            }
            result.addSong(sb.build());
        }

        // albums: toBuilder 保留已有字段
        for (Album ea : existing.getImmutableAlbums()) {
            Album.AlbumBuilder ab = ea.toBuilder();
            if (!template.getImmutableAlbums().isEmpty()) {
                Album ta = template.getImmutableAlbums().get(0);
                if (nns(ta.getAlbumName()))    ab.albumName(ta.getAlbumName());
                if (ta.getAlbumYear() != null)  ab.albumYear(ta.getAlbumYear());
                if (ta.getAlbumType() != null)  ab.albumType(ta.getAlbumType());
                if (nns(ta.getCompany()))       ab.company(ta.getCompany());
                if (nns(ta.getIntroduction()))  ab.introduction(ta.getIntroduction());
                if (nns(ta.getLanguage()))      ab.language(ta.getLanguage());
            }
            result.addAlbum(ab.build());
        }

        // artists / styles / lyrics：模板有则全量覆盖，否则保留
        if (!template.getImmutableArtists().isEmpty()) {
            template.getImmutableArtists().forEach(result::addArtist);
        } else {
            existing.getImmutableArtists().forEach(result::addArtist);
        }
        if (!template.getImmutableStyles().isEmpty()) {
            template.getImmutableStyles().forEach(result::addStyle);
        } else {
            existing.getImmutableStyles().forEach(result::addStyle);
        }
        if (!template.getImmutableLyrics().isEmpty()) {
            template.getImmutableLyrics().forEach(result::addLyric);
        } else {
            existing.getImmutableLyrics().forEach(result::addLyric);
        }

        return result;
    }

    // ── 辅助 ──

    private static String str(Map<?, ?> m, String k) {
        Object v = m.get(k);
        return v instanceof String s && !s.isBlank() ? s : null;
    }

    private static boolean v(Map<?, ?> m, String k) { return str(m, k) != null; }
    private static boolean nns(String s) { return s != null && !s.isBlank(); }
}
