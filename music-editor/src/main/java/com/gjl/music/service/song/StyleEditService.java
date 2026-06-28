package com.gjl.music.service.song;

import java.util.Map;

/**
 * 风格编辑服务接口??? */
public interface StyleEditService {

    /**
     * 更新风格元数据。DB 提交后异步同步所有关联歌曲的文件标签???     *
     * @param name 当前风格名称（用于查找）
     * @param body 请求体，可含 name(新名???, description, styleImage
     * @return 更新后的风格 Map
     */
    Map<String, Object> updateStyle(String name, Map<String, Object> body);
}
