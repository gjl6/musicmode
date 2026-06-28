package com.gjl.music.auth.mapper;

import com.gjl.music.auth.model.AuthUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户 Mapper。
 */
@Mapper
public interface AuthUserMapper {

    AuthUser selectById(@Param("id") Long id);

    AuthUser selectByUsername(@Param("username") String username);

    List<AuthUser> selectAll();

    boolean existsByUsername(@Param("username") String username);

    int insert(AuthUser user);

    int updateById(AuthUser user);

    int updatePassword(@Param("id") Long id, @Param("password") String password,
                       @Param("subsonicSecret") String subsonicSecret);

    int updateSubsonicSecret(@Param("id") Long id, @Param("subsonicSecret") String subsonicSecret);

    int updateAvatar(@Param("id") Long id, @Param("avatarPath") String avatarPath);

    int updateLoginTime(@Param("id") Long id);

    int deleteById(@Param("id") Long id);

    /** 管理端：按关键字搜索用户（分页） */
    List<AuthUser> selectPage(@Param("keyword") String keyword,
                              @Param("offset") int offset,
                              @Param("limit") int limit,
                              @Param("orderBy") String orderBy,
                              @Param("orderDir") String orderDir);

    /** 管理端：按关键字统计用户数 */
    long count(@Param("keyword") String keyword);

    /** 通过角色码查找用户 */
    List<AuthUser> selectByRoleCode(@Param("roleCode") String roleCode);
}
