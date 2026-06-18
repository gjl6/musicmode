package com.gjl.music.infra.util;

import java.util.Locale;


public final class ContentTypeResolver {

    private ContentTypeResolver() {}


    public static String resolve(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".mp3"))  return "audio/mpeg";
        if (name.endsWith(".flac")) return "audio/flac";
        if (name.endsWith(".wav"))  return "audio/wav";
        if (name.endsWith(".mp4") || name.endsWith(".m4a") || name.endsWith(".aac")) return "audio/mp4";
        if (name.endsWith(".ogg") || name.endsWith(".opus")) return "audio/ogg";
        if (name.endsWith(".wma"))  return "audio/x-ms-wma";
        if (name.endsWith(".aiff") || name.endsWith(".aif")) return "audio/aiff";
        if (name.endsWith(".wv"))   return "audio/x-wavpack";
        if (name.endsWith(".ape"))  return "audio/x-ape";
        return "application/octet-stream";
    }
}
