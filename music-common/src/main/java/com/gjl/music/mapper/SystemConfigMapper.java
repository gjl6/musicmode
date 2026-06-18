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


    int insertIgnore(SystemConfig config);


    int batchInsertIgnore(@Param("list") List<SystemConfig> configs);


    int updateValue(@Param("configKey") String configKey,
                    @Param("configValue") String configValue);
}
