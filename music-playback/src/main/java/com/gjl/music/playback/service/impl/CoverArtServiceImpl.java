package com.gjl.music.playback.service.impl;

import com.gjl.music.mapper.AlbumMapper;
import com.gjl.music.mapper.ArtistMapper;
import com.gjl.music.mapper.SongMapper;
import com.gjl.music.mapper.StyleMapper;
import com.gjl.music.model.Album;
import com.gjl.music.model.Artist;
import com.gjl.music.model.Song;
import com.gjl.music.model.Style;
import com.gjl.music.playback.model.Playlist;
import com.gjl.music.playback.service.CoverArtService;
import com.gjl.music.playback.service.PlaylistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 封面解析服务。
 *
 * <p>按 ID 查找封面文件路径，支持歌曲 / 专辑 / 艺术家 / 歌单 / 流派五种实体类型。
 * 实现 fallback 链逻辑：
 *
 * <pre>
 * 歌曲:   song.cover_path
 * 专辑:   album.album_cover → 该专辑下第一首歌的 cover_path
 * 艺术家: artist.artist_cover → 该艺术家某专辑的 album_cover
 *         → 该艺术家某首歌的 cover_path
 * 歌单:   手动封面 → 歌单内歌曲逐级回退：song.cover_path → album 封面 → artist 封面
 * 流派:   style.style_image → 根据流派名自动生成首字 PNG
 * </pre>
 */
@Slf4j
@Service
public class CoverArtServiceImpl implements CoverArtService {

    private final SongMapper songMapper;
    private final AlbumMapper albumMapper;
    private final ArtistMapper artistMapper;
    private final StyleMapper styleMapper;
    private final PlaylistService playlistService;
    private final Path coversDir;

    public CoverArtServiceImpl(SongMapper songMapper,
                               AlbumMapper albumMapper,
                               ArtistMapper artistMapper,
                               StyleMapper styleMapper,
                               PlaylistService playlistService,
                               @Value("${music.covers-dir:../covers}") String coversDir) {
        this.songMapper = songMapper;
        this.albumMapper = albumMapper;
        this.artistMapper = artistMapper;
        this.styleMapper = styleMapper;
        this.playlistService = playlistService;
        this.coversDir = Path.of(coversDir).toAbsolutePath().normalize();
    }

    /**
     * 按 ID 解析封面文件路径。
     *
     * <p>支持前缀编码 ID：{@code song-123} / {@code album-123} / {@code artist-123}。
     * 也兼容旧版裸数字 ID（依次尝试歌曲→专辑→艺术家）。
     *
     * @param id 带前缀的实体 ID 字符串
     * @return 存在且可读的封面文件路径，无封面时返回 null
     */
    @Override
    public Path resolveCoverPath(String id) {
        if (id == null || id.isBlank()) {
            log.info("[CoverArt] 入参为空");
            return null;
        }
        log.info("[CoverArt] 请求封面: id={}", id);

        // 前缀编码：精确匹配实体类型
        if (id.startsWith("song-")) {
            Long numId = parseLong(id.substring(5));
            log.info("[CoverArt] 前缀=song, numId={}", numId);
            Path result = numId != null ? resolveFromSong(numId) : null;
            log.info("[CoverArt] 歌曲封面结果: id={}, result={}", id, result);
            return result;
        }
        // Subsonic 标准: al-{id} (album cover art)
        if (id.startsWith("al-")) {
            Long numId = parseLong(id.substring(3));
            log.info("[CoverArt] 前缀=al (Subsonic album), numId={}", numId);
            Path result = numId != null ? resolveFromAlbum(numId) : null;
            log.info("[CoverArt] Subsonic 专辑封面结果: id={}, result={}", id, result);
            return result;
        }
        if (id.startsWith("album-")) {
            Long numId = parseLong(id.substring(6));
            log.info("[CoverArt] 前缀=album, numId={}", numId);
            Path result = numId != null ? resolveFromAlbum(numId) : null;
            log.info("[CoverArt] 专辑封面结果: id={}, result={}", id, result);
            return result;
        }
        // Subsonic 标准: ar-{id} (artist cover art)
        if (id.startsWith("ar-")) {
            Long numId = parseLong(id.substring(3));
            log.info("[CoverArt] 前缀=ar (Subsonic artist), numId={}", numId);
            Path result = numId != null ? resolveFromArtist(numId) : null;
            log.info("[CoverArt] Subsonic 艺术家封面结果: id={}, result={}", id, result);
            return result;
        }
        if (id.startsWith("artist-")) {
            Long numId = parseLong(id.substring(7));
            log.info("[CoverArt] 前缀=artist, numId={}", numId);
            Path result = numId != null ? resolveFromArtist(numId) : null;
            log.info("[CoverArt] 艺术家封面结果: id={}, result={}", id, result);
            return result;
        }
        if (id.startsWith("playlist-")) {
            Long plId = parseLong(id.substring(9));
            log.info("[CoverArt] 前缀=playlist, plId={}", plId);
            Path result = plId != null ? resolveFromPlaylist(plId) : null;
            log.info("[CoverArt] 歌单封面结果: id={}, result={}", id, result);
            return result;
        }
        if (id.startsWith("genre-")) {
            Long numId = parseLong(id.substring(6));
            log.info("[CoverArt] 前缀=genre, numId={}", numId);
            Path result = numId != null ? resolveFromGenre(numId) : null;
            log.info("[CoverArt] 流派封面结果: id={}, result={}", id, result);
            return result;
        }

        // 兼容旧版裸数字 ID
        log.info("[CoverArt] 无前缀(旧版兼容), id={}", id);
        Long numId = parseLong(id);
        if (numId == null) {
            log.warn("[CoverArt] 无法解析为数字: id={}", id);
            return null;
        }

        Path p = resolveFromSong(numId);
        if (p != null) { log.info("[CoverArt] 旧版-歌曲命中: id={}, path={}", id, p); return p; }

        p = resolveFromAlbum(numId);
        if (p != null) { log.info("[CoverArt] 旧版-专辑命中: id={}, path={}", id, p); return p; }

        p = resolveFromArtist(numId);
        if (p != null) { log.info("[CoverArt] 旧版-艺术家命中: id={}, path={}", id, p); return p; }

        log.info("[CoverArt] 未找到封面: id={}", id);
        return null;
    }

