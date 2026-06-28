package com.gjl.music.module.song.writer;

import com.gjl.music.exception.MetadataWriteException;
import com.gjl.music.infra.util.AudioFileUtils;
import com.gjl.music.model.MusicMetadata;
import com.gjl.music.module.song.enrich.module.EnrichPipeline;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 写入器工厂 —— 根据扩展名分派到对应的格式写入器。
 */
@Component
public class WriterFactoryImpl implements WriterFactory {

    private final EnrichPipeline enrichPipeline;
    private final String coversDir;

    public WriterFactoryImpl(EnrichPipeline enrichPipeline,
                             @Value("${music.covers-dir:../covers}") String coversDir) {
        this.enrichPipeline = enrichPipeline;
        this.coversDir = coversDir;
    }

    @Override
    public void write(File file, MusicMetadata metadata) throws MetadataWriteException {
        createWriter(file).write(file, metadata);
    }

    protected DefaultWriter createWriter(File file) {
        String ext = AudioFileUtils.extension(file.toPath());
        return switch (ext) {
            case "mp3"       -> new Mp3Writer(enrichPipeline, coversDir);
            case "flac"      -> new FlacWriter(enrichPipeline, coversDir);
            case "wav"       -> new WavWriter(enrichPipeline, coversDir);
            case "mp4","m4a" -> new Mp4Writer(enrichPipeline, coversDir);
            default          -> new DefaultWriter(enrichPipeline, coversDir);
        };
    }
}
