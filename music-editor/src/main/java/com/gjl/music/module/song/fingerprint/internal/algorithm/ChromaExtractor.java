package com.gjl.music.module.song.fingerprint.internal.algorithm;

import org.jtransforms.fft.DoubleFFT_1D;

/**
 * FFT + Chroma 特征提取。
 * 将 11025 Hz 单声道 PCM 信号转换为 12 维色度特征序列。
 *
 * <p>算法：4096 点 FFT（2/3 重叠，Hamming 窗）→ 频域 bins 映射到 12 个音高类（A=0, A#=1, ..., G#=11）。
 */
public final class ChromaExtractor {

    private static final int FFT_SIZE = 4096;
    private static final int HOP_SIZE = FFT_SIZE / 3; // 1365, 匹配官方 chromaprint
    private static final double SAMPLE_RATE = 11025.0;
    private static final double BASE_FREQ = 27.5;     // A0
    private static final double MIN_FREQ = 28.0;
    private static final double MAX_FREQ = 3520.0;
    private static final int NUM_BANDS = 12;

    /** 预计算：FFT bin i → 音高类 (0-11)，无效为 -1 */
    private final int[] noteTable;
    /** 覆盖的 min bin 索引 */
    private final int minBin;
    /** 覆盖的 max bin 索引 (exclusive) */
    private final int maxBin;

    private final DoubleFFT_1D fft;
    private final double[] hammingWindow;

    public ChromaExtractor() {
        this.fft = new DoubleFFT_1D(FFT_SIZE);
        this.hammingWindow = buildHammingWindow();

        // 预计算音高类查找表
        int minB = -1, maxB = -1;
        this.noteTable = new int[FFT_SIZE / 2 + 1];
        for (int i = 0; i < noteTable.length; i++) {
            double freq = i * SAMPLE_RATE / FFT_SIZE;
            if (freq < MIN_FREQ || freq > MAX_FREQ) {
                noteTable[i] = -1;
            } else {
                double octave = Math.log(freq / BASE_FREQ) / Math.log(2.0);
                int note = (int) (NUM_BANDS * (octave - Math.floor(octave)));
                if (note < 0) note = 0;
                if (note >= NUM_BANDS) note = NUM_BANDS - 1;
                noteTable[i] = note;
                if (minB == -1) minB = i;
                maxB = i + 1;
            }
        }
        this.minBin = minB;
        this.maxBin = maxB;
    }

    /**
     * 从 11025 Hz 单声道 PCM 提取色度特征。
     * @param samples 11025 Hz 单声道 PCM 样本
     * @return float[帧数][12] 色度图
     */
    public float[][] extract(float[] samples) {
        int numFrames = (samples.length - FFT_SIZE) / HOP_SIZE + 1;
        if (numFrames <= 0) return new float[0][NUM_BANDS];

        float[][] chroma = new float[numFrames][NUM_BANDS];
        double[] frame = new double[FFT_SIZE];

        for (int f = 0; f < numFrames; f++) {
            int offset = f * HOP_SIZE;

            // 填充帧并应用 Hamming 窗
            for (int i = 0; i < FFT_SIZE; i++) {
                int idx = offset + i;
                frame[i] = (idx < samples.length ? samples[idx] : 0.0) * hammingWindow[i];
            }

            // FFT
            fft.realForward(frame);

            // 功率谱 → 色度
            float[] chromaRow = chroma[f];

            // DC 分量 (bin 0)
            if (noteTable[0] >= 0) {
                chromaRow[noteTable[0]] += (float) (frame[0] * frame[0]);
            }

            // Nyquist 分量 (bin N/2)，存储在 frame[1]
            if (noteTable[FFT_SIZE / 2] >= 0) {
                chromaRow[noteTable[FFT_SIZE / 2]] += (float) (frame[1] * frame[1]);
            }

            // bins 1 到 N/2-1
            for (int i = 1; i < FFT_SIZE / 2; i++) {
                int note = noteTable[i];
                if (note >= 0) {
                    double re = frame[2 * i];
                    double im = frame[2 * i + 1];
                    chromaRow[note] += (float) (re * re + im * im);
                }
            }
        }

        return chroma;
    }

    private static double[] buildHammingWindow() {
        double[] w = new double[FFT_SIZE];
        for (int i = 0; i < FFT_SIZE; i++) {
            w[i] = 0.53836 - 0.46164 * Math.cos(2.0 * Math.PI * i / (FFT_SIZE - 1));
        }
        return w;
    }
}