    // ── 歌曲封面 ──

    private Path resolveFromSong(Long id) {
        Song song = songMapper.findSongById(id);
        if (song == null || song.getCoverPath() == null || song.getCoverPath().isBlank()) {
            return null;
        }
        Path path = resolveLocalPath(song.getCoverPath());
        if (path != null) {
            log.debug("歌曲封面命中: id={}, path={}", id, path);
        }
        return path;
    }

    // ── 专辑封面（含 fallback）──

    private Path resolveFromAlbum(Long id) {
        log.info("[CoverArt] resolveFromAlbum: 查询专辑 id={}", id);
        Album album = albumMapper.findAlbumById(id);
        if (album == null) {
            log.warn("[CoverArt] resolveFromAlbum: 专辑不存在 id={}", id);
            return null;
        }
        log.info("[CoverArt] resolveFromAlbum: 专辑名={}, albumCover={}, songCount={}",
                album.getAlbumName(), album.getAlbumCover(), album.getSongCount());

        // 优先：album.album_cover 直接指定
        if (album.getAlbumCover() != null && !album.getAlbumCover().isBlank()) {
            log.info("[CoverArt] resolveFromAlbum: 有直接 album_cover={}", album.getAlbumCover());
            Path path = resolveLocalPath(album.getAlbumCover());
            if (path != null) {
                log.info("[CoverArt] 专辑封面命中(直接): id={}, path={}", id, path);
                return path;
            }
            log.warn("[CoverArt] resolveFromAlbum: album_cover 路径解析失败: {}", album.getAlbumCover());
        } else {
            log.info("[CoverArt] resolveFromAlbum: 无直接 album_cover, 尝试 fallback");
        }

        // fallback: 该专辑下第一首有封面的歌曲
        String firstSongCover = songMapper.findFirstSongCoverByAlbumId(id);
        log.info("[CoverArt] resolveFromAlbum: fallback SQL 结果={}", firstSongCover);
        if (firstSongCover != null && !firstSongCover.isBlank()) {
            Path path = resolveLocalPath(firstSongCover);
            if (path != null) {
                log.info("[CoverArt] 专辑封面命中(song fallback): id={}, path={}", id, path);
                return path;
            }
            log.warn("[CoverArt] resolveFromAlbum: fallback 路径解析失败: {}", firstSongCover);
        }

        log.warn("[CoverArt] resolveFromAlbum: 未找到封面 id={}", id);
        return null;
    }

    // ── 艺术家封面（含 fallback）──

