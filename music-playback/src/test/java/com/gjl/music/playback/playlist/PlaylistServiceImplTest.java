package com.gjl.music.playback.playlist;

import com.gjl.music.mapper.MusicMapper;
import com.gjl.music.model.Song;
import com.gjl.music.playback.mapper.PlaylistMapper;
import com.gjl.music.playback.mapper.PlaylistTrackMapper;
import com.gjl.music.playback.model.Playlist;
import com.gjl.music.playback.model.PlaylistTrack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("PlaylistServiceImpl 单元测试")
class PlaylistServiceImplTest {

    @Mock
    private PlaylistMapper playlistMapper;

    @Mock
    private PlaylistTrackMapper trackMapper;

    @Mock
    private MusicMapper musicMapper;

    private PlaylistServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PlaylistServiceImpl(playlistMapper, trackMapper, musicMapper, "./covers");
    }


    @Nested
    @DisplayName("查询播放列表")
    class ListPlaylists {

        @Test
        @DisplayName("listByUser 委托到 findByOwner")
        void listByUser() {
            Playlist pl = buildPlaylist(1L, "收藏", "1");
            when(playlistMapper.findByOwner("1")).thenReturn(List.of(pl));

            List<Playlist> result = service.listByUser(1L);
            assertEquals(1, result.size());
            assertEquals(1L, result.get(0).getId());
        }

        @Test
        @DisplayName("listByUsername 按用户名查询")
        void listByUsername() {
            Playlist pl = buildPlaylist(1L, "收藏", "testuser");
            when(playlistMapper.findByOwner("testuser")).thenReturn(List.of(pl));

            List<Playlist> result = service.listByUsername("testuser");
            assertEquals(1, result.size());
            assertEquals("testuser", result.get(0).getOwner());
        }

        @Test
        @DisplayName("空列表 → []")
        void emptyList() {
            when(playlistMapper.findByOwner("empty")).thenReturn(List.of());
            assertTrue(service.listByUsername("empty").isEmpty());
        }
    }


    @Nested
    @DisplayName("查询播放列表详情")
    class GetById {

        @Test
        @DisplayName("getById → Playlist")
        void getById() {
            Playlist pl = buildPlaylist(1L, "收藏", "testuser");
            when(playlistMapper.findById(1L)).thenReturn(pl);

            Playlist result = service.getById(1L);
            assertNotNull(result);
            assertEquals("收藏", result.getName());
        }

        @Test
        @DisplayName("getById 不存在 → null")
        void getByIdNotFound() {
            when(playlistMapper.findById(999L)).thenReturn(null);
            assertNull(service.getById(999L));
        }

        @Test
        @DisplayName("getSongs 获取歌曲列表")
        void getSongs() {
            when(trackMapper.findByPlaylistId(1L)).thenReturn(List.of(
                    PlaylistTrack.builder().playlistId(1L).songId(1L).position(0).build(),
                    PlaylistTrack.builder().playlistId(1L).songId(2L).position(1).build()
            ));
            when(musicMapper.findSongsByIds(List.of(1L, 2L))).thenReturn(List.of(
                    Song.builder().id("1").title("Song 1").build(),
                    Song.builder().id("2").title("Song 2").build()
            ));

            List<Song> songs = service.getSongs(1L);
            assertEquals(2, songs.size());
            assertEquals("Song 1", songs.get(0).getTitle());
        }

        @Test
        @DisplayName("getSongs 空 → []")
        void getSongsEmpty() {
            when(trackMapper.findByPlaylistId(-1L)).thenReturn(List.of());
            assertTrue(service.getSongs(-1L).isEmpty());
        }
    }


    @Nested
    @DisplayName("播放列表 CRUD")
    class Crud {

        @Test
        @DisplayName("createByUsername 插入并返回（ID 由 DB 自增）")
        void createByUsername() {
            Playlist result = service.createByUsername("新列表", "备注", "testuser", null, true);

                        assertEquals("新列表", result.getName());
            assertEquals("备注", result.getComment());
            assertEquals("testuser", result.getOwner());
            assertTrue(result.isPublic());

            verify(playlistMapper).insert(argThat(p ->
                    p.getName().equals("新列表") &&
                            p.getOwner().equals("testuser") &&
                            p.isPublic()
            ));
        }

        @Test
        @DisplayName("create 委托到 createByUsername（含 coverPath）")
        void create() {
            service.create("列表", "", 1L, false, null);
            verify(playlistMapper).insert(argThat(p ->
                    p.getOwner().equals("1")
            ));
        }

        @Test
        @DisplayName("update 成功")
        void update() {
            Playlist existing = buildPlaylist(1L, "旧名", "testuser");
            when(playlistMapper.findById(1L)).thenReturn(existing);

            Playlist result = service.update(1L, "新名", "新备注", true, null);

            assertEquals("新名", result.getName());
            assertEquals("新备注", result.getComment());
            assertTrue(result.isPublic());
            verify(playlistMapper).update(existing);
        }

        @Test
        @DisplayName("update 不存在 → 抛出异常")
        void updateNotFound() {
            when(playlistMapper.findById(404L)).thenReturn(null);

            assertThrows(IllegalArgumentException.class,
                    () -> service.update(404L, "name", "", false, null));
            verify(playlistMapper, never()).update(any());
        }

        @Test
        @DisplayName("delete 先删曲目再删播放列表")
        void delete() {
            Playlist existing = buildPlaylist(1L, "测试", "testuser");
            when(playlistMapper.findById(1L)).thenReturn(existing);

            service.delete(1L);

            InOrder order = inOrder(trackMapper, playlistMapper);
            order.verify(trackMapper).deleteByPlaylistId(1L);
            order.verify(playlistMapper).deleteById(1L);
        }
    }


    @Nested
    @DisplayName("播放列表歌曲管理")
    class SongManagement {

        @Test
        @DisplayName("addSongs 追加到末尾")
        void addSongs() {
            when(playlistMapper.findById(1L)).thenReturn(buildPlaylist(1L, "测试", "testuser"));
            when(trackMapper.maxPosition(1L)).thenReturn(5);

            service.addSongs(1L, List.of(10L, 20L, 30L));

                        verify(trackMapper).batchInsert(argThat(list -> {
                return list.size() == 3 &&
                        list.get(0).getPosition() == 1000 &&
                        list.get(1).getPosition() == 2000 &&
                        list.get(2).getPosition() == 3000;
            }));
            verify(playlistMapper).updateSongCount(1L);
        }

        @Test
        @DisplayName("addSongs 空列表（maxPosition=-1 → 大间隔从1000开始）")
        void addSongsToEmpty() {
            when(playlistMapper.findById(1L)).thenReturn(buildPlaylist(1L, "测试", "testuser"));
            when(trackMapper.maxPosition(1L)).thenReturn(-1);

            service.addSongs(1L, List.of(1L));

            verify(trackMapper).batchInsert(argThat(list ->
                    list.get(0).getPosition() == 1000));
        }

        @Test
        @DisplayName("removeSong 按位置删除后自动补位")
        void removeSong() {
            Playlist existing = buildPlaylist(1L, "测试", "testuser");
            when(playlistMapper.findById(1L)).thenReturn(existing);
            when(trackMapper.findByPlaylistId(1L)).thenReturn(List.of(
                    PlaylistTrack.builder().id(1L).playlistId(1L).songId(1L).position(1000).build(),
                    PlaylistTrack.builder().id(2L).playlistId(1L).songId(2L).position(2000).build()
            ));

            service.removeSong(1L, 3);

            verify(trackMapper).deleteByPlaylistAndPosition(1L, 3);
                        verify(trackMapper).setPosition(1L, 1L, 1000);
            verify(trackMapper).setPosition(1L, 2L, 2000);
            verify(playlistMapper).updateSongCount(1L);
        }
    }


    @Nested
    @DisplayName("M3U 导出/导入")
    class M3u {

        @Test
        @DisplayName("exportM3u 生成正确 M3U8 内容")
        void exportM3u() {
            when(trackMapper.findByPlaylistId(1L)).thenReturn(List.of(
                    PlaylistTrack.builder().playlistId(1L).songId(1L).position(0).build()
            ));
            when(musicMapper.findSongsByIds(List.of(1L))).thenReturn(List.of(
                    Song.builder().id("1").title("夜曲").filePath("/music/夜曲.flac")
                            .duration(286).build()
            ));

            String m3u = service.exportM3u(1L);
            assertTrue(m3u.startsWith("#EXTM3U"));
            assertTrue(m3u.contains("#EXTINF:286,夜曲"));
            assertTrue(m3u.contains("/music/夜曲.flac"));
        }

        @Test
        @DisplayName("importM3u 匹配歌曲路径")
        void importM3u() {
            Playlist existing = buildPlaylist(1L, "测试", "testuser");
            when(playlistMapper.findById(1L)).thenReturn(existing);
            when(trackMapper.maxPosition(1L)).thenReturn(0);
            when(musicMapper.findSongByFilePath("/music/01.flac")).thenReturn(
                    Song.builder().id("1").filePath("/music/01.flac").build());
            when(musicMapper.findSongByFilePath("/music/02.flac")).thenReturn(
                    Song.builder().id("2").filePath("/music/02.flac").build());

            String m3uContent = """
                #EXTM3U
                #EXTINF:286,夜曲
                /music/01.flac
                #EXTINF:259,蓝色风暴
                /music/02.flac
                """;

            List<Long> ids = service.importM3u(1L, m3uContent, null);
            assertEquals(2, ids.size());
            assertEquals(1L, ids.get(0));
            assertEquals(2L, ids.get(1));
            verify(trackMapper).batchInsert(anyList());
            verify(playlistMapper).updateSongCount(1L);
        }

        @Test
        @DisplayName("importM3u 未匹配到歌曲时不插入")
        void importM3uNoMatch() {
            Playlist existing = buildPlaylist(1L, "测试", "testuser");
            when(playlistMapper.findById(1L)).thenReturn(existing);
            when(musicMapper.findSongByFilePath(anyString())).thenReturn(null);

            String m3uContent = "/music/nonexistent.flac";
            List<Long> ids = service.importM3u(1L, m3uContent, null);

            assertTrue(ids.isEmpty());
            verify(trackMapper, never()).batchInsert(anyList());
        }

        @Test
        @DisplayName("importM3u 相对路径拼接 rootDir")
        void importM3uRelativePath() {
            Playlist existing = buildPlaylist(1L, "测试", "testuser");
            when(playlistMapper.findById(1L)).thenReturn(existing);
            when(trackMapper.maxPosition(1L)).thenReturn(0);
            when(musicMapper.findSongByFilePath("/music/albums/01.flac")).thenReturn(
                    Song.builder().id("1").build());

            String m3uContent = "albums/01.flac";
            List<Long> ids = service.importM3u(1L, m3uContent, "/music");

            assertEquals(1, ids.size());
        }
    }


    private static Playlist buildPlaylist(Long id, String name, String owner) {
        return Playlist.builder()
                .id(id).name(name).owner(owner)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
