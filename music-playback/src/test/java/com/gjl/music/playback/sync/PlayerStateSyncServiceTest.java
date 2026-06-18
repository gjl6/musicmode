package com.gjl.music.playback.sync;
import com.gjl.music.playback.model.PlayerState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("PlayerStateSyncService 单元测试")
class PlayerStateSyncServiceTest {

    @Mock
    private SimpMessagingTemplate messaging;

    private PlayerStateSyncService service;

    @BeforeEach
    void setUp() {
        service = new PlayerStateSyncService(messaging);
    }

    @Nested
    @DisplayName("broadcast 推送")
    class Broadcast {

        @Test
        @DisplayName("broadcast 推送状态到 /topic/player/{user}/state")
        void broadcastToState() {
            PlayerState state = PlayerState.builder()
                    .currentSongId(42L).username("testuser")
                    .position(60.0).event("PLAY").queueIndex(0).build();

            service.broadcast(state);

                        verify(messaging).convertAndSend(
                    "/topic/player/testuser/state", (Object) state);
        }

        @Test
        @DisplayName("broadcast 同时推送事件到 /topic/player/{user}/event")
        void broadcastToEvent() {
            PlayerState state = PlayerState.builder()
                    .currentSongId(42L).username("testuser")
                    .position(60.0).event("PLAY").queueIndex(0).build();

            service.broadcast(state);

            verify(messaging).send(
                    eq("/topic/player/testuser/event"),
                    any(Message.class));
        }

        @Test
        @DisplayName("event 为 null 时不推送事件通道")
        void nullEventSkipsEventChannel() {
            PlayerState state = PlayerState.builder()
                    .currentSongId(42L).username("testuser")
                    .position(0).event(null).build();

            service.broadcast(state);

            verify(messaging).convertAndSend(
                    "/topic/player/testuser/state", (Object) state);
            verify(messaging, never()).send(anyString(), any(Message.class));
        }

        @Test
        @DisplayName("username 为 null 时不推送")
        void nullUsername() {
            PlayerState state = PlayerState.builder()
                    .currentSongId(42L).username(null)
                    .position(0).event("PLAY").build();

            service.broadcast(state);

            verify(messaging, never()).convertAndSend(anyString(), any(Object.class));
            verify(messaging, never()).send(anyString(), any(Message.class));
        }

        @Test
        @DisplayName("broadcast 更新 lastStates 并设置 timestamp")
        void broadcastUpdatesLastState() {
            PlayerState state = PlayerState.builder()
                    .currentSongId(42L).username("testuser")
                    .position(120.0).event("PLAY").build();

            service.broadcast(state);

            assertTrue(state.getTimestamp() > 0);
            PlayerState last = service.getLastState("testuser");
            assertNotNull(last);
            assertEquals(42L, last.getCurrentSongId());
            assertEquals("PLAY", last.getEvent());
        }
    }

    @Nested
    @DisplayName("getLastState")
    class GetLastState {

        @Test
        @DisplayName("返回最后广播状态")
        void returnsLastBroadcastState() {
            PlayerState state = PlayerState.builder()
                    .currentSongId(1L).username("user1").event("PLAY").build();
            service.broadcast(state);

            PlayerState last = service.getLastState("user1");
            assertEquals(1L, last.getCurrentSongId());
            assertEquals("PLAY", last.getEvent());
        }

        @Test
        @DisplayName("未广播过的用户返回 null")
        void unknownUserReturnsNull() {
            assertNull(service.getLastState("unknown"));
        }

        @Test
        @DisplayName("多次广播覆盖上一次状态")
        void multipleBroadcasts() {
            service.broadcast(PlayerState.builder()
                    .currentSongId(1L).username("u").event("PLAY").build());
            service.broadcast(PlayerState.builder()
                    .currentSongId(2L).username("u").event("PAUSE").build());

            PlayerState last = service.getLastState("u");
            assertEquals(2L, last.getCurrentSongId());
            assertEquals("PAUSE", last.getEvent());
        }
    }

    @Nested
    @DisplayName("事件消息内容")
    class EventMessage {

        @Test
        @DisplayName("事件消息包含正确字段")
        void eventMessageFields() {
            PlayerState state = PlayerState.builder()
                    .currentSongId(42L).username("testuser")
                    .position(120.5).event("PLAY").build();

            service.broadcast(state);

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(messaging).send(eq("/topic/player/testuser/event"), captor.capture());

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> payload =
                    (java.util.Map<String, Object>) captor.getValue().getPayload();
            assertEquals("PLAY", payload.get("event"));
            assertEquals(42L, payload.get("songId"));
            assertEquals(120.5, payload.get("position"));
        }

        @Test
        @DisplayName("songId 为 null 时事件消息返回 -1")
        void nullSongIdEvent() {
            PlayerState state = PlayerState.builder()
                    .username("testuser").event("STOP")
                    .currentSongId(null).position(0).build();

            service.broadcast(state);

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(messaging).send(eq("/topic/player/testuser/event"), captor.capture());

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> payload =
                    (java.util.Map<String, Object>) captor.getValue().getPayload();
            assertEquals(-1L, payload.get("songId"));
        }
    }
}
