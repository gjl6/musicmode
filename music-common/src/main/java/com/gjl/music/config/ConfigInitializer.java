package com.gjl.music.config;

import com.gjl.music.mapper.SystemConfigMapper;
import com.gjl.music.model.SystemConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 配置初始化器 —— 启动时将 application.properties 和硬编码常量同步到 DB。
 *
 * <p>使用 {@code INSERT IGNORE} 语义：仅当 DB 中不存在该 key 时才写入，
 * 用户已修改的值不会被覆盖。
 */
@Slf4j
@Component
public class ConfigInitializer {

    private final SystemConfigMapper mapper;
    private final Environment env;

    public ConfigInitializer(SystemConfigMapper mapper, Environment env) {
        this.mapper = mapper;
        this.env = env;
    }

    @PostConstruct
    public void init() {
        List<SystemConfig> configs = buildAllConfigs();
        if (configs.isEmpty()) return;

        mapper.batchInsertIgnore(configs);
        log.info("ConfigInitializer: {} entries synced (new: keys not overwritten if exist)", configs.size());
    }

    private List<SystemConfig> configs() { return new ArrayList<>(); }

    private List<SystemConfig> buildAllConfigs() {
        List<SystemConfig> list = new ArrayList<>();

        // ════════════════ enrich — 默认限流 ════════════════
        add(list, "enrich.default_provider",
                prop("music.enrich.default-provider", "qqmusic,kugou,kuwo,netease,itunes,musicbrainz,migu"),
                "enrich", "默认搜索源", "逗号分隔的 provider 列表", "LIST", 1);
        add(list, "enrich.artist_provider",
                prop("music.enrich.artist-provider", "netease,qqmusic,itunes,musicbrainz,baidubaike,wikipedia"),
                "enrich", "艺术家详情源", "支持艺术家详情查询的 provider 列表", "LIST", 2);
        add(list, "enrich.album_provider",
                prop("music.enrich.album-provider", "qqmusic,musicbrainz,itunes,netease"),
                "enrich", "专辑详情源", "支持专辑详情查询的 provider 列表", "LIST", 3);
        add(list, "enrich.default.rate_limit_ms",
                prop("music.enrich.providers.default.rate-limit-ms", "240"),
                "enrich", "默认请求间隔(ms)", null, "INT", 4);
        add(list, "enrich.default.max_concurrent",
                prop("music.enrich.providers.default.max-concurrent", "6"),
                "enrich", "默认最大并发", null, "INT", 5);
        add(list, "enrich.default.timeout_seconds",
                prop("music.enrich.providers.default.timeout-seconds", "15"),
                "enrich", "默认超时(秒)", null, "INT", 6);
        add(list, "enrich.default.rate_limit_retries",
                prop("music.enrich.providers.default.rate-limit-retries", "3"),
                "enrich", "默认重试次数", "频控退避重试次数", "INT", 7);
        add(list, "enrich.default.rate_limit_backoff_ms",
                prop("music.enrich.providers.default.rate-limit-backoff-ms", "30000"),
                "enrich", "默认退避间隔(ms)", "指数退避初始等待", "LONG", 7);

        // ════════════════ enrich — Song providers ════════════════
        addEntityProviderConfigs(list, "song", "qqmusic", "QQ音乐", 10,
                new String[][]{
                        {"search_url", "https://u.y.qq.com/cgi-bin/musicu.fcg"},
                        {"album_url", "https://i.y.qq.com/v8/fcg-bin/fcg_v8_album_info_cp.fcg"},
                        {"lyric_url", "https://i.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg"},
                },
                "120", "8", "15", "3", "3000");
        addEntityProviderStatic(list, "song", "qqmusic", "rate_limit_code", "2001", "QQ音乐限流错误码", "INT", 16);

        addEntityProviderConfigs(list, "song", "kugou", "酷狗音乐", 20,
                new String[][]{
                        {"search_url", "http://mobilecdn.kugou.com/api/v3/search/song"},
                        {"detail_url", "http://m.kugou.com/app/i/getSongInfo.php"},
                        {"lyric_search_url", "http://lyrics.kugou.com/search"},
                        {"lyric_download_url", "http://lyrics.kugou.com/download"},
                },
                "240", "6", "15", "3", "30000");
        addEntityProviderStatic(list, "song", "kugou", "referer", "https://www.kugou.com", "酷狗 Referer", "STRING", 25);
        addEntityProviderStatic(list, "song", "kugou", "user_agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", "User-Agent", "STRING", 26);

        addEntityProviderConfigs(list, "song", "kuwo", "酷我音乐", 30,
                new String[][]{
                        {"search_url", "http://search.kuwo.cn/r.s"},
                        {"lyric_url", "https://m.kuwo.cn/newh5/singles/songinfoandlrc"},
                        {"cover_cdn", "https://img4.kuwo.cn/star/albumcover/"},
                        {"cover_cdn_mv", "https://img4.kuwo.cn/wmvpic/"},
                },
                "240", "6", "15", "3", "30000");
        addEntityProviderStatic(list, "song", "kuwo", "referer", "https://www.kuwo.cn", "酷我 Referer", "STRING", 35);
        addEntityProviderStatic(list, "song", "kuwo", "csrf", "music", "酷我 CSRF Token", "STRING", 36);
        addEntityProviderStatic(list, "song", "kuwo", "user_agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", "User-Agent", "STRING", 37);

        addEntityProviderConfigs(list, "song", "netease", "网易云音乐", 40,
                new String[][]{
                        {"search_url", "https://music.163.com/api/search/get"},
                        {"detail_url", "https://music.163.com/api/song/detail"},
                        {"lyric_url", "https://music.163.com/api/song/lyric"},
                        {"artist_search_url", "https://music.163.com/api/search/get"},
                        {"artist_detail_url", "https://music.163.com/api/artist"},
                },
                "240", "6", "15", "3", "30000");
        addEntityProviderStatic(list, "song", "netease", "referer", "https://music.163.com", "网易云 Referer", "STRING", 45);
        addEntityProviderStatic(list, "song", "netease", "user_agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", "User-Agent", "STRING", 46);

        addEntityProviderConfigs(list, "song", "itunes", "iTunes", 50,
                new String[][]{
                        {"search_url", "https://itunes.apple.com/search"},
                        {"lookup_url", "https://itunes.apple.com/lookup"},
                },
                "2000", "6", "15", "1", "30000");

        addEntityProviderConfigs(list, "song", "musicbrainz", "MusicBrainz", 60,
                new String[][]{
                        {"search_url", "https://musicbrainz.org/ws/2/recording"},
                        {"release_url", "https://musicbrainz.org/ws/2/release"},
                },
                "1500", "6", "20", "3", "60000");
        addEntityProviderStatic(list, "song", "musicbrainz", "user_agent", "MusicPipeline/1.0", "User-Agent", "STRING", 63);
        addEntityProviderStatic(list, "song", "musicbrainz", "cover_art_tpl",
                "https://coverartarchive.org/release/%s/front", "封面归档URL模板", "STRING", 64);

        addEntityProviderConfigs(list, "song", "migu", "咪咕音乐", 70,
                new String[][]{
                        {"search_url", "https://m.music.migu.cn/migu/remoting/scr_search_tag"},
                        {"song_url", "https://c.musicapp.migu.cn/MIGUM2/v2.0/music/song"},
                },
                "1000", "6", "15", "3", "30000");
        addEntityProviderStatic(list, "song", "migu", "referer", "https://music.migu.cn", "咪咕 Referer", "STRING", 73);
        addEntityProviderStatic(list, "song", "migu", "user_agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", "User-Agent", "STRING", 74);

        // ════════════════ enrich — Artist providers ════════════════
        addEntityProviderConfigs(list, "artist", "qqmusic", "QQ音乐", 80,
                new String[][]{
                        {"search_url", "https://c.y.qq.com/splcloud/fcgi-bin/smartbox_new.fcg"},
                        {"detail_url", "https://u.y.qq.com/cgi-bin/musicu.fcg"},
                },
                "120", "8", "15", "3", "3000");
        addEntityProviderStatic(list, "artist", "qqmusic", "cover_tpl",
                "https://y.gtimg.cn/music/photo_new/T001R300x300M000%s.jpg", "QQ音乐歌手封面模板", "STRING", 83);
        addEntityProviderStatic(list, "artist", "qqmusic", "referer", "https://y.qq.com", "QQ音乐 Referer", "STRING", 84);
        addEntityProviderStatic(list, "artist", "qqmusic", "user_agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", "User-Agent", "STRING", 85);

        addEntityProviderConfigs(list, "artist", "netease", "网易云音乐", 90,
                new String[][]{
                        {"search_url", "https://music.163.com/api/search/get"},
                        {"detail_url", "https://music.163.com/api/v1/artist"},
                },
                "240", "6", "15", "3", "30000");
        addEntityProviderStatic(list, "artist", "netease", "referer", "https://music.163.com", "网易云 Referer", "STRING", 93);
        addEntityProviderStatic(list, "artist", "netease", "user_agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", "User-Agent", "STRING", 94);

        addEntityProviderConfigs(list, "artist", "itunes", "iTunes", 100,
                new String[][]{
                        {"search_url", "https://itunes.apple.com/search"},
                        {"lookup_url", "https://itunes.apple.com/lookup"},
                },
                "2000", "6", "15", "1", "30000");

        addEntityProviderConfigs(list, "artist", "musicbrainz", "MusicBrainz", 110,
                new String[][]{
                        {"search_url", "https://musicbrainz.org/ws/2/artist/"},
                        {"detail_url", "https://musicbrainz.org/ws/2/artist/"},
                },
                "1500", "6", "20", "3", "60000");
        addEntityProviderStatic(list, "artist", "musicbrainz", "user_agent", "MusicPipeline/1.0", "User-Agent", "STRING", 113);

        addEntityProviderConfigs(list, "artist", "baidubaike", "百度百科", 120,
                new String[][]{
                        {"search_url", "https://www.baidu.com/s?wd="},
                        {"base_url", "https://baike.baidu.com/item/"},
                        {"warmup_url", "https://www.baidu.com/"},
                },
                "500", "3", "15", "3", "5000");
        addEntityProviderStatic(list, "artist", "baidubaike", "user_agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "User-Agent", "STRING", 124);

        addEntityProviderConfigs(list, "artist", "wikipedia", "Wikipedia", 130,
                new String[][]{
                        {"api_url", "https://%s.wikipedia.org/w/api.php"},
                },
                "2000", "6", "10", "3", "30000");
        addEntityProviderStatic(list, "artist", "wikipedia", "user_agent",
                "MusicMode/1.0 (music-management; bot)", "User-Agent", "STRING", 133);
        addEntityProviderStatic(list, "artist", "wikipedia", "max_intro_len", "500", "简介最大长度", "INT", 134);

        // ════════════════ enrich — Album providers ════════════════
        addEntityProviderConfigs(list, "album", "qqmusic", "QQ音乐", 140,
                new String[][]{
                        {"api_url", "https://u.y.qq.com/cgi-bin/musicu.fcg"},
                },
                "120", "8", "15", "3", "3000");
        addEntityProviderStatic(list, "album", "qqmusic", "cover_tpl",
                "https://y.gtimg.cn/music/photo_new/T002R300x300M000%s.jpg", "QQ音乐专辑封面模板", "STRING", 143);
        addEntityProviderStatic(list, "album", "qqmusic", "referer", "https://y.qq.com", "QQ音乐 Referer", "STRING", 144);
        addEntityProviderStatic(list, "album", "qqmusic", "user_agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", "User-Agent", "STRING", 145);

        addEntityProviderConfigs(list, "album", "netease", "网易云音乐", 150,
                new String[][]{
                        {"search_url", "https://music.163.com/api/search/get"},
                        {"detail_url", "https://music.163.com/api/v1/album"},
                },
                "240", "6", "15", "3", "30000");
        addEntityProviderStatic(list, "album", "netease", "referer", "https://music.163.com", "网易云 Referer", "STRING", 153);
        addEntityProviderStatic(list, "album", "netease", "user_agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", "User-Agent", "STRING", 154);

        addEntityProviderConfigs(list, "album", "itunes", "iTunes", 160,
                new String[][]{
                        {"search_url", "https://itunes.apple.com/search"},
                },
                "2000", "6", "15", "3", "30000");

        addEntityProviderConfigs(list, "album", "musicbrainz", "MusicBrainz", 170,
                new String[][]{
                        {"search_url", "https://musicbrainz.org/ws/2/release-group/"},
                },
                "1100", "6", "15", "3", "60000");
        addEntityProviderStatic(list, "album", "musicbrainz", "user_agent",
                "MusicMode/1.0 ( personal-music-manager )", "User-Agent", "STRING", 173);

        // ════════════════ enrich — 搜索/匹配 ════════════════
        add(list, "enrich.search.max_results",
                "12", "enrich", "最大搜索结果数", null, "INT", 80);
        add(list, "enrich.match.loose_threshold",
                "0.60", "enrich", "宽松匹配阈值", "Levenshtein 相似度", "DOUBLE", 81);
        add(list, "enrich.match.standard_threshold",
                "0.75", "enrich", "标准匹配阈值", "Levenshtein 相似度", "DOUBLE", 82);

        // ════════════════ pipeline — executor ════════════════
        add(list, "pipeline.executor.scheduler.max_pipelines",
                prop("pipeline.executor.scheduler.max-pipelines", "1"),
                "pipeline", "最大并发管道数", "信号量许可数", "INT", 101);
        add(list, "pipeline.executor.worker.core_size",
                prop("pipeline.executor.worker.core-size",
                        String.valueOf(Math.max(2, Runtime.getRuntime().availableProcessors() - 2))),
                "pipeline", "工作线程常驻数", null, "INT", 102);
        add(list, "pipeline.executor.worker.max_size",
                prop("pipeline.executor.worker.max-size",
                        String.valueOf(Runtime.getRuntime().availableProcessors())),
                "pipeline", "工作线程最大数", null, "INT", 103);
        add(list, "pipeline.executor.worker.queue_capacity",
                prop("pipeline.executor.worker.queue-capacity", "2000"),
                "pipeline", "工作队列容量", "仅启动时生效", "INT", 104);
        add(list, "pipeline.executor.virtual.max_concurrent",
                prop("pipeline.executor.virtual.max-concurrent", "0"),
                "pipeline", "虚拟线程最大并发", "0 = 不限流", "INT", 105);

        // ════════════════ pipeline — persistence ════════════════
        add(list, "pipeline.recovery.auto_resume",
                prop("pipeline.recovery.auto-resume", "false"),
                "pipeline", "启动时自动恢复管道", "开启后启动时按原状态恢复RUNNING/READY管道，关闭则全部转为暂停", "BOOLEAN", 110);
        add(list, "pipeline.recovery.enabled",
                prop("pipeline.recovery.enabled", "true"),
                "pipeline", "启动恢复未完成管道", null, "BOOLEAN", 111);
        add(list, "pipeline.task.retention_hours",
                prop("pipeline.task.retention-hours", "720"),
                "pipeline", "任务保留小时数", null, "INT", 112);
        add(list, "pipeline.persistence.item_log_flush_size",
                "200", "pipeline", "日志批量刷盘大小", "PipelinePersistence ITEM_LOG_FLUSH_SIZE", "INT", 113);

        // ════════════════ pipeline — store ════════════════
        add(list, "pipeline.store.max_active_size",
                "100", "pipeline", "Redis 活跃管道上限", "PipelineRecordStore MAX_ACTIVE_SIZE", "INT", 120);
        add(list, "pipeline.store.cache_ttl_minutes",
                "5", "pipeline", "缓存穿透 TTL(分钟)", "PipelineRecordStore EMPTY_TTL", "INT", 121);

        // ════════════════ switches ════════════════
        add(list, "switches.persist_enabled",
                prop("music.persist.enabled", "true"),
                "switches", "写入数据库", "全局 DB 持久化开关", "BOOLEAN", 130);
        add(list, "switches.write_tags_enabled",
                prop("music.write-tags.enabled", "true"),
                "switches", "写回文件标签", "全局写标签开关", "BOOLEAN", 131);

        // ════════════════ watch (v2 废弃) ════════════════
        // watch.* keys 已移除。扫描调度配置迁移到 watch_profile 表（每任务独立）。
        // 不再需要全局 watch.enabled / watch.incremental_interval_sec / watch.full_scan_interval_min。

        // ════════════════ fingerprint ════════════════
        add(list, "fingerprint.skip_if_exists",
                prop("music.fingerprint.skip-if-exists", "true"),
                "fingerprint", "跳过已有指纹", null, "BOOLEAN", 150);
        add(list, "fingerprint.timeout_seconds",
                prop("music.fingerprint.timeout-seconds", "120"),
                "fingerprint", "指纹计算超时(秒)", null, "INT", 151);

        // ════════════════ ffmpeg ════════════════
        add(list, "music.ffmpeg.path",
                prop("music.ffmpeg.path", "ffmpeg"),
                "ffmpeg", "FFmpeg 可执行文件路径", null, "STRING", 155);
        add(list, "music.ffprobe.path",
                prop("music.ffprobe.path", "ffprobe"),
                "ffmpeg", "ffprobe 可执行文件路径", null, "STRING", 156);

        // ════════════════ dedup ════════════════
        add(list, "dedup.default_strategies",
                "hash,filename,metadata", "dedup", "默认去重策略", "逗号分隔", "LIST", 160);
        add(list, "dedup.fingerprint.threshold",
                "0.80", "dedup", "指纹去重阈值", "FingerprintDedupStrategy", "DOUBLE", 161);
        add(list, "dedup.fingerprint.min_common",
                "3", "dedup", "指纹最少公共子串", null, "INT", 162);
        add(list, "dedup.fingerprint.batch_size",
                "500", "dedup", "指纹去重批大小", null, "INT", 163);
        add(list, "dedup.hash.batch_size",
                "500", "dedup", "哈希去重批大小", null, "INT", 164);
        add(list, "dedup.metadata.batch_size",
                "200", "dedup", "元数据去重批大小", null, "INT", 165);
        add(list, "dedup.metadata.persist_batch",
                "50", "dedup", "元数据去重持久批大小", null, "INT", 166);

        // ════════════════ module — 批量大小 ════════════════
        add(list, "delete.batch_size",
                "200", "module", "删除模块批大小", "DeleteModuleImpl", "INT", 170);
        add(list, "writer.batch_size",
                "200", "module", "写标签批大小", "GapFillingModule", "INT", 171);
        add(list, "writer.fork_batch",
                "50", "module", "写标签 Fork 批大小", null, "INT", 172);
        add(list, "writer.item_batch_size",
                "16", "module", "写标签 Item 批大小", null, "INT", 173);
        add(list, "scanner.streaming_threshold",
                "2000", "module", "流式扫描阈值", "ScannerModuleImpl", "INT", 174);

        // ════════════════ search ════════════════
        add(list, "music.search.health-threshold",
                "0.8", "search", "索引健康阈值",
                "索引文档数 / DB 记录数低于此值时触发自动全量重建（0=永不自动重建，1=必须完全一致）", "DOUBLE", 175);

        // ════════════════ artist ════════════════
        add(list, "music.artist.split-separators",
                "[\"\\\\\", \",\", \";\", \"&\", \"+\", \"|\", \"、\", \"，\", \"/\", \"_\", \"ft.\", \"feat.\", \"featuring\", \"presents\", \"pres.\", \"vs.\", \"versus\", \"x\", \" \", \"\\\\u0000\"]",
                "artist", "艺术家分隔符", "JSON 数组，单字符=字面分隔符，多字符=词边界关键词，\\\\u0000=null字节。空格做智能分割：CJK↔Latin边界切割，全英文整体保留", "JSON", 175);
        add(list, "music.artist.join-separator",
                " / ", "artist", "艺术家连接符", "多艺术家显示时的连接字符串", "STRING", 176);

        // ════════════════ organize ════════════════
        add(list, "organize.max_segment_length",
                "200", "module", "路径段最大长度", "OrganizePathBuilder", "INT", 180);
        add(list, "organize.max_conflict_retries",
                "1000", "module", "文件名冲突最大重试", null, "INT", 181);

        // ════════════════ auth ════════════════
        // JWT Secret：优先从 properties 读取，否则自动生成强随机 secret
        String jwtSecret = prop("music.auth.jwt.secret", null);
        if (jwtSecret == null || jwtSecret.isBlank()) {
            jwtSecret = UUID.randomUUID().toString() + "." + UUID.randomUUID().toString();
        }
        SystemConfig jwtSecretConfig = new SystemConfig();
        jwtSecretConfig.setConfigKey("music.auth.jwt.secret");
        jwtSecretConfig.setConfigValue(jwtSecret);
        jwtSecretConfig.setCategory("auth");
        jwtSecretConfig.setLabel("JWT 签名密钥");
        jwtSecretConfig.setDescription("HS256 签名密钥，首次启动自动生成");
        jwtSecretConfig.setValueType("STRING");
        jwtSecretConfig.setSensitive(true);
        jwtSecretConfig.setSortOrder(190);
        list.add(jwtSecretConfig);

        add(list, "music.auth.jwt.access-token-expiration-seconds",
                prop("music.auth.jwt.access-token-expiration-seconds", "1800"),
                "auth", "Access Token 过期时间(秒)", "默认 30 分钟", "INT", 191);
        add(list, "music.auth.jwt.refresh-token-expiration-seconds",
                prop("music.auth.jwt.refresh-token-expiration-seconds", "604800"),
                "auth", "Refresh Token 过期时间(秒)", "默认 7 天", "INT", 192);
        add(list, "music.auth.enabled",
                prop("music.auth.enabled", "true"),
                "auth", "启用认证", "关闭后所有 API 无需认证", "BOOLEAN", 193);
        add(list, "music.auth.allow-registration",
                prop("music.auth.allow-registration", "true"),
                "auth", "允许公开注册", "关闭后仅管理员可创建用户", "BOOLEAN", 194);
        add(list, "music.auth.white-list-paths",
                "/api/auth/login,/api/auth/register,/api/auth/refresh",
                "auth", "认证白名单路径", "逗号分隔的无需认证路径", "LIST", 195);

        String adminPwd = prop("music.auth.default-admin-password", "admin123");
        SystemConfig adminPwdConfig = new SystemConfig();
        adminPwdConfig.setConfigKey("music.auth.default-admin-password");
        adminPwdConfig.setConfigValue(adminPwd);
        adminPwdConfig.setCategory("auth");
        adminPwdConfig.setLabel("默认管理员密码");
        adminPwdConfig.setDescription("首次启动时自动创建，建议立即修改");
        adminPwdConfig.setValueType("STRING");
        adminPwdConfig.setSensitive(true);
        adminPwdConfig.setSortOrder(196);
        list.add(adminPwdConfig);

        add(list, "music.auth.default-admin-username",
                prop("music.auth.default-admin-username", "admin"),
                "auth", "默认管理员用户名", "首次启动时自动创建", "STRING", 197);
        add(list, "music.auth.login-max-failures",
                prop("music.auth.login-max-failures", "5"),
                "auth", "登录失败锁定阈值", "Phase C 启用", "INT", 198);

        return list;
    }

    // ── helpers ──

    private String prop(String key, String fallback) {
        return env.getProperty(key, fallback);
    }

    private void add(List<SystemConfig> list, String key, String value,
                     String category, String label, String desc,
                     String type, int sort) {
        SystemConfig c = new SystemConfig();
        c.setConfigKey(key);
        c.setConfigValue(value);
        c.setCategory(category);
        c.setLabel(label != null ? label : key);
        c.setDescription(desc);
        c.setValueType(type);
        c.setSortOrder(sort);
        list.add(c);
    }

    /** 为 entity×provider 添加限流 + URL 配置（从 properties 读取，fallback 到默认值） */
    private void addEntityProviderConfigs(List<SystemConfig> list, String entity, String code, String label,
                                           int sortStart, String[][] extraUrls,
                                           String rateMs, String concurrent, String timeout,
                                           String retries, String backoffMs) {
        String propPrefix = "music.enrich.providers." + code;
        String keyPrefix = "enrich." + entity + "." + code;
        int s = sortStart;
        add(list, keyPrefix + ".rate_limit_ms",
                env.getProperty(propPrefix + ".rate-limit-ms", rateMs),
                "enrich", label + "(" + entity + ") 请求间隔(ms)", null, "LONG", s++);
        add(list, keyPrefix + ".max_concurrent",
                env.getProperty(propPrefix + ".max-concurrent", concurrent),
                "enrich", label + "(" + entity + ") 最大并发", null, "INT", s++);
        add(list, keyPrefix + ".timeout_seconds",
                env.getProperty(propPrefix + ".timeout-seconds", timeout),
                "enrich", label + "(" + entity + ") 超时(秒)", null, "INT", s++);
        add(list, keyPrefix + ".rate_limit_retries",
                env.getProperty(propPrefix + ".rate-limit-retries", retries),
                "enrich", label + "(" + entity + ") 重试次数", null, "INT", s++);
        add(list, keyPrefix + ".rate_limit_backoff_ms",
                env.getProperty(propPrefix + ".rate-limit-backoff-ms", backoffMs),
                "enrich", label + "(" + entity + ") 退避间隔(ms)", null, "LONG", s++);

        for (String[] url : extraUrls) {
            String urlKey = url[0].replace("-", "_");
            add(list, keyPrefix + "." + urlKey,
                    env.getProperty(propPrefix + "." + url[0], url[1]),
                    "enrich", label + "(" + entity + ") " + url[0], null, "STRING", s++);
        }
    }

    /** 为 entity×provider 添加纯静态配置（无 properties fallback） */
    private void addEntityProviderStatic(List<SystemConfig> list, String entity, String code, String key,
                                          String value, String label, String type, int sort) {
        add(list, "enrich." + entity + "." + code + "." + key, value, "enrich", label, null, type, sort);
    }
}
