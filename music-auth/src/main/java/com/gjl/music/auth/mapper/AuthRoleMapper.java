package com.gjl.music.auth.mapper;

import com.gjl.music.auth.model.AuthRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


@Mapper
public interface AuthRoleMapper {

    AuthRole selectById(@Param("id") Long id);

    AuthRole selectByCode(@Param("roleCode") String roleCode);

    List<AuthRole> selectByUserId(@Param("userId") Long userId);

    List<AuthRole> selectAll();

    int insert(AuthRole role);

    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    int deleteUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    int deleteUserRoles(@Param("userId") Long userId);

    int updateById(AuthRole role);

    int deleteById(@Param("id") Long id);
}
