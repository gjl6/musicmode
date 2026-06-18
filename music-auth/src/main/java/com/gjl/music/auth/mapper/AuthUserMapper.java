package com.gjl.music.auth.mapper;

import com.gjl.music.auth.model.AuthUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


@Mapper
public interface AuthUserMapper {

    AuthUser selectById(@Param("id") Long id);

    AuthUser selectByUsername(@Param("username") String username);

    List<AuthUser> selectAll();

    boolean existsByUsername(@Param("username") String username);

    int insert(AuthUser user);

    int updateById(AuthUser user);

    int updatePassword(@Param("id") Long id, @Param("password") String password);

    int updateLoginTime(@Param("id") Long id);

    int deleteById(@Param("id") Long id);


    List<AuthUser> selectPage(@Param("keyword") String keyword,
                              @Param("offset") int offset,
                              @Param("limit") int limit,
                              @Param("orderBy") String orderBy,
                              @Param("orderDir") String orderDir);


    long count(@Param("keyword") String keyword);


    List<AuthUser> selectByRoleCode(@Param("roleCode") String roleCode);
}
