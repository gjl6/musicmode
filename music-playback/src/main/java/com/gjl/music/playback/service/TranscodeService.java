package com.gjl.music.playback.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;


public interface TranscodeService {

    void stream(Long songId, String reqFormat, int maxBitRate, int timeOffset,
                HttpServletRequest request, HttpServletResponse response,
                String username) throws IOException;
}
