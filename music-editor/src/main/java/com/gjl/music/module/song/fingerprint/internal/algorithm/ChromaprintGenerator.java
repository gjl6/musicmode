package com.gjl.music.module.song.fingerprint.internal.algorithm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Chromaprint 指纹生成编排器。
 * PCM 样本 → 重采样到 11025Hz 单声道 → ChromaExtractor → ChromaProcessor → FingerprintCalculator → ChromaprintResult。
 */
@Slf4j
@Component
public class ChromaprintGenerator {

    private static final double TARGET_SAMPLE_RATE = 11025.0;

    private final ChromaExtractor chromaExtractor;
    private final ChromaProcessor chromaProcessor;
    private final FingerprintCalculator fingerprintCalculator;

    public ChromaprintGenerator() {
        this.chromaExtractor = new ChromaExtractor();
        this.chromaProcessor = new ChromaProcessor(1); // 官方管道不含 ChromaResampler
        this.fingerprintCalculator = new FingerprintCalculator();
    }

    /**
     * 从 PCM 样本生成 Chromaprint 指纹。
     * @param samples 原始 PCM 样本（-1.0 ~ 1.0）
     * @param sampleRate 原始采样率
     * @return ChromaprintResult，失败时 fingerprint 为空
     */
    public ChromaprintResult generate(float[] samples, int sampleRate) {
        if (samples == null || samples.length == 0) {
            return new ChromaprintResult(0, "");
        }

        try {
            // 1. 重采样到 11025 Hz 单声道
            float[] resampled = resample(samples, sampleRate, TARGET_SAMPLE_RATE);
            if (resampled.length < 4096) {
                log.debug("音频太短 ({} samples)，无法生成指纹", resampled.length);
                return new ChromaprintResult(resampled.length / 11025, "");
            }

            // 2. 色度提取
            float[][] chroma = chromaExtractor.extract(resampled);
            if (chroma.length < 16) {
                log.debug("色度帧数不足 ({}), 需要至少 16 帧", chroma.length);
                return new ChromaprintResult(resampled.length / 11025, "");
            }

            // 3. 后处理
            float[][] processed = chromaProcessor.process(chroma);
            if (processed.length < 16) {
                log.debug("处理后帧数不足 ({})", processed.length);
                return new ChromaprintResult(resampled.length / 11025, "");
            }

            // 4. 计算 + 压缩
            String fingerprint = fingerprintCalculator.compress(processed);

            int duration = resampled.length / 11025;
            return new ChromaprintResult(duration, fingerprint);

        } catch (Exception e) {
            log.warn("指纹生成失败: {}", e.getMessage());
            return new ChromaprintResult(0, "");
        }
    }

    /** 线性插值重采样。当输入已是目标采样率时直接返回原数组。 */
    public static float[] resample(float[] samples, int srcRate, double dstRate) {
        if (Math.abs(srcRate - dstRate) < 0.5) {
            return samples; // 无需重采样，避免不必要的拷贝
        }

        double ratio = srcRate / dstRate;
        int outLen = (int) (samples.length / ratio);
        if (outLen == 0) return new float[0];

        float[] result = new float[outLen];
        for (int i = 0; i < outLen; i++) {
            double srcIdx = i * ratio;
            int idx0 = (int) srcIdx;
            int idx1 = Math.min(idx0 + 1, samples.length - 1);
            double frac = srcIdx - idx0;
            result[i] = (float) (samples[idx0] * (1.0 - frac) + samples[idx1] * frac);
        }
        return result;
    }
}
