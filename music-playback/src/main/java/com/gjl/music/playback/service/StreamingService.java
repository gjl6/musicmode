package com.gjl.music.playback.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 音频流式传输服务。
 *
 * <p>支持 HTTP Range（可 seek 流）和直接流（不可 seek，如转码）。
 */
public interface StreamingService {

    /**
     * 流式传输原始音频文件（可 seek，完整支持 HTTP Range）。
     *
     * @param filePath 音频文件绝对路径
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    void streamFile(String filePath, HttpServletRequest request,
                    HttpServletResponse response) throws IOException;

    /**
     * 通过 songId 查找并流式传输。
     *
     * @param songId  歌曲数据库 ID
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    void streamSong(Long songId, HttpServletRequest request,
                    HttpServletResponse response) throws IOException;
}
