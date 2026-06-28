package com.gjl.music.auth.mapper;

import com.gjl.music.auth.model.AuthPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 权限 Mapper。
 */
@Mapper
public interface AuthPermissionMapper {

    AuthPermission selectById(@Param("id") Long id);

    AuthPermission selectByCode(@Param("permissionCode") String permissionCode);

    List<AuthPermission> selectByRoleId(@Param("roleId") Long roleId);

    List<AuthPermission> selectByUserId(@Param("userId") Long userId);

    List<AuthPermission> selectAll();

    int insert(AuthPermission permission);

    int insertRolePermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    int updateById(AuthPermission permission);

    int deleteById(@Param("id") Long id);

    int deleteRolePermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    int deleteRolePermissions(@Param("roleId") Long roleId);
}
