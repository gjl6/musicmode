package com.gjl.music.module;

import lombok.extern.slf4j.Slf4j;

import javax.tools.*;
import java.io.*;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Java 源码动态编译器 —— 将用户上传的 .java 文件编译并加载为 Class。
 *
 * <p>使用 JDK 内置 {@link javax.tools.JavaCompiler}，无需额外依赖。
 */
@Slf4j
public class JavaSourceCompiler {

    private final Path classOutputDir;

    public JavaSourceCompiler() {
        try {
            this.classOutputDir = Files.createTempDirectory("cp-compile-");
            this.classOutputDir.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new RuntimeException("Cannot create temp dir for compilation", e);
        }
    }

    /**
     * 编译 Java 源码并加载类。
     *
     * @param className  全限定类名，如 "com.gjl.music.module.MyProvider"
     * @param sourceCode 完整的 Java 源码
     * @return 编译后的 Class 对象
     */
    public Class<?> compile(String className, String sourceCode) throws Exception {
        String simpleName = className.contains(".")
                ? className.substring(className.lastIndexOf('.') + 1)
                : className;

        Path srcFile = classOutputDir.resolve(simpleName + ".java");
        Files.writeString(srcFile, sourceCode, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new RuntimeException("No JavaCompiler available — run with JDK, not JRE");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, null)) {
            fm.setLocation(StandardLocation.CLASS_OUTPUT, List.of(classOutputDir.toFile()));

            Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromFiles(
                    List.of(srcFile.toFile()));
            List<String> options = List.of("-proc:none", "-Xlint:-options");
            JavaCompiler.CompilationTask task = compiler.getTask(null, fm, diagnostics, options, null, units);

            if (!task.call()) {
                StringBuilder errs = new StringBuilder("Compilation failed:\n");
                for (Diagnostic<?> d : diagnostics.getDiagnostics()) {
                    long line = d.getLineNumber();
                    errs.append(line > 0
                            ? String.format("  Line %d: %s%n", line, d.getMessage(null))
                            : String.format("  %s%n", d.getMessage(null)));
                }
                throw new RuntimeException(errs.toString());
            }
        }

