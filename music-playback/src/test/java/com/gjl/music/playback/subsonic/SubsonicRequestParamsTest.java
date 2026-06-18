package com.gjl.music.playback.subsonic;
import com.gjl.music.playback.infra.subsonic.SubsonicRequestParams;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
@DisplayName("SubsonicRequestParams 单元测试")
class SubsonicRequestParamsTest {

    @Mock
    private HttpServletRequest request;

    private SubsonicRequestParams params;

    @BeforeEach
    void setUp() {
        when(request.getParameterMap()).thenReturn(Map.of(
                "u", new String[]{"testuser"},
                "v", new String[]{"1.16.1"},
                "c", new String[]{"DSub"},
                "f", new String[]{"json"},
                "t", new String[]{"md5token"},
                "s", new String[]{"salt123"},
                "callback", new String[]{"cbFunc"},
                "id", new String[]{"42"},
                "size", new String[]{"10"},
                "offset", new String[]{"5"}
        ));
        params = new SubsonicRequestParams(request);
    }

    @Test
    @DisplayName("标准参数提取")
    void standardParams() {
        assertEquals("1.16.1", params.version());
        assertEquals("DSub", params.client());
        assertEquals("json", params.format());
        assertEquals("testuser", params.username());
        assertEquals("md5token", params.token());
        assertEquals("salt123", params.salt());
        assertEquals("cbFunc", params.callback());
    }

    @Test
    @DisplayName("默认值生效（无参数时）")
    void defaults() {
        when(request.getParameterMap()).thenReturn(Collections.emptyMap());
        SubsonicRequestParams empty = new SubsonicRequestParams(request);

        assertEquals("1.16.1", empty.version());
        assertEquals("generic", empty.client());
        assertEquals("xml", empty.format());
        assertNull(empty.username());
        assertNull(empty.password());
    }

    @Test
    @DisplayName("get(key, defaultValue) 默认值")
    void getWithDefault() {
        assertEquals("testuser", params.get("u", "fallback"));
        assertEquals("fallback", params.get("nonexistent", "fallback"));
        assertEquals("42", params.get("id", "0"));
    }

    @Test
    @DisplayName("getInt 正确解析")
    void getInt() {
        assertEquals(42, params.getInt("id", 0));
        assertEquals(10, params.getInt("size", 0));
        assertEquals(5, params.getInt("offset", 0));
    }

    @Test
    @DisplayName("getInt 非法值返回默认值")
    void getIntInvalid() {
        assertEquals(0, params.getInt("u", 0));
        assertEquals(-1, params.getInt("nonexistent", -1));
    }

    @Test
    @DisplayName("password 参数返回 null（当前设计中）")
    void passwordReturnsNull() {
                        assertNull(params.password());
    }
}
