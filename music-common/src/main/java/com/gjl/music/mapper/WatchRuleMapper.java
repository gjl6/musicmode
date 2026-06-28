package com.gjl.music.mapper;

import com.gjl.music.model.WatchRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * WatchRule MyBatis Mapper。
 */
@Mapper
public interface WatchRuleMapper {

    List<WatchRule> selectAll();

    List<WatchRule> selectEnabled();

    /** @deprecated v2 废弃（不再依赖 auto_trigger 字段） */
    @Deprecated
    List<WatchRule> selectAutoTrigger();

    WatchRule selectById(@Param("id") Long id);

    int insert(WatchRule rule);

    int update(WatchRule rule);

    /** 仅更新运行状态（轻量写入，不覆盖扫描配置） */
    int updateState(@Param("id") Long id,
                    @Param("state") String state,
                    @Param("lastScanAt") LocalDateTime lastScanAt,
                    @Param("lastRunAt") LocalDateTime lastRunAt,
                    @Param("errorMessage") String errorMessage);

    int deleteById(@Param("id") Long id);
}
