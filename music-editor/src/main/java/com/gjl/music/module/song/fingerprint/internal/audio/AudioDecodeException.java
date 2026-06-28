package com.gjl.music.module.song.fingerprint.internal.audio;

/** 音频解码异常 */
public class AudioDecodeException extends Exception {

    public AudioDecodeException(String message) {
        super(message);
    }

    public AudioDecodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
