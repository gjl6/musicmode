package com.gjl.music.playback.sync;
import com.gjl.music.playback.model.PlayerState;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


@DisplayName("PlayerState 单元测试")
class PlayerStateTest {

    @Test
    @DisplayName("Builder 构建完整状态")
    void builderAllFields() {
        PlayerState state = PlayerState.builder()
                .currentSongId(42L)
                .username("testuser")
                .position(120.5)
                .timestamp(System.currentTimeMillis())
                .event("PLAY")
                .queueIndex(3)
                .queueId("queue-123")
                .build();

        assertEquals(42L, state.getCurrentSongId());
        assertEquals("testuser", state.getUsername());
        assertEquals(120.5, state.getPosition(), 0.01);
        assertEquals("PLAY", state.getEvent());
        assertEquals(3, state.getQueueIndex());
        assertEquals("queue-123", state.getQueueId());
    }

    @Test
    @DisplayName("Builder 部分字段")
    void builderPartialFields() {
        PlayerState state = PlayerState.builder()
                .currentSongId(1L)
                .username("user")
                .event("PAUSE")
                .build();

        assertEquals(1L, state.getCurrentSongId());
        assertEquals("user", state.getUsername());
        assertEquals("PAUSE", state.getEvent());
        assertEquals(0.0, state.getPosition(), 0.01);
        assertEquals(0, state.getQueueIndex());
    }

    @Test
    @DisplayName("NoArgsConstructor + Setter")
    void noArgsConstructor() {
        PlayerState state = new PlayerState();
        state.setCurrentSongId(10L);
        state.setUsername("admin");
        state.setEvent("STOP");
        state.setPosition(0);
        state.setQueueIndex(-1);

        assertEquals(10L, state.getCurrentSongId());
        assertEquals("admin", state.getUsername());
        assertEquals("STOP", state.getEvent());
    }

    @Test
    @DisplayName("equals 和 hashCode")
    void equalsAndHashCode() {
        PlayerState s1 = PlayerState.builder()
                .currentSongId(1L).username("u").event("PLAY").build();
        PlayerState s2 = PlayerState.builder()
                .currentSongId(1L).username("u").event("PLAY").build();
        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    @DisplayName("不同状态不相等")
    void notEqual() {
        PlayerState s1 = PlayerState.builder().currentSongId(1L).username("u").build();
        PlayerState s2 = PlayerState.builder().currentSongId(2L).username("u").build();
        assertNotEquals(s1, s2);
    }

    @Test
    @DisplayName("toString 包含关键字段")
    void toStringContainsFields() {
        PlayerState state = PlayerState.builder()
                .currentSongId(42L)
                .username("test")
                .event("PLAY")
                .build();
        String s = state.toString();
        assertTrue(s.contains("42"));
        assertTrue(s.contains("test"));
        assertTrue(s.contains("PLAY"));
    }

    @Test
    @DisplayName("SEARCH_FIELDS 事件常量值")
    void eventValues() {
        PlayerState play = PlayerState.builder().currentSongId(1L).username("u")
                .event("PLAY").build();
        assertEquals("PLAY", play.getEvent());

        PlayerState pause = PlayerState.builder().currentSongId(1L).username("u")
                .event("PAUSE").build();
        assertEquals("PAUSE", pause.getEvent());

        PlayerState stop = PlayerState.builder().currentSongId(1L).username("u")
                .event("STOP").build();
        assertEquals("STOP", stop.getEvent());

        PlayerState tc = PlayerState.builder().currentSongId(1L).username("u")
                .event("TRACK_CHANGE").build();
        assertEquals("TRACK_CHANGE", tc.getEvent());

        PlayerState seek = PlayerState.builder().currentSongId(1L).username("u")
                .event("SEEK").build();
        assertEquals("SEEK", seek.getEvent());
    }

    @Test
    @DisplayName("timestamp 可读写")
    void timestamp() {
        PlayerState state = new PlayerState();
        long now = System.currentTimeMillis();
        state.setTimestamp(now);
        assertEquals(now, state.getTimestamp());
    }
}
