package com.gjl.music.config;

import com.gjl.music.mapper.SystemConfigMapper;
import com.gjl.music.model.SystemConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.*;


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

                add(list, "enrich.default_provider",
                prop("music.enrich.default-provider", "qqmusic,kugou,kuwo,netease,itunes,musicbrainz,migu"),
                "enrich", "默认搜索源", "逗号分隔的 provider 列表", "LIST", 1);
        add(list, "enrich.artist_provider",
                prop("music.enrich.artist-provider", "netease,qqmusic,itunes,musicbrainz,baidubaike,wikipedia"),
                "enrich", "艺术家详情源", "支持艺术家详情查询的 provider 列表", "LIST", 2);
        add(list, "enrich.default.rate_limit_ms",
                prop("music.enrich.providers.default.rate-limit-ms", "240"),
                "enrich", "默认请求间隔(ms)", null, "INT", 3);
        add(list, "enrich.default.max_concurrent",
                prop("music.enrich.providers.default.max-concurrent", "6"),
                "enrich", "默认最大并发", null, "INT", 4);
        add(list, "enrich.default.timeout_seconds",
                prop("music.enrich.providers.default.timeout-seconds", "15"),
                "enrich", "默认超时(秒)", null, "INT", 5);
        add(list, "enrich.default.rate_limit_retries",
                prop("music.enrich.providers.default.rate-limit-retries", "3"),
                "enrich", "默认重试次数", "频控退避重试次数", "INT", 6);
        add(list, "enrich.default.rate_limit_backoff_ms",
                prop("music.enrich.providers.default.rate-limit-backoff-ms", "30000"),
                "enrich", "默认退避间隔(ms)", "指数退避初始等待", "LONG", 6);

                addProviderConfigs(list, "qqmusic", "QQ音乐", 10,
                new String[][]{
                        {"search-url", "https://u.y.qq.com/cgi-bin/musicu.fcg"},
                        {"album-url", "https://i.y.qq.com/v8/fcg-bin/fcg_v8_album_info_cp.fcg"},
                        {"lyric-url", "https://i.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg"},
                },
                "120", "8", "15", "3", "3000");
        addProviderStatic(list, "qqmusic", "cover_tpl",
                "https://y.gtimg.cn/music/photo_new/T002R300x300M000%s.jpg", "QQ音乐封面模板", "STRING", 14);
        addProviderStatic(list, "qqmusic", "referer", "https://y.qq.com", "QQ音乐 Referer", "STRING", 15);
        addProviderStatic(list, "qqmusic", "rate_limit_code", "2001", "QQ音乐限流错误码", "INT", 16);

        addProviderConfigs(list, "kugou", "酷狗音乐", 20,
                new String[][]{},
                "240", "6", "15", "3", "30000");
        addProviderStatic(list, "kugou", "search_url", "http://mobilecdn.kugou.com/api/v3/search/song", "酷狗搜索URL", "STRING", 21);
        addProviderStatic(list, "kugou", "detail_url", "http://m.kugou.com/app/i/getSongInfo.php", "酷狗详情URL", "STRING", 22);
        addProviderStatic(list, "kugou", "lyric_search_url", "http://lyrics.kugou.com/search", "酷狗歌词搜索URL", "STRING", 23);
        addProviderStatic(list, "kugou", "lyric_download_url", "http://lyrics.kugou.com/download", "酷狗歌词下载URL", "STRING", 24);
        addProviderStatic(list, "kugou", "referer", "https://www.kugou.com", "酷狗 Referer", "STRING", 25);

        addProviderConfigs(list, "kuwo", "酷我音乐", 30,
                new String[][]{},
                "240", "6", "15", "3", "30000");
        addProviderStatic(list, "kuwo", "search_url", "http://search.kuwo.cn/r.s", "酷我搜索URL", "STRING", 31);
        addProviderStatic(list, "kuwo", "lyric_url", "https://m.kuwo.cn/newh5/singles/songinfoandlrc", "酷我歌词URL", "STRING", 32);
        addProviderStatic(list, "kuwo", "cover_cdn", "https://img4.kuwo.cn/star/albumcover/", "酷我封面CDN", "STRING", 33);
        addProviderStatic(list, "kuwo", "cover_cdn_mv", "https://img4.kuwo.cn/wmvpic/", "酷我MV封面CDN", "STRING", 34);
        addProviderStatic(list, "kuwo", "referer", "https://www.kuwo.cn", "酷我 Referer", "STRING", 35);
        addProviderStatic(list, "kuwo", "csrf", "music", "酷我 CSRF Token", "STRING", 36);

        addProviderConfigs(list, "netease", "网易云音乐", 40,
                new String[][]{},
                "240", "6", "15", "3", "30000");
        addProviderStatic(list, "netease", "search_url", "https://music.163.com/api/search/get", "网易云搜索URL", "STRING", 41);
        addProviderStatic(list, "netease", "detail_url", "https://music.163.com/api/song/detail", "网易云详情URL", "STRING", 42);
        addProviderStatic(list, "netease", "lyric_url", "https://music.163.com/api/song/lyric", "网易云歌词URL", "STRING", 43);
        addProviderStatic(list, "netease", "referer", "https://music.163.com", "网易云 Referer", "STRING", 44);

        addProviderConfigs(list, "itunes", "iTunes", 50,
                new String[][]{
                        {"search-url", "https://itunes.apple.com/search"},
                        {"lookup-url", "https://itunes.apple.com/lookup"},
                },
                "2000", "6", "15", "1", "30000");

        addProviderConfigs(list, "musicbrainz", "MusicBrainz", 60,
                new String[][]{
                        {"search-url", "https://musicbrainz.org/ws/2/recording"},
                        {"release-url", "https://musicbrainz.org/ws/2/release"},
                },
                "1500", "6", "20", "3", "60000");
        addProviderStatic(list, "musicbrainz", "user_agent", "MusicPipeline/1.0", "User-Agent", "STRING", 61);
        addProviderStatic(list, "musicbrainz", "cover_art_tpl",
                "https://coverartarchive.org/release/%s/front", "封面归档URL模板", "STRING", 62);

        addProviderConfigs(list, "migu", "咪咕音乐", 70,
                new String[][]{
                        {"search-url", "https://m.music.migu.cn/migu/remoting/scr_search_tag"},
                        {"song-url", "https://c.musicapp.migu.cn/MIGUM2/v2.0/music/song"},
                },
                "240", "6", "15", "3", "30000");
        addProviderStatic(list, "migu", "referer", "https://music.migu.cn", "咪咕 Referer", "STRING", 71);

                add(list, "enrich.search.max_results",
                "12", "enrich", "最大搜索结果数", null, "INT", 80);
        add(list, "enrich.match.loose_threshold",
                "0.60", "enrich", "宽松匹配阈值", "Levenshtein 相似度", "DOUBLE", 81);
        add(list, "enrich.match.standard_threshold",
                "0.75", "enrich", "标准匹配阈值", "Levenshtein 相似度", "DOUBLE", 82);

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

                add(list, "pipeline.recovery.enabled",
                prop("pipeline.recovery.enabled", "true"),
                "pipeline", "启动恢复未完成管道", null, "BOOLEAN", 110);
        add(list, "pipeline.task.retention_hours",
                prop("pipeline.task.retention-hours", "720"),
                "pipeline", "任务保留小时数", null, "INT", 111);
        add(list, "pipeline.persistence.item_log_flush_size",
                "200", "pipeline", "日志批量刷盘大小", "PipelinePersistence ITEM_LOG_FLUSH_SIZE", "INT", 112);

                add(list, "pipeline.store.max_active_size",
                "100", "pipeline", "Redis 活跃管道上限", "PipelineRecordStore MAX_ACTIVE_SIZE", "INT", 120);
        add(list, "pipeline.store.cache_ttl_minutes",
                "5", "pipeline", "缓存穿透 TTL(分钟)", "PipelineRecordStore EMPTY_TTL", "INT", 121);

                add(list, "switches.persist_enabled",
                prop("music.persist.enabled", "true"),
                "switches", "写入数据库", "全局 DB 持久化开关", "BOOLEAN", 130);
        add(list, "switches.write_tags_enabled",
                prop("music.write-tags.enabled", "true"),
                "switches", "写回文件标签", "全局写标签开关", "BOOLEAN", 131);

                add(list, "watch.enabled",
                prop("music.watch.enabled", "true"),
                "watch", "启用文件监控", null, "BOOLEAN", 140);
        add(list, "watch.incremental_interval_sec",
                prop("music.watch.incremental-interval-sec", "30"),
                "watch", "增量扫描间隔(秒)", null, "INT", 141);
        add(list, "watch.full_scan_interval_min",
                prop("music.watch.full-scan-interval-min", "120"),
                "watch", "全量对账间隔(分钟)", null, "INT", 142);

                add(list, "fingerprint.skip_if_exists",
                prop("music.fingerprint.skip-if-exists", "true"),
                "fingerprint", "跳过已有指纹", null, "BOOLEAN", 150);
        add(list, "fingerprint.timeout_seconds",
                prop("music.fingerprint.timeout-seconds", "120"),
                "fingerprint", "指纹计算超时(秒)", null, "INT", 151);

                add(list, "music.ffmpeg.path",
                prop("music.ffmpeg.path", "ffmpeg"),
                "ffmpeg", "FFmpeg 可执行文件路径", null, "STRING", 155);
        add(list, "music.ffprobe.path",
                prop("music.ffprobe.path", "ffprobe"),
                "ffmpeg", "ffprobe 可执行文件路径", null, "STRING", 156);

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

                add(list, "music.artist.split-separators",
                "[\"\\\\\", \",\", \";\", \"&\", \"+\", \"|\", \"、\", \"，\", \"/\", \"_\", \"ft.\", \"feat.\", \"featuring\", \"presents\", \"pres.\", \"vs.\", \"versus\", \"x\", \" \", \"\\\\u0000\"]",
                "artist", "艺术家分隔符", "JSON 数组，单字符=字面分隔符，多字符=词边界关键词，\\\\u0000=null字节。空格做智能分割：CJK↔Latin边界切割，全英文整体保留", "JSON", 175);
        add(list, "music.artist.join-separator",
                " / ", "artist", "艺术家连接符", "多艺术家显示时的连接字符串", "STRING", 176);

                add(list, "organize.max_segment_length",
                "200", "module", "路径段最大长度", "OrganizePathBuilder", "INT", 180);
        add(list, "organize.max_conflict_retries",
                "1000", "module", "文件名冲突最大重试", null, "INT", 181);

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


    private void addProviderConfigs(List<SystemConfig> list, String code, String label,
                                     int sortStart, String[][] extraUrls,
                                     String rateMs, String concurrent, String timeout,
                                     String retries, String backoffMs) {
        String prefix = "music.enrich.providers." + code;
        int s = sortStart;
        add(list, "enrich." + code + ".rate_limit_ms",
                env.getProperty(prefix + ".rate-limit-ms", rateMs),
                "enrich", label + " 请求间隔(ms)", null, "LONG", s++);
        add(list, "enrich." + code + ".max_concurrent",
                env.getProperty(prefix + ".max-concurrent", concurrent),
                "enrich", label + " 最大并发", null, "INT", s++);
        add(list, "enrich." + code + ".timeout_seconds",
                env.getProperty(prefix + ".timeout-seconds", timeout),
                "enrich", label + " 超时(秒)", null, "INT", s++);
        add(list, "enrich." + code + ".rate_limit_retries",
                env.getProperty(prefix + ".rate-limit-retries", retries),
                "enrich", label + " 重试次数", null, "INT", s++);
        add(list, "enrich." + code + ".rate_limit_backoff_ms",
                env.getProperty(prefix + ".rate-limit-backoff-ms", backoffMs),
                "enrich", label + " 退避间隔(ms)", null, "LONG", s++);

        for (String[] url : extraUrls) {
            String urlKey = url[0].replace("-", "_");
            add(list, "enrich." + code + "." + urlKey,
                    env.getProperty(prefix + "." + url[0], url[1]),
                    "enrich", label + " " + url[0], null, "STRING", s++);
        }
    }


    private void addProviderStatic(List<SystemConfig> list, String code, String key,
                                    String value, String label, String type, int sort) {
        add(list, "enrich." + code + "." + key, value, "enrich", label, null, type, sort);
    }
}
