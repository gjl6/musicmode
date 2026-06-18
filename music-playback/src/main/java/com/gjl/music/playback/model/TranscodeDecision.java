package com.gjl.music.playback.model;


public record TranscodeDecision(
        boolean canDirectPlay,
        String targetFormat,
        int targetBitrate,
        int targetSampleRate,
        int targetChannels,
        String targetMimeType,
        String commandName
) {

    public static TranscodeDecision directPlay(String sourceFormat, String mimeType) {
        return new TranscodeDecision(true, sourceFormat, 0, 0, 0, mimeType, null);
    }


    public static TranscodeDecision transcode(String targetFormat, int targetBitrate,
                                              int targetSampleRate, int targetChannels,
                                              String targetMimeType, String commandName) {
        return new TranscodeDecision(false, targetFormat, targetBitrate,
                targetSampleRate, targetChannels, targetMimeType, commandName);
    }
}
