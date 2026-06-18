package com.gjl.music.playback.playlist;

import com.gjl.music.mapper.MusicMapper;
import com.gjl.music.model.Song;
import com.gjl.music.playback.mapper.PlayQueueMapper;
import com.gjl.music.playback.model.PlayQueueEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("PlayQueueService 单元测试")
class PlayQueueServiceTest {

    @Mock
    private PlayQueueMapper queueMapper;

    @Mock
    private MusicMapper musicMapper;

    private PlayQueueService service;

    @BeforeEach
    void setUp() {
        service = new PlayQueueService(queueMapper, musicMapper);
    }

    @Nested
    @DisplayName("获取队列")
    class GetQueue {

        @Test
        @DisplayName("有 entries → 返回歌曲列表")
        void withEntries() {
            when(queueMapper.findByUsername("testuser")).thenReturn(List.of(
                    PlayQueueEntry.builder().username("testuser")
                            .songId(1L).position(0).build(),
                    PlayQueueEntry.builder().username("testuser")
                            .songId(2L).position(1).build()
            ));
            when(musicMapper.findSongsByIds(List.of(1L, 2L))).thenReturn(List.of(
                    Song.builder().id("1").title("First").build(),
                    Song.builder().id("2").title("Second").build()
            ));

            List<Song> songs = service.getQueue("testuser");
            assertEquals(2, songs.size());
            assertEquals("First", songs.get(0).getTitle());
            assertEquals("Second", songs.get(1).getTitle());
        }

        @Test
        @DisplayName("空队列 → []")
        void emptyQueue() {
            when(queueMapper.findByUsername("empty")).thenReturn(List.of());
            assertTrue(service.getQueue("empty").isEmpty());
            verify(musicMapper, never()).findSongsByIds(anyList());
        }
    }

    @Test
    @DisplayName("getEntries 返回原始条目含 position")
    void getEntries() {
        when(queueMapper.findByUsername("testuser")).thenReturn(List.of(
                PlayQueueEntry.builder().username("testuser")
                        .songId(10L).position(0).build(),
                PlayQueueEntry.builder().username("testuser")
                        .songId(20L).position(1).build()
        ));

        List<PlayQueueEntry> entries = service.getEntries("testuser");
        assertEquals(2, entries.size());
        assertEquals(0, entries.get(0).getPosition());
        assertEquals(1, entries.get(1).getPosition());
    }

    @Nested
    @DisplayName("保存队列")
    class SaveQueue {

        @Test
        @DisplayName("保存替换整个队列")
        void saveReplacesQueue() {
            service.saveQueue("testuser", List.of(5L, 10L, 15L));

            verify(queueMapper).clearByUsername("testuser");
            verify(queueMapper).batchInsert(argThat(list ->
                    list.size() == 3 &&
                            ((PlayQueueEntry) list.get(0)).getPosition() == 0 &&
                            ((PlayQueueEntry) list.get(1)).getPosition() == 1 &&
                            ((PlayQueueEntry) list.get(2)).getPosition() == 2 &&
                            ((PlayQueueEntry) list.get(0)).getUsername().equals("testuser")
            ));
        }

        @Test
        @DisplayName("保存空列表 → 仅清空不插入")
        void saveEmptyList() {
            service.saveQueue("testuser", List.of());

            verify(queueMapper).clearByUsername("testuser");
            verify(queueMapper, never()).batchInsert(anyList());
        }

        @Test
        @DisplayName("多次保存覆盖")
        void multipleSaves() {
            service.saveQueue("user", List.of(1L));
            service.saveQueue("user", List.of(2L, 3L));

            verify(queueMapper, times(2)).clearByUsername("user");
            verify(queueMapper, times(2)).batchInsert(anyList());
        }
    }

    @Test
    @DisplayName("remove 按位置删除")
    void remove() {
        service.remove("testuser", 5);
        verify(queueMapper).deleteByUsernameAndPosition("testuser", 5);
    }

    @Test
    @DisplayName("clear 清空队列")
    void clear() {
        service.clear("testuser");
        verify(queueMapper).clearByUsername("testuser");
    }
}
