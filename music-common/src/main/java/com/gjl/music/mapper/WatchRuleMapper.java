package com.gjl.music.mapper;

import com.gjl.music.model.WatchRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


@Mapper
public interface WatchRuleMapper {

    List<WatchRule> selectAll();

    List<WatchRule> selectEnabled();


    List<WatchRule> selectAutoTrigger();

    WatchRule selectById(@Param("id") Long id);

    int insert(WatchRule rule);

    int update(WatchRule rule);

    int deleteById(@Param("id") Long id);
}
