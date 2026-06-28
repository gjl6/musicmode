package com.gjl.music.auth.mapper;

import com.gjl.music.auth.model.AuthMenu;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 菜单/路由 Mapper。
 */
@Mapper
public interface AuthMenuMapper {

    /** 按 sort_order 升序查询全部菜单 */
    List<AuthMenu> selectAllOrdered();
}
