package com.gjl.music.parser;

import com.gjl.music.config.ConfigService;
import com.gjl.music.exception.MetadataParseException;
import com.gjl.music.infra.util.AudioFileUtils;
import com.gjl.music.model.MusicMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;


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


    protected DefaultParser createParser(File file) {
        DefaultParser parser = switch (AudioFileUtils.extension(file.toPath())) {
            case "mp3"  -> new Mp3Parser();
            case "flac" -> new FlacParser();
            case "wav"  -> new WavParser();
            case "mp4"  -> new Mp4Parser();
            case "m4a"  -> new Mp4Parser();
            default     -> new DefaultParser();
        };
                parser.setCoversDir(coversDir);
        parser.setMusicRootDir(musicRootDir);
                String config = configService.getString(ARTIST_SPLIT_KEY, null);
        if (config != null) {
            parser.setArtistSplitConfig(config);
        }
        return parser;
    }

}
