package com.gjl.music.auth.mapper;

import com.gjl.music.auth.model.AuthAuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志 Mapper。
 */
@Mapper
public interface AuthAuditLogMapper {

    int insert(AuthAuditLog log);

    List<AuthAuditLog> selectByOperatorId(@Param("operatorId") Long operatorId,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    List<AuthAuditLog> selectByTimeRange(@Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    long count();
}
