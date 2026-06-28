package com.gjl.music.module.song.fingerprint.internal.algorithm;

/** Chromaprint 指纹计算结果 */
public record ChromaprintResult(int durationSeconds, String fingerprint) {

    public boolean isValid() {
        return fingerprint != null && !fingerprint.isBlank();
    }
}
