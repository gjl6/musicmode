package com.gjl.music.playback.subsonic;
import com.gjl.music.playback.infra.subsonic.SubsonicResponseBuilder;

import com.gjl.music.mapper.MusicMapper;
import com.gjl.music.playback.service.PlayCountService;
import com.gjl.music.playback.service.impl.PlaylistServiceImpl;
import com.gjl.music.playback.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("SubsonicController 单元测试")
class SubsonicControllerTest {

    @Mock
    private MusicMapper musicMapper;

    @Mock
    private SearchService searchService;

    @Mock
    private PlaylistServiceImpl playlistService;

    @Mock
    private SubsonicResponseBuilder responseBuilder;

    @Mock
    private CoverArtService coverArtService;

    @Mock
    private PlayCountService playCountService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        SubsonicController controller = new SubsonicController(
                musicMapper, searchService, playlistService, responseBuilder,
                coverArtService, playCountService);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }


    @Nested
    @DisplayName("System 端点")
    class System {

        @Test
        @DisplayName("ping → ok")
        void ping() throws Exception {
            when(responseBuilder.buildOk(anyMap()))
                    .thenReturn(Map.of("status", "ok"));
            when(responseBuilder.serialize(anyMap(), eq("xml"), eq(null)))
                    .thenReturn("<subsonic-response status=\"ok\"/>");

            mvc.perform(get("/rest/ping"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("getLicense → 返回 license")
        void getLicense() throws Exception {
            when(responseBuilder.buildOk(anyMap()))
                    .thenReturn(Map.of("status", "ok"));
            when(responseBuilder.serialize(anyMap(), eq("xml"), eq(null)))
                    .thenReturn("<subsonic-response status=\"ok\"/>");

            mvc.perform(get("/rest/getLicense"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("不支持的动作 → error")
        void unsupportedAction() throws Exception {
            when(responseBuilder.buildError(eq(10), anyString()))
                    .thenReturn(Map.of("status", "failed"));
            when(responseBuilder.serialize(anyMap(), eq("xml"), eq(null)))
                    .thenReturn("<subsonic-response status=\"failed\"/>");

            mvc.perform(get("/rest/invalidAction"))
                    .andExpect(status().isOk());
        }
    }


    @Nested
    @DisplayName("Browsing 端点")
    class Browsing {

        @Test
        @DisplayName("getMusicFolders → 返回 musicFolders")
        void getMusicFolders() throws Exception {
            when(responseBuilder.buildOk(anyMap()))
                    .thenReturn(Map.of("status", "ok"));
            when(responseBuilder.serialize(anyMap(), eq("xml"), eq(null)))
                    .thenReturn("<subsonic-response status=\"ok\"/>");

            mvc.perform(get("/rest/getMusicFolders"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("getIndexes → 返回空 indexes")
        void getIndexes() throws Exception {
            when(responseBuilder.buildOk(anyMap()))
                    .thenReturn(Map.of("status", "ok"));
            when(responseBuilder.serialize(anyMap(), eq("xml"), eq(null)))
                    .thenReturn("<subsonic-response status=\"ok\"/>");

            mvc.perform(get("/rest/getIndexes"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("getMusicDirectory 缺少 id → error")
        void getMusicDirectoryNoId() throws Exception {
            when(responseBuilder.buildError(eq(10), anyString()))
                    .thenReturn(Map.of("status", "failed"));
            when(responseBuilder.serialize(anyMap(), eq("xml"), eq(null)))
                    .thenReturn("<subsonic-response status=\"failed\"/>");

            mvc.perform(get("/rest/getMusicDirectory"))
                    .andExpect(status().isOk());
        }
    }


    @Nested
    @DisplayName("Search 端点")
    class SearchEndpoints {

        @Test
        @DisplayName("search2 空查询 → 空结果")
        void search2EmptyQuery() throws Exception {
            when(responseBuilder.buildOk(anyMap()))
                    .thenReturn(Map.of("status", "ok"));
            when(responseBuilder.serialize(anyMap(), eq("xml"), eq(null)))
                    .thenReturn("<subsonic-response status=\"ok\"/>");

            mvc.perform(get("/rest/search2").param("query", ""))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("search2 带查询 → 三路搜索")
        void search2WithQuery() throws Exception {
            when(searchService.searchSongs(anyString(), anyInt(), anyInt()))
                    .thenReturn(List.of());
            when(searchService.searchAlbums(anyString(), anyInt(), anyInt()))
                    .thenReturn(List.of());
            when(searchService.searchArtists(anyString(), anyInt(), anyInt()))
                    .thenReturn(List.of());
            when(responseBuilder.buildOk(anyMap()))
                    .thenReturn(Map.of("status", "ok"));
            when(responseBuilder.serialize(anyMap(), eq("xml"), eq(null)))
                    .thenReturn("<subsonic-response status=\"ok\"/>");

            mvc.perform(get("/rest/search2").param("query", "test"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("search3 支持分页参数")
        void search3WithPagination() throws Exception {
            when(searchService.searchSongs(anyString(), anyInt(), anyInt()))
                    .thenReturn(List.of());
            when(searchService.searchAlbums(anyString(), anyInt(), anyInt()))
                    .thenReturn(List.of());
            when(searchService.searchArtists(anyString(), anyInt(), anyInt()))
                    .thenReturn(List.of());
            when(responseBuilder.buildOk(anyMap()))
                    .thenReturn(Map.of("status", "ok"));
            when(responseBuilder.serialize(anyMap(), eq("xml"), eq(null)))
                    .thenReturn("<subsonic-response status=\"ok\"/>");

            mvc.perform(get("/rest/search3")
                            .param("query", "test")
                            .param("artistCount", "10")
                            .param("albumCount", "20")
                            .param("songCount", "50"))
                    .andExpect(status().isOk());
        }
    }


    @Nested
    @DisplayName("Playlist 端点")
    class PlaylistEndpoints {

        @Test
        @DisplayName("getPlaylists → 返回列表")
        void getPlaylists() throws Exception {
            when(responseBuilder.buildOk(anyMap()))
                    .thenReturn(Map.of("status", "ok"));
            when(responseBuilder.serialize(anyMap(), eq("xml"), eq(null)))
                    .thenReturn("<subsonic-response status=\"ok\"/>");

            mvc.perform(get("/rest/getPlaylists"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("getPlaylist 缺少 id → error")
        void getPlaylistNoId() throws Exception {
            when(responseBuilder.buildError(eq(70), anyString()))
                    .thenReturn(Map.of("status", "failed"));
            when(responseBuilder.serialize(anyMap(), eq("xml"), eq(null)))
                    .thenReturn("<subsonic-response status=\"failed\"/>");

            mvc.perform(get("/rest/getPlaylist"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("createPlaylist 缺少 name/playlistId → error")
        void createPlaylistNoName() throws Exception {
            when(responseBuilder.buildError(eq(10), anyString()))
                    .thenReturn(Map.of("status", "failed"));
            when(responseBuilder.serialize(anyMap(), eq("xml"), eq(null)))
                    .thenReturn("<subsonic-response status=\"failed\"/>");

            mvc.perform(post("/rest/createPlaylist"))
                    .andExpect(status().isOk());
        }
    }


    @Nested
    @DisplayName(".view 后缀兼容")
    class ViewSuffix {

        @Test
        @DisplayName("ping.view 等同于 ping")
        void pingView() throws Exception {
            when(responseBuilder.buildOk(anyMap()))
                    .thenReturn(Map.of("status", "ok"));
            when(responseBuilder.serialize(anyMap(), eq("xml"), eq(null)))
                    .thenReturn("<subsonic-response status=\"ok\"/>");

            mvc.perform(get("/rest/ping.view"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("POST .view 后缀")
        void postView() throws Exception {
            when(responseBuilder.buildOk(anyMap()))
                    .thenReturn(Map.of("status", "ok"));
            when(responseBuilder.serialize(anyMap(), eq("xml"), eq(null)))
                    .thenReturn("<subsonic-response status=\"ok\"/>");

            mvc.perform(post("/rest/ping.view"))
                    .andExpect(status().isOk());
        }
    }


    @Nested
    @DisplayName("JSON 格式响应")
    class JsonFormat {

        @Test
        @DisplayName("f=json → JSON 响应")
        void jsonFormat() throws Exception {
            when(responseBuilder.buildOk(anyMap()))
                    .thenReturn(Map.of("status", "ok"));
            when(responseBuilder.serialize(anyMap(), eq("json"), eq(null)))
                    .thenReturn("{\"subsonic-response\":{\"status\":\"ok\"}}");

            mvc.perform(get("/rest/ping").param("f", "json"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/json;charset=UTF-8"))
                    .andExpect(content().string("{\"subsonic-response\":{\"status\":\"ok\"}}"));
        }

        @Test
        @DisplayName("f=jsonp + callback → JSONP 响应")
        void jsonpFormat() throws Exception {
            when(responseBuilder.buildOk(anyMap()))
                    .thenReturn(Map.of("status", "ok"));
            when(responseBuilder.serialize(anyMap(), eq("jsonp"), eq("myCallback")))
                    .thenReturn("myCallback({\"subsonic-response\":{\"status\":\"ok\"}})");

            mvc.perform(get("/rest/ping")
                            .param("f", "jsonp")
                            .param("callback", "myCallback"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/json;charset=UTF-8"))
                    .andExpect(content().string("myCallback({\"subsonic-response\":{\"status\":\"ok\"}})"));
        }
    }
}
