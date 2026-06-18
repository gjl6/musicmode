package com.gjl.music.playback.playlist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjl.music.model.Song;
import com.gjl.music.playback.model.PlayQueueEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
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
@DisplayName("PlayQueueController 单元测试")
class PlayQueueControllerTest {

    @Mock
    private PlayQueueService playQueueService;

    private MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        PlayQueueController controller = new PlayQueueController(playQueueService);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Nested
    @DisplayName("GET /api/play-queue — 获取队列")
    class GetQueue {

        @Test
        @DisplayName("有歌曲 → 200 + 歌曲列表")
        void withSongs() throws Exception {
            when(playQueueService.getQueue(anyString())).thenReturn(List.of(
                    Song.builder().id("1").title("First").build(),
                    Song.builder().id("2").title("Second").build()
            ));

            mvc.perform(get("/api/play-queue").principal(() -> "testuser"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].title").value("First"));
        }

        @Test
        @DisplayName("空队列 → 200 + []")
        void emptyQueue() throws Exception {
            when(playQueueService.getQueue(anyString())).thenReturn(List.of());

            mvc.perform(get("/api/play-queue").principal(() -> "testuser"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/play-queue/entries — 原始条目")
    class GetEntries {

        @Test
        @DisplayName("返回含 position 的条目")
        void withPosition() throws Exception {
            when(playQueueService.getEntries(anyString())).thenReturn(List.of(
                    PlayQueueEntry.builder().id(1L).username("testuser")
                            .songId(10L).position(0)
                            .addedAt(LocalDateTime.now()).build(),
                    PlayQueueEntry.builder().id(2L).username("testuser")
                            .songId(20L).position(1)
                            .addedAt(LocalDateTime.now()).build()
            ));

            mvc.perform(get("/api/play-queue/entries").principal(() -> "testuser"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].position").value(0))
                    .andExpect(jsonPath("$[1].position").value(1));
        }
    }

    @Nested
    @DisplayName("POST /api/play-queue — 保存队列")
    class SaveQueue {

        @Test
        @DisplayName("保存 → 200 OK")
        void saveQueue() throws Exception {
            doNothing().when(playQueueService)
                    .saveQueue(anyString(), anyList());

            String body = objectMapper.writeValueAsString(Map.of(
                    "songIds", List.of(5, 10, 15)));

            mvc.perform(post("/api/play-queue")
                            .principal(() -> "testuser")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());

            verify(playQueueService).saveQueue(anyString(),
                    eq(List.of(5L, 10L, 15L)));
        }
    }

    @Nested
    @DisplayName("DELETE /api/play-queue/{position} — 删除条目")
    class RemoveEntry {

        @Test
        @DisplayName("删除指定位置 → 204")
        void removeEntry() throws Exception {
            doNothing().when(playQueueService).remove(anyString(), eq(3));

            mvc.perform(delete("/api/play-queue/3").principal(() -> "testuser"))
                    .andExpect(status().isNoContent());

            verify(playQueueService).remove(anyString(), eq(3));
        }
    }

    @Nested
    @DisplayName("DELETE /api/play-queue — 清空")
    class ClearQueue {

        @Test
        @DisplayName("清空 → 204")
        void clearQueue() throws Exception {
            doNothing().when(playQueueService).clear(anyString());

            mvc.perform(delete("/api/play-queue").principal(() -> "testuser"))
                    .andExpect(status().isNoContent());

            verify(playQueueService).clear(anyString());
        }
    }
}
