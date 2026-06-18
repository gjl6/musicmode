package com.gjl.music.playback.infra.subsonic;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Slf4j
public class SubsonicResponseBuilder {

    private static final ObjectMapper JSON;
    private static final XmlMapper XML;

    static {
        JSON = new ObjectMapper()
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        XML = XmlMapper.builder()
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .build();
    }

    private final String serverVersion;
    private final boolean openSubsonic;

    public SubsonicResponseBuilder(String serverVersion, boolean openSubsonic) {
        this.serverVersion = serverVersion;
        this.openSubsonic = openSubsonic;
    }


    @SuppressWarnings("unchecked")
    public Map<String, Object> buildOk(Object payload) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("status", "ok");
        root.put("version", "1.16.1");
        root.put("type", "Navidrome");
        root.put("serverVersion", serverVersion);
        root.put("openSubsonic", openSubsonic);

        if (payload instanceof Map) {
            root.putAll((Map<String, Object>) payload);
        } else if (payload != null) {
            root.put("data", payload);
        }

        return root;
    }


    public Map<String, Object> buildError(int code, String message) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("status", "failed");
        root.put("version", "1.16.1");
        root.put("type", "Navidrome");
        root.put("serverVersion", serverVersion);
        root.put("openSubsonic", openSubsonic);

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        root.put("error", error);

        return root;
    }


    public String serialize(Map<String, Object> data, String format, String callback) {
        Map<String, Object> wrapped = wrap(data, format);

        try {
            if ("json".equals(format) || "jsonp".equals(format)) {
                String json = JSON.writeValueAsString(wrapped);
                if ("jsonp".equals(format) && callback != null && !callback.isBlank()) {
                    return callback + "(" + json + ")";
                }
                return json;
            } else {
                                Map<String, Object> clean = sanitizeMap(wrapped);
                return XML.writeValueAsString(clean.get("subsonic-response"));
            }
        } catch (JsonProcessingException e) {
            log.error("Subsonic 响应序列化失败", e);
            return "{\"subsonic-response\":{\"status\":\"failed\",\"error\":{\"code\":0,\"message\":\"Serialization error\"}}}";
        }
    }


    @SuppressWarnings("unchecked")
    private static Map<String, Object> sanitizeMap(Map<String, Object> map) {
        Map<String, Object> clean = new LinkedHashMap<>(map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            clean.put(entry.getKey(), sanitizeValue(entry.getValue()));
        }
        return clean;
    }


    private static Object sanitizeValue(Object value) {
        if (value instanceof String s) {
            return sanitizeString(s);
        } else if (value instanceof Map<?, ?> m) {
                        Map<String, Object> clean = new LinkedHashMap<>(m.size());
            for (Map.Entry<?, ?> e : m.entrySet()) {
                clean.put(String.valueOf(e.getKey()), sanitizeValue(e.getValue()));
            }
            return clean;
        } else if (value instanceof List<?> l) {
            return l.stream().map(SubsonicResponseBuilder::sanitizeValue).toList();
        }
        return value;
    }


    private static String sanitizeString(String s) {
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
                            } else if (sb != null) {
                sb.append(c);
            }
        }
        return sb == null ? s : sb.toString();
    }


    private Map<String, Object> wrap(Map<String, Object> data, String format) {
        Map<String, Object> wrapped = new LinkedHashMap<>();
        if ("json".equals(format) || "jsonp".equals(format)) {
            wrapped.put("subsonic-response", data);
        } else {
                        wrapped.put("subsonic-response", data);
        }
        return wrapped;
    }
}
