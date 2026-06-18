package com.gjl.music.playback.service;

import java.nio.file.Path;


public interface CoverArtService {

    Path resolveCoverPath(String id);
}
