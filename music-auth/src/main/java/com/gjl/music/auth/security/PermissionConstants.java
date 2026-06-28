package com.gjl.music.auth.security;

import java.util.Set;

/**
 * 权限码常量 —— 所有权限码的唯一定义来源。
 * <p>
 * 使用方式：
 * <pre>{@code
 * @PreAuthorize("hasAuthority('" + PermissionConstants.MUSIC_BROWSE + "')")
 * }</pre>
 * 或通过 Thymeleaf/SPEL 引用：
 * <pre>{@code
 * @PreAuthorize("hasAuthority(T(com.gjl.music.auth.security.PermissionConstants).CONFIG_MANAGE)")
 * }</pre>
 */
public final class PermissionConstants {

    private PermissionConstants() {
        // 工具类，禁止实例化
    }

    // ═══════════════════════════════════════════════════════════════
    // Level 1 — 消费者 (Consumer)    ROLE_USER 默认拥有
    // ═══════════════════════════════════════════════════════════════

    /** 音乐浏览：查看文件列表、浏览目录、搜索 */
    public static final String MUSIC_READ = "music:read";

    /** 音乐播放：流媒体播放、转码、播放队列 */
    public static final String MUSIC_PLAY = "music:play";

    /** 播放列表管理：创建、编辑、删除自己的播放列表 */
    public static final String PLAYLIST_WRITE = "playlist:write";

    // ═══════════════════════════════════════════════════════════════
    // Level 2 — 编辑者 (Curator)     需管理员授予
    // ═══════════════════════════════════════════════════════════════

    /** 音乐编辑：修改元数据、保存标签、工具操作 */
    public static final String MUSIC_WRITE = "music:write";

    /** 音乐删除：删除歌曲文件 */
    public static final String MUSIC_DELETE = "music:delete";

    /** 音乐导入：导入外部曲库 */
    public static final String MUSIC_IMPORT = "music:import";

    /** 艺术家管理：编辑、合并、规范化、增强艺术家 */
    public static final String ARTIST_WRITE = "artist:write";

    /** 专辑管理：编辑、合并、规范化、增强专辑 */
    public static final String ALBUM_WRITE = "album:write";

    // ═══════════════════════════════════════════════════════════════
    // Level 3 — 管理者 (Admin)       ROLE_ADMIN 专属
    // ═══════════════════════════════════════════════════════════════

    /** 管道管理：提交、暂停、恢复、取消管道任务 */
    public static final String PIPELINE_WRITE = "pipeline:write";

    /** 系统配置：读写系统配置、CustomProvider、监控规则 */
    public static final String CONFIG_WRITE = "config:write";

    /** 用户管理：创建、编辑、删除用户，分配角色 */
    public static final String USER_WRITE = "user:write";

    // ── 旧常量（兼容过渡期，后续版本移除）──

    /** @deprecated 使用 {@link #MUSIC_READ} 替代 */
    @Deprecated public static final String MUSIC_BROWSE = "music:read";
    /** @deprecated 使用 {@link #MUSIC_WRITE} 替代 */
    @Deprecated public static final String MUSIC_EDIT = "music:write";
    /** @deprecated 使用 {@link #PIPELINE_WRITE} 替代 */
    @Deprecated public static final String PIPELINE_MANAGE = "pipeline:write";
    /** @deprecated 使用 {@link #CONFIG_WRITE} 替代 */
    @Deprecated public static final String CONFIG_MANAGE = "config:write";
    /** @deprecated 使用 {@link #USER_WRITE} 替代 */
    @Deprecated public static final String USER_MANAGE = "user:write";

    // ── 内置保护 ──

    /** 不可删除的内置角色码 */
    public static final Set<String> PROTECTED_ROLES = Set.of("ROLE_ADMIN", "ROLE_USER");

    /** 不可删除的内置权限码 */
    public static final Set<String> PROTECTED_PERMISSIONS = Set.of(
            MUSIC_READ,
            MUSIC_PLAY,
            PLAYLIST_WRITE,
            MUSIC_WRITE,
            MUSIC_DELETE,
            MUSIC_IMPORT,
            ARTIST_WRITE,
            ALBUM_WRITE,
            PIPELINE_WRITE,
            CONFIG_WRITE,
            USER_WRITE
    );

    /** 所有权限码列表（用于前端初始化） */
    public static final Set<String> ALL_PERMISSIONS = PROTECTED_PERMISSIONS;
}
