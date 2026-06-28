package com.gjl.music.mapper;

import com.gjl.music.model.SystemConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SystemConfigMapper {

    List<SystemConfig> selectAll();

    List<SystemConfig> selectByCategory(@Param("category") String category);

    SystemConfig selectByKey(@Param("configKey") String configKey);

    List<SystemConfig> search(@Param("keyword") String keyword);

    List<String> selectAllCategories();

    int insert(SystemConfig config);

    int updateByKey(SystemConfig config);

    /** 插入或忽略（键冲突时跳过），用于初始化 */
    int insertIgnore(SystemConfig config);

    /** 批量插入或忽略 */
    int batchInsertIgnore(@Param("list") List<SystemConfig> configs);

    /** 更新值（仅更新 config_value 和 updated_at） */
    int updateValue(@Param("configKey") String configKey,
                    @Param("configValue") String configValue);
}
