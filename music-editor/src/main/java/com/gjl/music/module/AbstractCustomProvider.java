package com.gjl.music.module;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjl.music.config.ConfigService;
import com.gjl.music.model.*;
import com.gjl.music.infra.enrich.ProviderRateLimiter;
import com.gjl.music.module.song.enrich.SongProvider;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * 自定义标签源抽象基类 —— 提供 HTTP 请求、JSON 解析、参数注入、限流等辅助方法。
 *
 * <p>用户在子类中实现 {@link #search(String, String)} 即可。
 * 字段上的 {@link ConfigParam} 注解自动被系统解析，配置值由
 * {@link com.gjl.music.config.CustomProviderManager} 在创建实例后注入。
 */
@Slf4j
public abstract class AbstractCustomProvider implements SongProvider {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 由 CustomProviderManager 注入的配置值（key = @ConfigParam 字段名） */
    private volatile Map<String, String> configValues = Map.of();

    /** 内置限流器，由 setConfigValues 配置 */
    protected final ProviderRateLimiter limiter = new ProviderRateLimiter(240, 6, 15, 3, 30000);

    /** 最后一次错误的详细信息，供前端诊断使用 */
    private volatile String lastError = null;

    public void setConfigValues(Map<String, String> values) {
        this.configValues = Map.copyOf(values);
        // 自动从配置读取限流参数
        limiter.updateParams(
                getConfigLong("rate_limit_ms", 240),
                getConfigInt("timeout_seconds", 15),
                getConfigInt("rate_limit_retries", 3),
                getConfigLong("rate_limit_backoff_ms", 30000));
    }

    private long getConfigLong(String key, long def) {
        try { return Long.parseLong(getConfig(key)); }
        catch (NumberFormatException e) { return def; }
    }

    /** 读取前端配置的参数值 */
    protected String getConfig(String name) {
        return configValues.getOrDefault(name, "");
    }

    protected int getConfigInt(String name, int fallback) {
        try { return Integer.parseInt(getConfig(name)); }
        catch (NumberFormatException e) { return fallback; }
    }

    // ── 错误报告 ──

    /**
     * 记录最后一次错误（供前端测试按钮读取）。
     * 子类在 catch 块中调用此方法，错误信息将自动传递到前端。
     */
    protected void setLastError(String error) {
        this.lastError = error;
        log.warn("CustomProvider [{}] error: {}", name(), error);
    }

    /** 获取最后一次错误信息（由测试端点调用） */
    public String getLastError() {
        return lastError;
    }

    // ── 限流辅助 ──

    /** 获取限流许可（建议在搜索前调用） */
    protected void acquire() { limiter.acquire(); }

    /** 释放限流许可（建议在搜索后 finally 中调用） */
    protected void release() { limiter.release(); }

    /** 当前限流重试次数 */
    protected int rateLimitRetries() { return limiter.rateLimitRetries(); }

    /** 超时秒数 */
    protected int timeoutSeconds() { return limiter.timeoutSeconds(); }

    // ── HTTP 辅助 ──

    protected String encode(String s) {
        if (s == null || s.isBlank()) return "";
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    protected String httpGet(String url, Map<String, String> headers) throws Exception {
        log.info("CustomProvider httpGet: {}", url);
        try {
            var builder = HttpRequest.newBuilder().uri(URI.create(url)).GET()
                    .timeout(Duration.ofSeconds(15));
            if (headers != null) headers.forEach(builder::header);
            var resp = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            log.info("CustomProvider httpGet response: {} status={}", url, resp.statusCode());
            return resp.body();
        } catch (Exception e) {
            log.error("CustomProvider httpGet FAILED: {} - {}", url, e.toString());
            throw e;
        }
    }

    protected String httpPost(String url, String body, Map<String, String> headers) throws Exception {
        log.info("CustomProvider httpPost: {} bodyLen={}", url, body != null ? body.length() : 0);
        try {
            var builder = HttpRequest.newBuilder().uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15));
            if (headers != null) headers.forEach(builder::header);
            var resp = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            log.info("CustomProvider httpPost response: {} status={}", url, resp.statusCode());
            return resp.body();
        } catch (Exception e) {
            log.error("CustomProvider httpPost FAILED: {} - {}", url, e.toString());
            throw e;
        }
    }

    // ── JSON 辅助 ──

    protected JsonNode parseJson(String json) throws Exception {
        return JSON.readTree(json);
    }

    /**
     * 从 JSON 中按路径提取数组。支持两种格式：
     * <ul>
     *   <li><b>JSON Pointer</b>（推荐）：以 {@code /} 开头，如
     *       {@code /music.search.SearchCgiService/data/body/song/list}。
     *       使用 RFC 6901 语法，字段名中的 {@code .} 无特殊含义。</li>
     *   <li><b>点号路径</b>（简单场景）：如 {@code $.data.songs} 或 {@code data.songs}。
     *       点号用作层级分隔符，<b>不支持字段名中包含点号</b>的情况。</li>
     * </ul>
     */
    protected List<JsonNode> parseJsonArray(String json, String jsonPath) throws Exception {
        JsonNode root = JSON.readTree(json);
        if (jsonPath == null || jsonPath.isBlank()) {
            if (root.isArray()) return toList(root);
            return List.of(root);
        }

        // JSON Pointer 格式：以 / 开头
        if (jsonPath.startsWith("/")) {
            JsonNode result = root.at(jsonPath);
            if (result.isMissingNode()) return List.of();
            if (result.isArray()) return toList(result);
            return List.of(result);
        }

        // 点号路径格式
        String path = jsonPath.startsWith("$") ? jsonPath.substring(1) : jsonPath;
        for (String seg : path.split("\\.")) {
            seg = seg.trim();
            if (seg.isEmpty()) continue;
            if (root == null) return List.of();
            root = root.get(seg);
        }
        if (root == null) return List.of();
        if (root.isArray()) return toList(root);
        return List.of(root);
    }

    private static List<JsonNode> toList(JsonNode arr) {
        List<JsonNode> list = new ArrayList<>();
        for (JsonNode n : arr) list.add(n);
        return list;
    }

    protected String string(JsonNode node, String path) {
        JsonNode n = resolve(node, path);
        return n != null && !n.isNull() ? n.asText("") : "";
    }

    protected int intValue(JsonNode node, String path) {
        JsonNode n = resolve(node, path);
        return n != null && !n.isNull() ? n.asInt(0) : 0;
    }

    private JsonNode resolve(JsonNode node, String path) {
        if (path == null || path.isEmpty() || ".".equals(path)) return node;
        for (String seg : path.split("\\.")) {
            if (node == null) return null;
            node = node.get(seg);
        }
        return node;
    }

    // ── 结果构造辅助 ──

    protected MusicMetadata createResult(String title, String artist, String album) {
        MusicMetadata m = new MusicMetadata();
        if (title != null && !title.isBlank()) {
            Song s = new Song(); s.setTitle(title); m.addSong(s);
        }
        if (artist != null && !artist.isBlank()) {
            Artist a = new Artist(); a.setArtistName(artist); m.addArtist(a);
        }
        if (album != null && !album.isBlank()) {
            Album al = new Album(); al.setAlbumName(album); m.addAlbum(al);
        }
        return m;
    }

    // ── 必须实现 ──

    @Override public abstract String name();
    @Override public abstract List<MusicMetadata> search(String title, String artist);
}
