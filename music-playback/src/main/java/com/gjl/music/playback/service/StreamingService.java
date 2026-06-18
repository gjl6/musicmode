package com.gjl.music.playback.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;


public interface StreamingService {


    void streamFile(String filePath, HttpServletRequest request,
                    HttpServletResponse response) throws IOException;


    void streamSong(Long songId, HttpServletRequest request,
                    HttpServletResponse response) throws IOException;
}
