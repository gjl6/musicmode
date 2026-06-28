package com.gjl.music.service.artist;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 艺术家编辑服务接口??? */
public interface ArtistEditService {

    /**
     * 更新艺术家元数据。DB 提交后异步同步所有关联歌曲的文件标签???     *
     * @param id   艺术???ID
     * @param body 请求体，可含 name, introduction, gender, country
     * @return 更新后的艺术???Map
     */
    Map<String, Object> updateArtist(Long id, Map<String, Object> body);

    /**
     * 上传艺术家封面，保存???coversDir，返回显???URL???     */
    String uploadCover(Long artistId, MultipartFile file) throws IOException;
}
