package com.gjl.music.playback.streaming;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@ExtendWith(MockitoExtension.class)
@DisplayName("StreamController 单元测试")
class StreamControllerTest {

    @Mock
    private StreamingService streamingService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        StreamController controller = new StreamController(streamingService);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }


    @Nested
    @DisplayName("参数传递")
    class ParameterPassing {

        @Test
        @DisplayName("rawPath 参数被传递到 service")
        void rawPathPassedToService() throws Exception {
            doNothing().when(streamingService)
                    .streamFile(anyString(), any(), any());

            mvc.perform(get("/api/player/stream")
                            .param("rawPath", "/music/artist/album/track.flac"))
                    .andExpect(status().isOk());

            verify(streamingService).streamFile(
                    eq("/music/artist/album/track.flac"), any(), any());
        }

        @Test
        @DisplayName("songId 路径变量被传递到 service")
        void songIdPassedToService() throws Exception {
            doNothing().when(streamingService)
                    .streamSong(anyLong(), any(), any());

            mvc.perform(get("/api/player/stream/12345"))
                    .andExpect(status().isOk());

            verify(streamingService).streamSong(eq(12345L), any(), any());
        }

        @Test
        @DisplayName("URL 编码的 rawPath 正确解码")
        void urlEncodedPath() throws Exception {
            doNothing().when(streamingService)
                    .streamFile(anyString(), any(), any());

            mvc.perform(get("/api/player/stream")
                            .param("rawPath", "/music/Jay%20Chou/%E5%A4%9C%E6%9B%B2.flac"))
                    .andExpect(status().isOk());

            verify(streamingService).streamFile(
                    eq("/music/Jay Chou/夜曲.flac"), any(), any());
        }
    }

    @Nested
    @DisplayName("端点结构")
    class EndpointStructure {

        @Test
        @DisplayName("GET /api/player/stream 无 rawPath 参数 → 400")
        void missingRawPath() throws Exception {
            mvc.perform(get("/api/player/stream"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("GET /api/player/stream/{songId} 正常调用")
        void streamById() throws Exception {
            doNothing().when(streamingService)
                    .streamSong(anyLong(), any(), any());

            mvc.perform(get("/api/player/stream/1"))
                    .andExpect(status().isOk());

            verify(streamingService).streamSong(eq(1L), any(), any());
        }
    }
}
