package com.gjl.music.infra.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;


public final class AudioFileUtils {

    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "mp3", "flac", "wav", "ogg", "wma", "m4a", "aac", "ape", "wv", "opus", "mp4");

    private AudioFileUtils() { throw new AssertionError("Utility class"); }


    public static boolean isAudioFile(Path path) {
        if (path == null || !Files.isRegularFile(path)) return false;
        return AUDIO_EXTENSIONS.contains(extension(path));
    }


    public static String extension(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1) : "";
    }


    public static boolean isAudioFile(java.io.File file) {
        return file != null && isAudioFile(file.toPath());
    }
}