    private Path resolveFromArtist(Long id) {
        log.info("[CoverArt] resolveFromArtist: 查询艺术家 id={}", id);
        Artist artist = artistMapper.findArtistById(id);
        if (artist == null) {
            log.warn("[CoverArt] resolveFromArtist: 艺术家不存在 id={}", id);
            return null;
        }
        log.info("[CoverArt] resolveFromArtist: 艺术家名={}, artistCover={}, albumCount={}",
                artist.getArtistName(), artist.getArtistCover(), artist.getAlbumCount());

        // 优先：artist.artist_cover 直接指定
        if (artist.getArtistCover() != null && !artist.getArtistCover().isBlank()) {
            log.info("[CoverArt] resolveFromArtist: 有直接 artist_cover={}", artist.getArtistCover());
            Path path = resolveLocalPath(artist.getArtistCover());
            if (path != null) {
                log.info("[CoverArt] 艺术家封面命中(直接): id={}, path={}", id, path);
                return path;
            }
            log.warn("[CoverArt] resolveFromArtist: artist_cover 路径解析失败: {}", artist.getArtistCover());
        } else {
            log.info("[CoverArt] resolveFromArtist: 无直接 artist_cover, 尝试 fallback-1(专辑封面)");
        }

        // fallback 1: 该艺术家某张专辑的封面
        String firstAlbumCover = songMapper.findFirstAlbumCoverByArtistId(id);
        log.info("[CoverArt] resolveFromArtist: fallback-1(专辑封面) SQL 结果={}", firstAlbumCover);
        if (firstAlbumCover != null && !firstAlbumCover.isBlank()) {
            Path path = resolveLocalPath(firstAlbumCover);
            if (path != null) {
                log.info("[CoverArt] 艺术家封面命中(album fallback): id={}, path={}", id, path);
                return path;
            }
            log.warn("[CoverArt] resolveFromArtist: fallback-1 路径解析失败: {}", firstAlbumCover);
        }

        // fallback 2: 该艺术家某首歌的封面
        log.info("[CoverArt] resolveFromArtist: 尝试 fallback-2(歌曲封面)");
        String firstSongCover = songMapper.findFirstSongCoverByArtistId(id);
        log.info("[CoverArt] resolveFromArtist: fallback-2(歌曲封面) SQL 结果={}", firstSongCover);
        if (firstSongCover != null && !firstSongCover.isBlank()) {
            Path path = resolveLocalPath(firstSongCover);
            if (path != null) {
                log.info("[CoverArt] 艺术家封面命中(song fallback): id={}, path={}", id, path);
                return path;
            }
            log.warn("[CoverArt] resolveFromArtist: fallback-2 路径解析失败: {}", firstSongCover);
        }

        log.warn("[CoverArt] resolveFromArtist: 未找到封面 id={}", id);
        return null;
    }

    // ── 歌单封面（多级回退：手动封面 → 歌曲封面 → 专辑封面 → 艺术家封面）──

    private Path resolveFromPlaylist(Long playlistId) {
        log.info("[CoverArt] resolveFromPlaylist: plId={}", playlistId);
        try {
            // 0) 优先：手动设置的歌单封面
            var pl = playlistService.getById(playlistId);
            if (pl != null && pl.getCoverPath() != null && !pl.getCoverPath().isBlank()) {
                Path path = resolveLocalPath(pl.getCoverPath());
                if (path != null) {
                    log.info("[CoverArt] 歌单封面命中(手动): plId={}, path={}", playlistId, path);
                    return path;
                }
            }

            List<Song> songs = playlistService.getSongs(playlistId);
            if (songs == null || songs.isEmpty()) {
                log.info("[CoverArt] resolveFromPlaylist: 歌单无歌曲, plId={}", playlistId);
                return null;
            }
            // 遍历歌曲，多级回退：歌曲 coverPath → 专辑封面 → 艺术家封面
            for (Song song : songs) {
                // 1) 歌曲自身封面
                if (song.getCoverPath() != null && !song.getCoverPath().isBlank()) {
                    Path path = resolveLocalPath(song.getCoverPath());
                    if (path != null) {
                        log.info("[CoverArt] 歌单封面命中(song): plId={}, songId={}, path={}",
                                playlistId, song.getId(), path);
                        return path;
                    }
                }
                // 2) 歌曲所属专辑封面（内部已有 album_cover → song fallback）
                if (song.getAlbumId() != null) {
                    Path path = resolveFromAlbum(song.getAlbumId().longValue());
                    if (path != null) {
                        log.info("[CoverArt] 歌单封面命中(album): plId={}, songId={}, albumId={}, path={}",
                                playlistId, song.getId(), song.getAlbumId(), path);
                        return path;
                    }
                }
                // 3) 歌曲所属艺术家封面（内部已有 artist_cover → album → song fallback）
                if (song.getArtistId() != null) {
                    Path path = resolveFromArtist(song.getArtistId().longValue());
                    if (path != null) {
                        log.info("[CoverArt] 歌单封面命中(artist): plId={}, songId={}, artistId={}, path={}",
                                playlistId, song.getId(), song.getArtistId(), path);
                        return path;
                    }
                }
            }
            log.info("[CoverArt] resolveFromPlaylist: 歌单中无歌曲有封面, plId={}", playlistId);
        } catch (Exception e) {
            log.warn("[CoverArt] resolveFromPlaylist 异常: plId={}", playlistId, e);
        }
        return null;
    }

