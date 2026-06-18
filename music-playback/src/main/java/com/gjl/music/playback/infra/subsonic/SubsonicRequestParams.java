package com.gjl.music.playback.infra.subsonic;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;


public class SubsonicRequestParams {

    private final Map<String, String> params = new HashMap<>();

    public SubsonicRequestParams(HttpServletRequest request) {
                request.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0) params.put(k, v[0]);
        });
    }

    public String get(String key) { return params.get(key); }
    public String get(String key, String defaultValue) { return params.getOrDefault(key, defaultValue); }

        public String version() { return get("v", "1.16.1"); }
    public String client() { return get("c", "generic"); }
    public String format() { return get("f", "xml"); }
    public String username() { return get("u"); }
    public String password() { return get("p"); }
    public String token() { return get("t"); }
    public String salt() { return get("s"); }
    public String callback() { return get("callback"); }

    public int getInt(String key, int defaultValue) {
        try { return Integer.parseInt(get(key)); } catch (Exception e) { return defaultValue; }
    }
}
