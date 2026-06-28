package com.gjl.music.search;

import java.util.List;

/**
 * 全文搜索服务接口。
 *
 * <p>支持歌曲、专辑、艺术家三路并行搜索，BM25 相关性排序。
 */
public interface SearchService {

    /** 搜索歌曲，返回按相关性排序的歌曲 ID 列表 */
    List<Long> searchSongs(String query, int offset, int limit);

    /** 搜索专辑，返回按相关性排序的专辑 ID 列表 */
    List<Long> searchAlbums(String query, int offset, int limit);

    /** 搜索艺术家，返回按相关性排序的艺术家 ID 列表 */
    List<Long> searchArtists(String query, int offset, int limit);
}