    // ── 流派封面（style_image → 自动生成首字图）──

    private Path resolveFromGenre(Long id) {
        log.info("[CoverArt] resolveFromGenre: 查询流派 id={}", id);
        Style style = styleMapper.findStyleById(id);
        if (style == null) {
            log.warn("[CoverArt] resolveFromGenre: 流派不存在 id={}", id);
            return null;
        }
        log.info("[CoverArt] resolveFromGenre: 流派名={}, styleImage={}",
                style.getStyleName(), style.getStyleImage());

        // 优先：style_image 直接指定且文件有效
        if (style.getStyleImage() != null && !style.getStyleImage().isBlank()) {
            Path path = resolveLocalPath(style.getStyleImage());
            if (path != null) {
                log.info("[CoverArt] 流派封面命中(style_image): id={}, path={}", id, path);
                return path;
            }
            log.warn("[CoverArt] resolveFromGenre: style_image 路径解析失败: {}", style.getStyleImage());
        }

        // fallback：生成首字图
        Path generated = generateGenreCover(id, style.getStyleName());
        if (generated != null) {
            log.info("[CoverArt] 流派封面命中(生成): id={}, name={}, path={}", id, style.getStyleName(), generated);
        }
        return generated;
    }

    private static final String[] GENRE_COLORS = {
            "#EF4444", "#F59E0B", "#10B981", "#3B82F6", "#8B5CF6",
            "#EC4899", "#06B6D4", "#F97316", "#6366F1", "#14B8A6",
    };

    private Path generateGenreCover(Long genreId, String genreName) {
        try {
            Files.createDirectories(coversDir);
            Path out = coversDir.resolve("genre-" + genreId + ".png");
            // 已生成过则直接返回
            if (Files.exists(out) && Files.isReadable(out) && Files.size(out) > 0) {
                return out;
            }

            String initial = (genreName != null && !genreName.isBlank())
                    ? genreName.trim().substring(0, 1).toUpperCase()
                    : "?";

            // 按流派名 hash 取色
            int hash = 0;
            String name = genreName != null ? genreName : "";
            for (int i = 0; i < name.length(); i++) hash = ((hash << 5) - hash) + name.charAt(i);
            Color bg = Color.decode(GENRE_COLORS[Math.abs(hash) % GENRE_COLORS.length]);

            int size = 300;
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();

            // 抗锯齿
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 背景 — 稍带透明的同色
            g.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 32));
            g.fillRoundRect(0, 0, size, size, 40, 40);

            // 文字
            g.setColor(bg);
            Font font = new Font("SansSerif", Font.BOLD, 150);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            int x = (size - fm.stringWidth(initial)) / 2;
            int y = (size - fm.getHeight()) / 2 + fm.getAscent();
            g.drawString(initial, x, y);

            g.dispose();
            ImageIO.write(img, "PNG", out.toFile());
            log.info("[CoverArt] 流派封面已生成: id={}, name={}, path={}", genreId, genreName, out);
            return out;
        } catch (Exception e) {
            log.error("[CoverArt] 流派封面生成失败: id={}, name={}", genreId, genreName, e);
            return null;
        }
    }

    // ── 路径解析 ──

    private Path resolveLocalPath(String coverPath) {
        try {
            log.info("[CoverArt] resolveLocalPath: 输入={}, coversDir={}", coverPath, coversDir);
            Path p = Path.of(coverPath);
            if (p.isAbsolute()) {
                boolean exists = Files.exists(p);
                boolean readable = exists && Files.isReadable(p);
                log.info("[CoverArt] resolveLocalPath: 绝对路径, exists={}, readable={}, path={}", exists, readable, p);
                return exists && readable ? p : null;
            }
            // 相对路径：相对于 coversDir
            Path resolved = coversDir.resolve(coverPath).normalize();
            log.info("[CoverArt] resolveLocalPath: 相对路径→绝对={}", resolved);
            // 安全检查：防止路径穿越
            if (!resolved.startsWith(coversDir)) {
                log.warn("封面路径穿越拦截: {}", coverPath);
                return null;
            }
            boolean exists = Files.exists(resolved);
            boolean readable = exists && Files.isReadable(resolved);
            log.info("[CoverArt] resolveLocalPath: exists={}, readable={}", exists, readable);
            return exists && readable ? resolved : null;
        } catch (Exception e) {
            log.warn("封面路径解析失败: {}", coverPath, e);
            return null;
        }
    }

    private static Long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
