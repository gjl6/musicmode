package com.gjl.music.persistence;

import com.gjl.music.model.MusicMetadata;

import java.util.List;
import java.util.Map;


public interface MetadataPersister {


    void persistBatch(List<Map.Entry<String, MusicMetadata>> batch);
}
