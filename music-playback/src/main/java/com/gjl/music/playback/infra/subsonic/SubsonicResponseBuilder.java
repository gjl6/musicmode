package com.gjl.music.playback.infra.subsonic;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Subsonic XML/JSON 双序列化响应构建器。
 *
 * <h3>响应结构</h3>
 * <pre>{@code
 * <subsonic-response xmlns="http://subsonic.org/restapi"
 *     status="ok" version="1.16.1" type="Music Mode" ...>
 *   ...payload...
 * </subsonic-response>
 * }</pre>
 *
 * <p>XML 使用 StAX 手写以保证符合 Subsonic XSD（属性 vs 子元素）。
 * JSON 使用 Jackson ObjectMapper。
 */
@Slf4j
public class SubsonicResponseBuilder {

    private static final String NS = "http://subsonic.org/restapi";
    private static final ObjectMapper JSON;

    static {
        JSON = new ObjectMapper()
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    private final String serverVersion;
    private final boolean openSubsonic;

    public SubsonicResponseBuilder(String serverVersion, boolean openSubsonic) {
        this.serverVersion = serverVersion;
        this.openSubsonic = openSubsonic;
    }

    /**
     * 构建成功响应的 Map 结构。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> buildOk(Object payload) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("status", "ok");
        root.put("version", "1.16.1");
        root.put("type", "Music Mode");
        root.put("serverVersion", serverVersion);
        root.put("openSubsonic", openSubsonic);

        if (payload instanceof Map) {
            root.putAll((Map<String, Object>) payload);
        } else if (payload != null) {
            root.put("data", payload);
        }

        return root;
    }

    /**
     * 构建错误响应的 Map 结构。
     */
    public Map<String, Object> buildError(int code, String message) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("status", "failed");
        root.put("version", "1.16.1");
        root.put("type", "Music Mode");
        root.put("serverVersion", serverVersion);
        root.put("openSubsonic", openSubsonic);

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        root.put("error", error);

        return root;
    }

    // ── 元数据键名（这些不序列化为子元素，而是属性/特殊处理）──
    private static final String[] META_KEYS = {
        "status", "version", "type", "serverVersion", "openSubsonic", "error"
    };

    private static boolean isMetaKey(String key) {
        for (String mk : META_KEYS) {
            if (mk.equals(key)) return true;
        }
        return false;
    }

    /**
     * 序列化为 XML 或 JSON 字符串。
     */
    public String serialize(Map<String, Object> data, String format, String callback) {
        try {
            if ("json".equals(format) || "jsonp".equals(format)) {
                Map<String, Object> wrapped = new LinkedHashMap<>();
                wrapped.put("subsonic-response", data);
                String json = JSON.writeValueAsString(wrapped);
                if ("jsonp".equals(format) && callback != null && !callback.isBlank()) {
                    return callback + "(" + json + ")";
                }
                return json;
            } else {
                // XML (default) — StAX 手写以保证符合 Subsonic XSD
                return serializeXml(data);
            }
        } catch (JsonProcessingException | XMLStreamException e) {
            log.error("Subsonic 响应序列化失败", e);
            return "{\"subsonic-response\":{\"status\":\"failed\","
                    + "\"error\":{\"code\":0,\"message\":\"Serialization error\"}}}";
        }
    }

    // ═══════════════════════════════════════════════════════
    // XML 序列化（StAX）
    // ═══════════════════════════════════════════════════════

