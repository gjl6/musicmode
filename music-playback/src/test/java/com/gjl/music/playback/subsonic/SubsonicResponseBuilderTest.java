package com.gjl.music.playback.subsonic;
import com.gjl.music.playback.infra.subsonic.SubsonicResponseBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


@DisplayName("SubsonicResponseBuilder 单元测试")
class SubsonicResponseBuilderTest {

    private SubsonicResponseBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new SubsonicResponseBuilder("1.0.0-SNAPSHOT", true);
    }


    @Nested
    @DisplayName("buildOk 成功响应")
    class BuildOk {

        @Test
        @DisplayName("空 payload")
        void emptyPayload() {
            Map<String, Object> result = builder.buildOk(null);
            assertEquals("ok", result.get("status"));
            assertEquals("1.16.1", result.get("version"));
            assertEquals("Navidrome", result.get("type"));
            assertEquals("1.0.0-SNAPSHOT", result.get("serverVersion"));
            assertEquals(true, result.get("openSubsonic"));
        }

        @Test
        @DisplayName("Map payload 合并到根")
        void mapPayload() {
            Map<String, Object> data = Map.of("key", "value", "number", 42);
            Map<String, Object> result = builder.buildOk(data);

            assertEquals("ok", result.get("status"));
            assertEquals("value", result.get("key"));
            assertEquals(42, result.get("number"));
        }

        @Test
        @DisplayName("非Map payload 包装为 data 字段")
        void nonMapPayload() {
            Map<String, Object> result = builder.buildOk("hello");
            assertEquals("ok", result.get("status"));
            assertEquals("hello", result.get("data"));
        }
    }


    @Nested
    @DisplayName("buildError 错误响应")
    class BuildError {

        @Test
        @DisplayName("错误码和消息正确")
        void errorCodeAndMessage() {
            Map<String, Object> result = builder.buildError(40, "认证失败");

            assertEquals("failed", result.get("status"));
            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) result.get("error");
            assertNotNull(error);
            assertEquals(40, error.get("code"));
            assertEquals("认证失败", error.get("message"));
        }

        @Test
        @DisplayName("错误码 70")
        void error70() {
            Map<String, Object> result = builder.buildError(70, "数据未找到");
            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) result.get("error");
            assertEquals(70, error.get("code"));
        }

        @Test
        @DisplayName("错误码 0")
        void error0() {
            Map<String, Object> result = builder.buildError(0, "未知错误");
            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) result.get("error");
            assertEquals(0, error.get("code"));
        }
    }


    @Nested
    @DisplayName("serialize 序列化")
    class Serialize {

        @Test
        @DisplayName("XML 序列化 ping 响应")
        void xmlPing() {
            Map<String, Object> data = builder.buildOk(Map.of());
            String xml = builder.serialize(data, "xml", null);
            assertNotNull(xml);
            assertFalse(xml.isEmpty());
            assertTrue(xml.contains("ok"));
        }

        @Test
        @DisplayName("JSON 序列化")
        void jsonPing() {
            Map<String, Object> data = builder.buildOk(Map.of());
            String json = builder.serialize(data, "json", null);
            assertNotNull(json);
            assertTrue(json.contains("\"subsonic-response\""));
            assertTrue(json.contains("\"status\""));
            assertTrue(json.contains("\"ok\""));
        }

        @Test
        @DisplayName("JSONP 序列化（含 callback）")
        void jsonpWithCallback() {
            Map<String, Object> data = builder.buildOk(Map.of());
            String jsonp = builder.serialize(data, "jsonp", "myCallback");
            assertNotNull(jsonp);
            assertTrue(jsonp.startsWith("myCallback("));
            assertTrue(jsonp.endsWith(")"));
            assertTrue(jsonp.contains("\"subsonic-response\""));
        }

        @Test
        @DisplayName("XML 错误序列化")
        void xmlError() {
            Map<String, Object> data = builder.buildError(40, "认证失败");
            String xml = builder.serialize(data, "xml", null);
            assertNotNull(xml);
            assertFalse(xml.isEmpty());
            assertTrue(xml.contains("failed") || xml.contains("40"));
        }

        @Test
        @DisplayName("JSON 错误序列化")
        void jsonError() {
            Map<String, Object> data = builder.buildError(0, "内部错误");
            String json = builder.serialize(data, "json", null);
            assertNotNull(json);
            assertTrue(json.contains("\"status\":\"failed\""));
            assertTrue(json.contains("\"code\":0"));
        }

        @Test
        @DisplayName("默认格式 XML（非 json/jsonp 时）")
        void defaultXml() {
            Map<String, Object> data = builder.buildOk(Map.of());
            String xml = builder.serialize(data, "xml", null);
            assertNotNull(xml);
            assertFalse(xml.isEmpty());

                        String xml2 = builder.serialize(data, "raw", null);
            assertNotNull(xml2);
            assertFalse(xml2.isEmpty());
        }

        @Test
        @DisplayName("JSONP 且 callback 为空 → 降级 JSON")
        void jsonpBlankCallback() {
            Map<String, Object> data = builder.buildOk(Map.of());
            String json = builder.serialize(data, "jsonp", "");
            assertNotNull(json);
            assertTrue(json.contains("\"subsonic-response\""));
            assertFalse(json.startsWith("("));
            assertFalse(json.endsWith(")"));
        }
    }


    @Nested
    @DisplayName("复杂数据序列化")
    class ComplexData {

        @Test
        @DisplayName("嵌套 Map JSON 序列化正确")
        void nestedMapJson() {
            Map<String, Object> license = Map.of("valid", true,
                    "email", "user@example.com",
                    "trial", false);
            Map<String, Object> data = builder.buildOk(Map.of("license", license));
            String json = builder.serialize(data, "json", null);

            assertTrue(json.contains("\"license\""));
            assertTrue(json.contains("\"valid\""));
            assertTrue(json.contains("true"));
        }

        @Test
        @DisplayName("列表数据 JSON 序列化正确")
        void listDataJson() {
            Map<String, Object> data = builder.buildOk(Map.of(
                    "items", List.of("a", "b", "c"),
                    "count", 3));
            String json = builder.serialize(data, "json", null);

            assertTrue(json.contains("\"items\""));
            assertTrue(json.contains("\"a\""));
            assertTrue(json.contains("\"b\""));
            assertTrue(json.contains("\"c\""));
            assertTrue(json.contains("\"count\""));
            assertTrue(json.contains("3"));
        }
    }
}
