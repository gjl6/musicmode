package com.gjl.music.mapper;

import com.gjl.music.model.Style;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface StyleMapper {

    void upsertStyle(Style style);

    Long selectStyleIdByName(String styleName);

    Style findStyleById(@Param("id") Long id);

    /** 按名称查询风格元数据（封面、描述） */
    Map<String, Object> selectStyleInfoByName(@Param("styleName") String styleName);

    void batchUpsertStyles(@Param("list") List<Style> list);

    int updateStyle(@Param("id") Long id,
                    @Param("styleName") String styleName,
                    @Param("description") String description,
                    @Param("styleImage") String styleImage);

    List<Map<String, Object>> selectStyleIdsByNames(@Param("names") List<String> names);

    /** 根据 ID 列表批量查询风格 */
    List<Style> findStylesByIds(@Param("ids") List<Long> ids);
}
