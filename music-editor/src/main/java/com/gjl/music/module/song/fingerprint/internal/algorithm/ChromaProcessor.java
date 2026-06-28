package com.gjl.music.module.song.fingerprint.internal.algorithm;

import java.util.Arrays;

/**
 * 色度图后处理：频域滤波 → L2归一化 → 时域重采样。
 */
public final class ChromaProcessor {

    private static final int NUM_BANDS = 12;
    private static final double[] FILTER_COEFFS = {0.25, 0.75, 1.0, 0.75, 0.25};
    private static final int FILTER_RADIUS = 2; // (5 - 1) / 2

    // 默认每 2 帧合并为 1 帧
    private final int resampleFactor;

    public ChromaProcessor() {
        this(2);
    }

    public ChromaProcessor(int resampleFactor) {
        this.resampleFactor = resampleFactor;
    }

    /**
     * 处理色度图：滤波 → 归一化 → 重采样。
     * @param chroma float[帧数][12]
     * @return float[处理后帧数][12]
     */
    public float[][] process(float[][] chroma) {
        if (chroma.length == 0) return chroma;

        float[][] filtered = filter(chroma);
        normalize(filtered);
        return resample(filtered);
    }

    /** 5-tap 时域平滑（跨帧，每 band 独立，与官方 Chromaprint 一致） */
    private static float[][] filter(float[][] chroma) {
        int numFrames = chroma.length;
        float[][] result = new float[numFrames][NUM_BANDS];

        for (int f = 0; f < numFrames; f++) {
            for (int b = 0; b < NUM_BANDS; b++) {
                double sum = 0.0;
                double weightSum = 0.0;
                for (int k = -FILTER_RADIUS; k <= FILTER_RADIUS; k++) {
                    int frameIdx = f + k;
                    if (frameIdx >= 0 && frameIdx < numFrames) {
                        double w = FILTER_COEFFS[k + FILTER_RADIUS];
                        sum += chroma[frameIdx][b] * w;
                        weightSum += w;
                    }
                }
                result[f][b] = (float) (sum / weightSum);
            }
        }
        return result;
    }

    /** 逐帧 L2 欧几里得归一化（与官方 Chromaprint 一致） */
    private static void normalize(float[][] chroma) {
        for (float[] frame : chroma) {
            double sumSq = 0.0;
            for (float v : frame) {
                sumSq += (double) v * v;
            }
            double norm = Math.sqrt(sumSq);
            if (norm < 0.01) {
                Arrays.fill(frame, 0.0f);
            } else {
                for (int b = 0; b < NUM_BANDS; b++) {
                    frame[b] = (float) (frame[b] / norm);
                }
            }
        }
    }

    /** 时域重采样：每 N 帧合并为 1 帧 */
    private float[][] resample(float[][] chroma) {
        if (resampleFactor <= 1) return chroma;
        int outFrames = chroma.length / resampleFactor;
        if (outFrames == 0) return chroma;

        float[][] result = new float[outFrames][NUM_BANDS];
        for (int f = 0; f < outFrames; f++) {
            int start = f * resampleFactor;
            for (int b = 0; b < NUM_BANDS; b++) {
                double sum = 0.0;
                for (int k = 0; k < resampleFactor; k++) {
                    sum += chroma[start + k][b];
                }
                result[f][b] = (float) (sum / resampleFactor);
            }
        }
        return result;
    }
}
