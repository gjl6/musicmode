package com.gjl.music.mapper;

import com.gjl.music.model.CustomProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CustomProviderMapper {

    List<CustomProvider> selectAll();

    List<CustomProvider> selectEnabled();

    CustomProvider selectById(@Param("id") Long id);

    CustomProvider selectByName(@Param("name") String name);

    int insert(CustomProvider provider);

    int update(CustomProvider provider);

    int updateConfig(@Param("id") Long id,
                     @Param("configJson") String configJson,
                     @Param("sourceCode") String sourceCode);

    int deleteById(@Param("id") Long id);
}
