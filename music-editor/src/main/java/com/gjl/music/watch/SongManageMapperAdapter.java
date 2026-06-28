package com.gjl.music.watch;

import com.gjl.music.editor.mapper.SongManageMapper;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link SongDbAccessor} 的 editor 实现 — 委托 {@link SongManageMapper}。
 */
@Component
public class SongManageMapperAdapter implements SongDbAccessor {

    private final SongManageMapper mapper;

    public SongManageMapperAdapter(SongManageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Map<String, long[]> queryByPaths(List<String> paths) {
        List<Map<String, Object>> rows = mapper.selectSongPathsByList(paths);
        if (rows == null || rows.isEmpty()) return Map.of();
        Map<String, long[]> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String path = String.valueOf(row.get("FILE_PATH"));
            long id = num(row, "ID");
            long mtime = num(row, "FILE_MTIME");
            result.put(path, new long[]{id, mtime});
        }
        return result;
    }

    @Override
    public Map<String, long[]> queryUnderRoot(String rootPath) {
        List<Map<String, Object>> rows = mapper.selectSongPathsUnderRoot(rootPath);
        if (rows == null || rows.isEmpty()) return Map.of();
        Map<String, long[]> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String path = String.valueOf(row.get("FILE_PATH"));
            long id = num(row, "ID");
            long mtime = num(row, "FILE_MTIME");
            result.put(path, new long[]{id, mtime});
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> queryPaged(String rootPath, int offset, int limit) {
        return mapper.selectSongIdsUnderRootPaged(rootPath, offset, limit);
    }

    @Override
    public void cascadeDelete(List<Long> songIds) {
        mapper.deleteSongArtistsBySongIds(songIds);
        mapper.deleteSongStylesBySongIds(songIds);
        mapper.deleteLyricsBySongIds(songIds);
        mapper.deleteSongsByIds(songIds);
    }

    private static long num(Map<String, Object> row, String col) {
        Object v = row.get(col);
        return v instanceof Number n ? n.longValue() : 0L;
    }
}
