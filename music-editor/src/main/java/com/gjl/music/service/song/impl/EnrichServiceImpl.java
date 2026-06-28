package com.gjl.music.service.song.impl;

import com.gjl.music.service.song.EnrichService;
import com.gjl.music.model.*;
import com.gjl.music.module.song.enrich.module.EnrichPipeline;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EnrichServiceImpl implements EnrichService {

    private final EnrichPipeline pipeline;

    public EnrichServiceImpl(EnrichPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public List<MusicMetadata> search(String providerName, MusicMetadata meta) {
        return pipeline.search(providerName, meta);
    }
}
