package com.gjl.music.auth.service;

import com.gjl.music.auth.dto.MenuNode;
import com.gjl.music.auth.mapper.AuthMenuMapper;
import com.gjl.music.auth.model.AuthMenu;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 菜单/路由树构建服务。
 *
 * <p>从 DB 加载全部菜单 → 按用户权限过滤 → 递归构建嵌套树。
 * 权限过滤规则：
 * <ol>
 *   <li>ROLE_ADMIN → 返回全部菜单</li>
 *   <li>requires_admin=1 且用户非 ADMIN → 跳过</li>
 *   <li>permission_code 不为 null 且用户不拥有 → 跳过</li>
 *   <li>其余 → 通过</li>
 * </ol>
 */
@Slf4j
@Service
public class MenuService {

    private final AuthMenuMapper menuMapper;

    public MenuService(AuthMenuMapper menuMapper) {
        this.menuMapper = menuMapper;
    }

    /**
     * 根据用户权限构建菜单树。
     *
     * @param permissions 用户权限码列表
     * @param isAdmin     用户是否拥有 ROLE_ADMIN 角色
     * @return 菜单树（顶级节点列表，含嵌套 children）
     */
    public List<MenuNode> buildMenuTree(List<String> permissions, boolean isAdmin) {
        List<AuthMenu> allMenus = menuMapper.selectAllOrdered();
        if (allMenus == null || allMenus.isEmpty()) {
            return Collections.emptyList();
        }

        // 按权限过滤
        List<AuthMenu> accessible = allMenus.stream()
                .filter(m -> isAccessible(m, permissions, isAdmin))
                .toList();

        // 递归建树
        return buildChildren(accessible, null);
    }

    /** 判断当前菜单是否对用户可见 */
    private boolean isAccessible(AuthMenu menu, List<String> permissions, boolean isAdmin) {
        // Admin 全部可见
        if (isAdmin) return true;

        // requires_admin=1 且非 Admin → 不可见
        if (Boolean.TRUE.equals(menu.getRequiresAdmin())) return false;

        // permission_code 不为空 → 必须拥有该权限
        if (menu.getPermissionCode() != null && !menu.getPermissionCode().isBlank()) {
            return permissions.contains(menu.getPermissionCode());
        }

        // 无权限要求 → 所有认证用户可见
        return true;
    }

    /** 递归构建子节点 */
    private List<MenuNode> buildChildren(List<AuthMenu> all, Long parentId) {
        List<MenuNode> nodes = new ArrayList<>();
        for (AuthMenu m : all) {
            if (Objects.equals(m.getParentId(), parentId)) {
                MenuNode node = MenuNode.from(m);
                List<MenuNode> children = buildChildren(all, m.getId());
                if (!children.isEmpty()) {
                    node.setChildren(children);
                }
                nodes.add(node);
            }
        }
        // 按 sort_order 排序
        nodes.sort(Comparator.comparing(n -> {
            AuthMenu found = all.stream()
                    .filter(m -> m.getRouteName().equals(n.getName()))
                    .findFirst().orElse(null);
            return found != null && found.getSortOrder() != null ? found.getSortOrder() : 0;
        }));
        return nodes;
    }
}
