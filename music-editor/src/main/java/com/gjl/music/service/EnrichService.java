package com.gjl.music.service;

import com.gjl.music.model.*;
import com.gjl.music.module.song.enrich.module.EnrichPipeline;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class EnrichService {

    private final EnrichPipeline pipeline;

    public EnrichService(EnrichPipeline pipeline) {
        this.pipeline = pipeline;
    }


    public List<MusicMetadata> search(String providerName, MusicMetadata meta) {
        return pipeline.search(providerName, meta);
    }
}
