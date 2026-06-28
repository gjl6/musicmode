package com.gjl.music.playback.infra.subsonic;

import java.util.List;
import java.util.Map;

/**
 * Subsonic 用户管理桥接接口。
 *
 * <p>music-playback 中定义接口，music-auth 中提供实现。
 * 打破模块依赖方向（auth → playback），避免 playback 直接依赖 auth。
 */
public interface SubsonicUserBridge {

    /** 获取所有用户列表（Subsonic getUsers），仅管理员可用 */
    List<Map<String, Object>> getAllUsers();

    /** 修改指定用户的密码（Subsonic changePassword），仅管理员可用 */
    void changePassword(String username, String newPassword);
}
