package com.gjl.music.playback.sync;
import com.gjl.music.playback.model.PlayerState;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("PlayerStateController 单元测试")
class PlayerStateControllerTest {

    @Mock
    private PlayerStateSyncService syncService;

    private MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        PlayerStateController controller = new PlayerStateController(syncService);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Nested
    @DisplayName("POST /api/player/state — 上报状态")
    class ReportState {

        @Test
        @DisplayName("上报播放状态 → 200 OK")
        void reportState() throws Exception {
            doNothing().when(syncService).broadcast(any());

            PlayerState state = PlayerState.builder()
                    .currentSongId(42L).position(120.5)
                    .event("PLAY").queueIndex(3).build();
            String body = objectMapper.writeValueAsString(state);

            mvc.perform(post("/api/player/state")
                            .principal(() -> "testuser")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("设置 username（通过 Principal）")
        void setsPrincipalUsername() throws Exception {
            doNothing().when(syncService).broadcast(any());

            PlayerState state = PlayerState.builder().event("PLAY").build();
            String body = objectMapper.writeValueAsString(state);

            mvc.perform(post("/api/player/state")
                            .principal(() -> "anon")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());

            verify(syncService).broadcast(argThat(s ->
                    s.getUsername() != null));
        }
    }

    @Nested
    @DisplayName("GET /api/player/state — 获取当前用户状态")
    class GetLastState {

        @Test
        @DisplayName("存在状态 → 返回状态")
        void hasState() throws Exception {
            PlayerState state = PlayerState.builder()
                    .currentSongId(42L).username("testuser")
                    .position(60.0).event("PLAY").build();
            when(syncService.getLastState(anyString())).thenReturn(state);

            mvc.perform(get("/api/player/state").principal(() -> "testuser"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentSongId").value(42))
                    .andExpect(jsonPath("$.position").value(60.0))
                    .andExpect(jsonPath("$.event").value("PLAY"));
        }

        @Test
        @DisplayName("无状态时返回默认 STOP 状态")
        void noStateReturnsDefault() throws Exception {
            when(syncService.getLastState(anyString())).thenReturn(null);

            mvc.perform(get("/api/player/state").principal(() -> "newuser"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.event").value("STOP"))
                    .andExpect(jsonPath("$.queueIndex").value(-1));
        }
    }

    @Nested
    @DisplayName("GET /api/player/state/{username} — 跨用户查询")
    class GetOtherUserState {

        @Test
        @DisplayName("存在状态 → 200")
        void otherUserHasState() throws Exception {
            PlayerState state = PlayerState.builder()
                    .currentSongId(99L).username("otheruser").event("PLAY").build();
            when(syncService.getLastState("otheruser")).thenReturn(state);

            mvc.perform(get("/api/player/state/otheruser"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("otheruser"))
                    .andExpect(jsonPath("$.currentSongId").value(99));
        }

        @Test
        @DisplayName("不存在状态 → 404")
        void otherUserNoState() throws Exception {
            when(syncService.getLastState("unknown")).thenReturn(null);

            mvc.perform(get("/api/player/state/unknown"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/player/state/control — 控制命令")
    class Control {

        @Test
        @DisplayName("PLAY 命令 → 200 OK")
        void playCommand() throws Exception {
            doNothing().when(syncService).broadcast(any());

            String body = objectMapper.writeValueAsString(Map.of(
                    "event", "play", "songId", 42, "position", 0));

            mvc.perform(post("/api/player/state/control")
                            .principal(() -> "testuser")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("缺少 event → 400 Bad Request")
        void missingEvent() throws Exception {
            String body = objectMapper.writeValueAsString(Map.of("songId", 42));

            mvc.perform(post("/api/player/state/control")
                            .principal(() -> "testuser")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("event 自动转大写")
        void eventUpperCase() throws Exception {
            doNothing().when(syncService).broadcast(any());

            String body = objectMapper.writeValueAsString(Map.of("event", "pause"));

            mvc.perform(post("/api/player/state/control")
                            .principal(() -> "testuser")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());

            verify(syncService).broadcast(argThat(s ->
                    "PAUSE".equals(s.getEvent())));
        }
    }
}