        try (java.net.URLClassLoader loader = new java.net.URLClassLoader(
                new java.net.URL[]{classOutputDir.toUri().toURL()},
                getClass().getClassLoader())) {
            return loader.loadClass(className);
        }
    }

    /**
     * 从 Class 中提取 @ConfigParam 注解的字段信息。
     */
    public static List<Map<String, Object>> extractParams(Class<?> clazz) {
        List<Map<String, Object>> params = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            ConfigParam ann = field.getAnnotation(ConfigParam.class);
            if (ann == null) continue;

            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", field.getName());
            p.put("label", ann.label());
            p.put("type", ann.type());
            p.put("required", ann.required());
            p.put("sensitive", ann.sensitive());

            field.setAccessible(true);
            try {
                Object defaultVal = field.get(clazz.getDeclaredConstructor().newInstance());
                p.put("value", defaultVal != null ? String.valueOf(defaultVal) : "");
            } catch (Exception ignored) {
                p.put("value", "");
            }

            params.add(p);
        }
        return params;
    }

    /** 生成模板代码 */
    public static String generateTemplate(String className, String label) {
        return """
package com.gjl.music.module;

import com.gjl.music.module.AbstractCustomProvider;
import com.gjl.music.module.ConfigParam;
import com.gjl.music.module.song.enrich.SongProvider;
import com.gjl.music.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/**
 * %s —— 自定义标签源。
 *
 * <h3>模板使用说明</h3>
 * <ol>
 *   <li>把本文件中的 TODO 替换为你的真实 API 调用</li>
 *   <li>保留不需要的示例方法注释掉或删除即可</li>
 *   <li>上传 .java 文件后，@{@code @ConfigParam} 声明的参数会自动出现在前端配置页</li>
 * </ol>
 *
 * <h3>可用辅助方法（继承自 AbstractCustomProvider）</h3>
 * <pre>
 * // ── 配置读取 ──
 * String  v = getConfig("字段名");          // 读取 @ConfigParam 声明的配置值
 * int     n = getConfigInt("字段名", 默认值); // 读取 int 型配置
 *
 * // ── HTTP 请求（抛出 Exception 时自动跳过，返回空列表）──
 * String body = httpGet(url, Map.of("Header", "value"));
 * String body = httpPost(url, jsonBody, Map.of("Authorization", "Bearer xxx"));
 *
 * // ── URL 编码 ──
 * String enc = encode("中文关键词");
 *
 * // ── JSON 解析 ──
 * JsonNode root = parseJson(jsonString);
 * List&lt;JsonNode&gt; arr = parseJsonArray(jsonString, "$.data.list");
 * String  s = string(node, "fieldName");
 * int     i = intValue(node, "count");
 *
 * // ── 结果构造 ──
 * MusicMetadata m = createResult("标题", "歌手", "专辑");
 *
 * // 手动构造（更多字段）：
 * MusicMetadata m = new MusicMetadata();
 * Song song = new Song();
 * song.setTitle("歌名"); song.setDuration(245);
 * song.setYear("2024"); song.setTrackNumber(1);
 * m.addSong(song);
 *
 * Artist artist = new Artist();
 * artist.setArtistName("歌手名");
 * m.addArtist(artist);
 *
 * Album album = new Album();
 * album.setAlbumName("专辑名");
 * album.setAlbumCover(coverUrl);
 * album.setAlbumYear(2024);
 * m.addAlbum(album);
 * </pre>
 */
public class %s extends AbstractCustomProvider {

    @ConfigParam(label = "搜索地址", required = true)
    private String searchUrl = "https://api.example.com/search";

    @ConfigParam(label = "详情地址")
    private String detailUrl = "";

    @ConfigParam(label = "歌词地址")
    private String lyricUrl = "";

    @ConfigParam(label = "API 密钥", sensitive = true)
    private String apiKey = "";

    @ConfigParam(label = "请求间隔(ms)", type = "INT")
    private int rate_limit_ms = 240;

    @ConfigParam(label = "超时(秒)", type = "INT")
    private int timeout_seconds = 15;

    @ConfigParam(label = "重试次数", type = "INT")
    private int rate_limit_retries = 3;

    @ConfigParam(label = "退避间隔(ms)", type = "INT")
    private int rate_limit_backoff_ms = 30000;

    @ConfigParam(label = "包含歌词", type = "BOOLEAN")
    private boolean fetchLyric = true;

    @Override
    public String name() {
        return "custom:%s";
    }

    @Override
    public List<MusicMetadata> search(String title, String artist) {
        String query = (title != null ? title : "")
                     + (artist != null ? " " + artist : "");

        int maxRetries = rateLimitRetries();
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            acquire();
            try {
                String url = searchUrl + "?keyword=" + encode(query);
                String json = httpGet(url,
                    Map.of("Authorization", "Bearer " + apiKey));

                List<MusicMetadata> results = new ArrayList<>();
                for (JsonNode node : parseJsonArray(json, "$.data.songs")) {
                    MusicMetadata m = new MusicMetadata();
                    Song song = new Song();
                    song.setTitle(string(node, "name"));
                    song.setDuration(intValue(node, "duration"));
                    m.addSong(song);

                    Artist artistObj = new Artist();
                    artistObj.setArtistName(string(node, "singer"));
                    m.addArtist(artistObj);

                    Album album = new Album();
                    album.setAlbumName(string(node, "album"));
                    m.addAlbum(album);

                    results.add(m);
                }
                if (results.isEmpty()) {
                    setLastError("搜索成功但未解析到结果 —— 请检查 parseJsonArray 的 JSON 路径是否正确");
                }
                return results;
            } catch (Exception e) {
                setLastError("搜索异常(第" + (attempt + 1) + "次尝试): " + e.toString());
                if (attempt < maxRetries) continue;
                setLastError("搜索失败(已重试" + maxRetries + "次): " + e.toString());
                return List.of();
            } finally {
                release();
            }
        }
        return List.of();
    }

    @Override
    public MusicMetadata getSongDetail(String songId) {
        return null;
    }

    @Override
    public String buildCoverUrl(String albumId) {
        return null;
    }
}
""".formatted(label, className, label);
    }
}
