package com.gjl.music.auth.dto;

import com.gjl.music.auth.model.AuthMenu;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单树节点 — 返回给前端的菜单 JSON。
 *
 * <p>前端用 name/path/component 构建 Vue Router 路由，
 * meta 携带 requiresAuth 等附加信息。
 */
@Data
public class MenuNode {

    private String name;
    private String path;
    private String component;
    private Map<String, Object> meta;
    private List<MenuNode> children;

    public static MenuNode from(AuthMenu m) {
        MenuNode node = new MenuNode();
        node.setName(m.getRouteName());
        node.setPath(m.getRoutePath());
        node.setComponent(m.getComponentKey());

        Map<String, Object> meta = new HashMap<>();
        meta.put("requiresAuth", true);
        node.setMeta(meta);
        node.setChildren(new ArrayList<>());
        return node;
    }
}
