package com.gjl.music.parser;

import com.gjl.music.config.ConfigService;
import com.gjl.music.exception.MetadataParseException;
import com.gjl.music.infra.util.AudioFileUtils;
import com.gjl.music.model.MusicMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 解析器工厂 —— 根据文件扩展名创建对应的解析器并执行解析。
 *
 * <pre>
 * ParserFactoryImpl (纯工厂)
 *   ├── .mp3  → new Mp3Parser().parse(file)
 *   ├── .flac → new FlacParser(coversDir).parse(file)
 *   ├── .wav  → new WavParser().parse(file)
 *   └── else  → new DefaultParser().parse(file)
 * </pre>
 * 格式专用解析器继承 {@link DefaultParser}，仅覆盖差异方法。
 */
@Component
public class ParserFactoryImpl implements ParserFactory {

    private static final String ARTIST_SPLIT_KEY = "music.artist.split-separators";

    private final ConfigService configService;

    @Value("${music.covers-dir:../covers}")
    private String coversDir;

    @Value("${music.root-dir:./music}")
    private String musicRootDir;

    public ParserFactoryImpl(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public MusicMetadata parse(File file) throws MetadataParseException {
        return createParser(file).parse(file);
    }

    @Override
    public MusicMetadata parseLight(File file) throws MetadataParseException {
        DefaultParser parser = createParser(file);
        parser.setLightMode(true);
        return parser.parse(file);
    }

    /** 根据扩展名创建解析器，注入通用配置 */
    protected DefaultParser createParser(File file) {
        DefaultParser parser = switch (AudioFileUtils.extension(file.toPath())) {
            case "mp3"  -> new Mp3Parser();
            case "flac" -> new FlacParser();
            case "wav"  -> new WavParser();
            case "mp4"  -> new Mp4Parser();
            case "m4a"  -> new Mp4Parser();
            default     -> new DefaultParser();
        };
        // 注入封面缓存目录和音乐根目录
        parser.setCoversDir(coversDir);
        parser.setMusicRootDir(musicRootDir);
        // 注入艺术家分割配置
        String config = configService.getString(ARTIST_SPLIT_KEY, null);
        if (config != null) {
            parser.setArtistSplitConfig(config);
        }
        return parser;
    }

}