    private String serializeXml(Map<String, Object> data) throws XMLStreamException {
        StringWriter sw = new StringWriter();
        XMLStreamWriter w = XMLOutputFactory.newInstance().createXMLStreamWriter(sw);

        w.writeStartDocument("UTF-8", "1.0");
        w.writeStartElement("subsonic-response");
        w.writeDefaultNamespace(NS);

        // 属性
        writeAttr(w, "status", data.get("status"));
        writeAttr(w, "version", data.get("version"));
        writeAttr(w, "type", data.get("type"));
        writeAttr(w, "serverVersion", data.get("serverVersion"));
        Object os = data.get("openSubsonic");
        if (Boolean.TRUE.equals(os)) {
            w.writeAttribute("openSubsonic", "true");
        }

        // error 元素（特殊处理）
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) data.get("error");
        if (error != null) {
            w.writeStartElement("error");
            writeAttr(w, "code", error.get("code"));
            writeAttr(w, "message", error.get("message"));
            w.writeEndElement();
        }

        // 非元数据的子元素
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!isMetaKey(entry.getKey())) {
                writeXmlValue(w, entry.getKey(), entry.getValue());
            }
        }

        w.writeEndElement(); // subsonic-response
        w.writeEndDocument();
        w.flush();
        return sw.toString();
    }

    /** 递归写入 XML 元素 */
    @SuppressWarnings("unchecked")
    private void writeXmlValue(XMLStreamWriter w, String key, Object value) throws XMLStreamException {
        if (value == null) return;
        if (value instanceof Map<?, ?> m) {
            w.writeStartElement(key);
            // ★ 两趟遍历：先写所有属性，再写子元素
            //    一旦写了子元素，Woodstox 不允许再写属性（XML 规范要求属性在前）
            List<Map.Entry<?, ?>> children = null;
            for (Map.Entry<?, ?> entry : m.entrySet()) {
                Object v = entry.getValue();
                String k = String.valueOf(entry.getKey());
                if (isSimpleAttr(v)) {
                    writeAttr(w, k, v);
                } else {
                    if (children == null) children = new ArrayList<>();
                    children.add(entry);
                }
            }
            if (children != null) {
                for (Map.Entry<?, ?> child : children) {
                    writeXmlValue(w, String.valueOf(child.getKey()), child.getValue());
                }
            }
            w.writeEndElement();
        } else if (value instanceof List<?> list) {
            for (Object item : list) {
                writeXmlValue(w, key, item);
            }
        } else if (value instanceof String s) {
            w.writeStartElement(key);
            w.writeCharacters(sanitizeString(s));
            w.writeEndElement();
        } else if (value instanceof Number || value instanceof Boolean) {
            w.writeStartElement(key);
            w.writeAttribute("value", String.valueOf(value));
            w.writeEndElement();
        } else {
            w.writeStartElement(key);
            w.writeCharacters(sanitizeString(String.valueOf(value)));
            w.writeEndElement();
        }
    }

    /** 是否为简单属性值（可直接作为 XML 属性） */
    private static boolean isSimpleAttr(Object v) {
        return v instanceof String
                || v instanceof Number
                || v instanceof Boolean
                || v == null;
    }

    private static void writeAttr(XMLStreamWriter w, String name, Object value) throws XMLStreamException {
        if (value == null) return;
        w.writeAttribute(name, sanitizeString(String.valueOf(value)));
    }

    // ═══════════════════════════════════════════════════════
    // 字符串清理
    // ═══════════════════════════════════════════════════════

    /**
     * 移除字符串中的 XML 非法控制字符（除了 \\t \\n \\r）。
     * XML 1.0 允许的字符范围：\\u0009 \\u000A \\u000D \\u0020-\\uD7FF \\uE000-\\uFFFD
     */
    static String sanitizeString(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder sb = null;
        for (int i = 0, len = s.length(); i < len; i++) {
            char c = s.charAt(i);
            if (c == 0x00 || (c < 0x20 && c != 0x09 && c != 0x0A && c != 0x0D)
                    || (c >= 0x7F && c <= 0x9F)) {
                if (sb == null) {
                    sb = new StringBuilder(len);
                    sb.append(s, 0, i);
                }
                // 跳过非法字符
            } else if (sb != null) {
                sb.append(c);
            }
        }
        return sb == null ? s : sb.toString();
    }
}
