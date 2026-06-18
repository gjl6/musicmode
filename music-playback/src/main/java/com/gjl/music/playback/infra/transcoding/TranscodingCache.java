package com.gjl.music.playback.infra.transcoding;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.Supplier;


public interface TranscodingCache {


    Path getOrCompute(String cacheKey, Supplier<InputStream> transcoder);


    long getCurrentSize();


    int getFileCount();


    void evictToTarget(long targetBytes);
}
