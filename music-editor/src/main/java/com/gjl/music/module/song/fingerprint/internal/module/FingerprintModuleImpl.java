package com.gjl.music.module.song.fingerprint.internal.module;

import com.gjl.music.exception.ModuleException;
import com.gjl.music.editor.mapper.SongManageMapper;
import com.gjl.music.model.MusicMetadata;
import com.gjl.music.model.Song;
import com.gjl.music.module.song.fingerprint.internal.algorithm.ChromaprintGenerator;
import com.gjl.music.module.song.fingerprint.internal.algorithm.ChromaprintResult;
import com.gjl.music.module.song.fingerprint.internal.audio.AudioDecoder;
import com.gjl.music.module.song.fingerprint.internal.audio.AudioDecodeException;
import com.gjl.music.module.song.fingerprint.module.FingerprintModule;
import com.gjl.music.module.song.fingerprint.module.FingerprintPipeline;
import com.gjl.music.module.FailurePolicy;
import com.gjl.music.module.song.support.MetadataGapFillingModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.*;

@Slf4j
@Component
public class FingerprintModuleImpl extends MetadataGapFillingModule implements FingerprintModule, FingerprintPipeline {

    private final ChromaprintGenerator generator;
    private final List<AudioDecoder> decoders;
    private final boolean skipIfExists;

    @Override
    public ExecutorType executorType() { return ExecutorType.PLATFORM; }

    public FingerprintModuleImpl(SongManageMapper songManageMapper, ChromaprintGenerator generator,
                                  List<AudioDecoder> decoders,
                                  @Value("${music.fingerprint.skip-if-exists:true}") boolean skipIfExists) {
        super(songManageMapper);
        this.generator = generator;
        this.decoders = decoders;
        this.skipIfExists = skipIfExists;
    }

    @Override public String name() { return "fingerprint"; }
    @Override public FailurePolicy failurePolicy() { return FailurePolicy.SKIP; }
    @Override public String label() { return "声纹识别"; }

    @Override
    public void configure(Map<String, Object> options) {
        // 保留扩展点
    }

    // ── GapFillingModule 模板方法 ──

    @Override
    protected MusicMetadata processItem(MusicMetadata meta) {
        if (meta == null) {
            throw new ModuleException(name(), "input metadata is null");
        }
        return fingerprinterSingle(meta);
    }

    /** 指纹只存 DB，不写入音频标签 */
    @Override
    protected boolean shouldWriteTags() { return false; }

    // ── FingerprintPipeline ──

    @Override
    public ChromaprintResult fingerprint(Path audioFile) {
        AudioDecoder decoder = findDecoder(audioFile);
        if (decoder == null) {
            log.warn("FingerprintModule: 无可用解码器处理 {}", audioFile.getFileName());
            return new ChromaprintResult(0, "");
        }
        try {
            AudioDecoder.DecodedAudio audio = decoder.decode(audioFile);
            return generator.generate(audio.samples(), audio.sampleRate());
        } catch (AudioDecodeException e) {
            log.warn("解码失败: {} - {}", audioFile, e.getMessage());
            return new ChromaprintResult(0, "");
        }
    }

    @Override
    public boolean isAvailable() {
        return !decoders.isEmpty();
    }

    // ── 内部 ──

    private MusicMetadata fingerprinterSingle(MusicMetadata meta) {
        var songs = meta.getImmutableSongs();
        if (songs.isEmpty()) return meta;

        String filePath = songs.getFirst().getFilePath();
        if (filePath == null || filePath.isBlank()) {
            log.warn("FingerprintModule: 歌曲文件路径缺失，跳过指纹生成");
            throw new ModuleException(name(), "filePath is blank");
        }
        Path path = Path.of(filePath);

        if (skipIfExists) {
            String existingFp = songs.getFirst().getFingerprint();
            if (existingFp != null && !existingFp.isBlank()) {
                log.debug("跳过已有指纹: {}", path.getFileName());
                return meta;
            }
        }

        ChromaprintResult result = fingerprint(path);
        if (!result.isValid()) {
            log.warn("FingerprintModule: 无法为 {} 生成有效指纹", path.getFileName());
            throw new ModuleException(name(), "无法生成有效指纹: " + path.getFileName());
        }

        MusicMetadata updated = new MusicMetadata();
        meta.getImmutableAlbums().forEach(updated::addAlbum);
        meta.getImmutableArtists().forEach(updated::addArtist);
        meta.getImmutableLyrics().forEach(updated::addLyric);
        meta.getImmutableStyles().forEach(updated::addStyle);

        for (Song song : songs) {
            updated.addSong(song.toBuilder().fingerprint(result.fingerprint()).build());
        }

        return updated;
    }

    private AudioDecoder findDecoder(Path audioFile) {
        for (AudioDecoder decoder : decoders) {
            if (decoder.supports(audioFile)) return decoder;
        }
        return null;
    }
}
