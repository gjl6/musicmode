package com.gjl.music.playback.playlist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjl.music.model.Song;
import com.gjl.music.playback.model.Playlist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("PlaylistController 单元测试")
class PlaylistControllerTest {

    @Mock
    private PlaylistServiceImpl playlistService;

    @Mock
    private PlayQueueService playQueueService;

    private MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        PlaylistController controller = new PlaylistController(playlistService, playQueueService);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Nested
    @DisplayName("GET /api/playlists")
    class ListPlaylists {

        @Test
        @DisplayName("列出用户播放列表 → 200")
        void listPlaylists() throws Exception {
            Playlist pl1 = Playlist.builder()
                    .id(1L).name("收藏").owner("anonymous")
                    .createdAt(LocalDateTime.now()).build();
            Playlist pl2 = Playlist.builder()
                    .id(2L).name("经典").owner("anonymous")
                    .createdAt(LocalDateTime.now()).build();
            when(playlistService.listByUsername(anyString())).thenReturn(List.of(pl1, pl2));

            mvc.perform(get("/api/playlists").principal(() -> "testuser"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].name").value("收藏"))
                    .andExpect(jsonPath("$[1].name").value("经典"));
        }

        @Test
        @DisplayName("空列表 → 200 + []")
        void emptyList() throws Exception {
            when(playlistService.listByUsername(anyString())).thenReturn(List.of());

            mvc.perform(get("/api/playlists").principal(() -> "testuser"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/playlists/{id}")
    class GetPlaylist {

        @Test
        @DisplayName("存在 → 200 + playlist + songs")
        void getById() throws Exception {
            Playlist pl = Playlist.builder()
                    .id(1L).name("收藏").owner("testuser").songCount(2).build();
            when(playlistService.getById(1L)).thenReturn(pl);
            when(playlistService.getSongs(1L)).thenReturn(List.of());

            mvc.perform(get("/api/playlists/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.playlist.name").value("收藏"))
                    .andExpect(jsonPath("$.songs").isArray());
        }

        @Test
        @DisplayName("不存在 → 404")
        void notFound() throws Exception {
            when(playlistService.getById(999L)).thenReturn(null);

            mvc.perform(get("/api/playlists/999"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/playlists")
    class CreatePlaylist {

        @Test
        @DisplayName("创建播放列表 → 200")
        void createPlaylist() throws Exception {
            Playlist created = Playlist.builder()
                    .id(1L).name("新播放列表").owner("anonymous").build();
            when(playlistService.createByUsername(
                    eq("新播放列表"), eq(""), anyString(), isNull(), eq(false), isNull()))
                    .thenReturn(created);

            String body = objectMapper.writeValueAsString(Map.of("name", "新播放列表"));

            mvc.perform(post("/api/playlists")
                            .principal(() -> "testuser")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("新播放列表"));
        }
    }

    @Nested
    @DisplayName("PUT /api/playlists/{id}")
    class UpdatePlaylist {

        @Test
        @DisplayName("更新成功 → 200")
        void updatePlaylist() throws Exception {
            Playlist updated = Playlist.builder()
                    .id(1L).name("重命名").owner("testuser").build();
            when(playlistService.update(
                    eq(1L), eq("重命名"), eq(""), eq(false), isNull()))
                    .thenReturn(updated);

            String body = objectMapper.writeValueAsString(Map.of("name", "重命名"));

            mvc.perform(put("/api/playlists/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("重命名"));
        }

        @Test
        @DisplayName("播放列表不存在 → 404")
        void updateNotFound() throws Exception {
            when(playlistService.update(anyLong(), anyString(), anyString(), anyBoolean(), isNull()))
                    .thenThrow(new IllegalArgumentException("播放列表不存在: 404"));

            String body = objectMapper.writeValueAsString(Map.of("name", "test"));

            mvc.perform(put("/api/playlists/404")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/playlists/{id}")
    class DeletePlaylist {

        @Test
        @DisplayName("删除成功 → 204")
        void deletePlaylist() throws Exception {
            doNothing().when(playlistService).delete(1L);

            mvc.perform(delete("/api/playlists/1"))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("添加/删除歌曲")
    class SongManagement {

        @Test
        @DisplayName("POST /{id}/songs 添加歌曲")
        void addSongs() throws Exception {
            doNothing().when(playlistService).addSongs(eq(1L), anyList());

            String body = objectMapper.writeValueAsString(Map.of(
                    "songIds", List.of(1, 2, 3)));

            mvc.perform(post("/api/playlists/1/songs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("DELETE /{id}/songs/{position} 删除歌曲")
        void removeSong() throws Exception {
            doNothing().when(playlistService).removeSong(1L, 2);

            mvc.perform(delete("/api/playlists/1/songs/2"))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("M3U 导入导出")
    class M3u {

        @Test
        @DisplayName("GET /{id}/export.m3u → 200 + M3U 内容")
        void exportM3u() throws Exception {
            Playlist pl = Playlist.builder().id(1L).name("收藏").build();
            when(playlistService.getById(1L)).thenReturn(pl);
            when(playlistService.exportM3u(1L))
                    .thenReturn("#EXTM3U\n/music/song.mp3\n");

            mvc.perform(get("/api/playlists/1/export.m3u"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition",
                            "attachment; filename=\"收藏.m3u8\""))
                    .andExpect(content().string("#EXTM3U\n/music/song.mp3\n"));
        }

        @Test
        @DisplayName("POST /{id}/import 导入 M3U")
        void importM3u() throws Exception {
            when(playlistService.importM3u(eq(1L), anyString(), eq("/music")))
                    .thenReturn(List.of(1L, 2L, 3L));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "playlist.m3u8", "audio/x-mpegurl",
                    "#EXTM3U\nsong1.mp3\nsong2.mp3\nsong3.mp3\n".getBytes());

            mvc.perform(multipart("/api/playlists/1/import")
                            .file(file)
                            .param("rootDir", "/music"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.imported").value(3))
                    .andExpect(jsonPath("$.songIds.length()").value(3));
        }
    }
}
