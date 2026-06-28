package com.gjl.music.playback.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 转码编排服务接口。
 */
public interface TranscodeService {

    void stream(Long songId, String reqFormat, int maxBitRate, int timeOffset,
                HttpServletRequest request, HttpServletResponse response,
                String username) throws IOException;
}
