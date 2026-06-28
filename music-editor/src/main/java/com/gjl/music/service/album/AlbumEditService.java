package com.gjl.music.service.album;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 专辑编辑服务接口??? */
public interface AlbumEditService {

    /**
     * 更新专辑元数据。DB 提交后异步同步所有关联歌曲的文件标签???     *
     * @param id   专辑 ID
     * @param body 请求体，可含 name, genre(albumType), year, introduction, company, language
     * @return 更新后的专辑 Map
     */
    Map<String, Object> updateAlbum(Long id, Map<String, Object> body);

    /**
     * 上传专辑封面，保存到 coversDir，返回显???URL???     */
    String uploadCover(Long albumId, MultipartFile file) throws IOException;
}
