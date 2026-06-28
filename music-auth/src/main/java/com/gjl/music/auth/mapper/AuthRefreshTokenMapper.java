package com.gjl.music.auth.mapper;

import com.gjl.music.auth.model.AuthRefreshToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Refresh Token Mapper。
 */
@Mapper
public interface AuthRefreshTokenMapper {

    AuthRefreshToken findByToken(@Param("token") String token);

    int insert(AuthRefreshToken token);

    int revokeByToken(@Param("token") String token);

    int revokeByUserId(@Param("userId") Long userId);

    int deleteExpired();
}
