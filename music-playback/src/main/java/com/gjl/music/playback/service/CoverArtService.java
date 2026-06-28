package com.gjl.music.playback.service;

import java.nio.file.Path;

/**
 * 封面解析服务接口。
 */
public interface CoverArtService {

    Path resolveCoverPath(String id);
}
