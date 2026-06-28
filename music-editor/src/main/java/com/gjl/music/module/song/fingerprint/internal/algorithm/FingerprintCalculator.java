package com.gjl.music.module.song.fingerprint.internal.algorithm;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static com.gjl.music.module.song.fingerprint.internal.algorithm.ClassifierConfig.CLASSIFIERS;
import static com.gjl.music.module.song.fingerprint.internal.algorithm.ClassifierConfig.Classifier;

/**
 * 指纹计算 + 压缩编码。
 * 在色度图上滑动 16×12 窗口，每位置应用 16 个分类器产出一个 32-bit 子指纹，
 * 再对子指纹序列进行 XOR+差分+位打包压缩，最后 Base64 输出。
 */
public final class FingerprintCalculator {

    private static final int WINDOW_FRAMES = 16;
    private static final int NUM_BANDS = 12;
    private static final int NUM_CLASSIFIERS = 16;
    private static final int ALGORITHM_ID = 1; // TEST2 = Chromaprint v1.x

    // 压缩参数
    private static final int NORMAL_BITS = 3;
    private static final int NORMAL_MASK = (1 << NORMAL_BITS) - 1; // 7
    private static final int EXCEPTIONAL_BITS = 5;

    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();

    /**
     * 计算并压缩指纹。
     * @param chroma float[帧数][12] 已处理的色度图
     * @return int[] 子指纹数组（32-bit each）
     */
    public int[] calculateSubfingerprints(float[][] chroma) {
        int numFrames = chroma.length;
        int numWindows = numFrames - WINDOW_FRAMES + 1;
        if (numWindows <= 0) return new int[0];

        int[] subfingerprints = new int[numWindows];

        for (int w = 0; w < numWindows; w++) {
            subfingerprints[w] = calculateOne(chroma, w);
        }
        return subfingerprints;
    }

    /**
     * 完整流程：色度图 → 子指纹 → 压缩 → Base64 指纹字符串。
     * @param chroma 已处理的色度图
     * @return Base64 编码的压缩指纹，与 fpcalc 输出兼容
     */
    public String compress(float[][] chroma) {
        int[] subfingerprints = calculateSubfingerprints(chroma);
        return compressToBase64(subfingerprints);
    }

    /** 子指纹数组 → 压缩 → Base64 */
    public String compressToBase64(int[] subfingerprints) {
        byte[] compressed = compress(subfingerprints);
        return BASE64.encodeToString(compressed);
    }

    // ── 单个窗口的分类器计算 ──

    private static int calculateOne(float[][] chroma, int windowStart) {
        int value = 0;
        for (int c = 0; c < NUM_CLASSIFIERS; c++) {
            Classifier classifier = CLASSIFIERS[c];
            double output = applyFilter(chroma, windowStart, classifier.filter());
            int bits = quantize(output, classifier.t1(), classifier.t2(), classifier.t3());
            value = (value << 2) | bits;
        }
        return value;
    }

    /** 应用 Filter 到 16×12 窗口内的子区域，使用 SubtractLog 比较 */
    private static double applyFilter(float[][] chroma, int windowStart,
                                      ClassifierConfig.Filter filter) {
        int ftype = filter.type();
        int rowStart = filter.y();
        int numRows = filter.height();
        int numCols = filter.width();

        switch (ftype) {
            case 0: // 全区域能量 vs 0
                return subtractLog(sumRegion(chroma, windowStart, rowStart, numRows, 0, numCols), 0.0);

            case 1: { // 下半区 vs 上半区 (subtractLog)
                int halfH = numRows / 2;
                double bottom = sumRegion(chroma, windowStart, rowStart + halfH, numRows - halfH, 0, numCols);
                double top = sumRegion(chroma, windowStart, rowStart, halfH, 0, numCols);
                return subtractLog(bottom, top);
            }

            case 2: { // 右半区 vs 左半区 (subtractLog)
                int halfW = numCols / 2;
                double right = sumRegion(chroma, windowStart, rowStart, numRows, halfW, numCols - halfW);
                double left = sumRegion(chroma, windowStart, rowStart, numRows, 0, halfW);
                return subtractLog(right, left);
            }

            case 3: { // 四象限对角区域: (bottom-left + top-right) - (top-left + bottom-right)
                int w2 = numCols / 2;
                int h2 = numRows / 2;
                double offDiag = sumRegion(chroma, windowStart, rowStart + h2, numRows - h2, 0, w2)  // bottom-left
                               + sumRegion(chroma, windowStart, rowStart, h2, w2, numCols - w2);       // top-right
                double onDiag = sumRegion(chroma, windowStart, rowStart, h2, 0, w2)                     // top-left
                              + sumRegion(chroma, windowStart, rowStart + h2, numRows - h2, w2, numCols - w2); // bottom-right
                return subtractLog(offDiag, onDiag);
            }

            case 4: { // 三水平带：中间 vs 外围 (subtractLog)
                int h1 = numRows / 3;
                int hRemain = numRows - h1;
                double middle = sumRegion(chroma, windowStart, rowStart + h1, hRemain - h1, 0, numCols);
                double outer = sumRegion(chroma, windowStart, rowStart, h1, 0, numCols)
                             + sumRegion(chroma, windowStart, rowStart + hRemain, numRows - hRemain, 0, numCols);
                return subtractLog(middle, outer);
            }

            case 5: { // 三垂直带：中间 vs 外围 (subtractLog)
                int w1 = numCols / 3;
                int wRemain = numCols - w1;
                double middle = sumRegion(chroma, windowStart, rowStart, numRows, w1, wRemain - w1);
                double outer = sumRegion(chroma, windowStart, rowStart, numRows, 0, w1)
                             + sumRegion(chroma, windowStart, rowStart, numRows, wRemain, numCols - wRemain);
                return subtractLog(middle, outer);
            }

            default:
                return 0.0;
        }
    }

    // ── SubtractLog 对数比值比较 ──

    /** 官方 Chromaprint 的对数比值比较器，用于所有 filter 类型 */
    private static double subtractLog(double a, double b) {
        return Math.log((1.0 + a) / (1.0 + b));
    }

    // ── 区域求和 ──

    private static double sumRegion(float[][] chroma, int windowStart,
                                     int rowStart, int numRows, int colStart, int numCols) {
        double sum = 0.0;
        int colEnd = Math.min(colStart + numCols, WINDOW_FRAMES);
        int rowEnd = Math.min(rowStart + numRows, NUM_BANDS);
        int actualColStart = Math.max(colStart, 0);
        int actualRowStart = Math.max(rowStart, 0);
        for (int c = actualColStart; c < colEnd; c++) {
            for (int r = actualRowStart; r < rowEnd; r++) {
                sum += chroma[windowStart + c][r];
            }
        }
        return sum;
    }

    // ── Gray code 量化 ──

    private static int quantize(double value, double t1, double t2, double t3) {
        // Gray code: 0 → 1 → 3 → 2
        if (value < t1) return 0;
        if (value < t2) return 1;
        if (value < t3) return 3;
        return 2;
    }

    // ── 压缩编码 ──

    /**
     * 压缩子指纹数组为字节数组。
     * 格式: [1字节算法ID][3字节长度BE][normal bits packed][exceptional bits packed]
     */
    byte[] compress(int[] subfingerprints) {
        if (subfingerprints.length == 0) {
            return new byte[]{ALGORITHM_ID, 0, 0, 0};
        }

        List<Integer> normalBits = new ArrayList<>();
        List<Integer> exceptionalBits = new ArrayList<>();

        // 编码每帧：bit 从 1 开始，lastBit 每帧重置为 0，帧尾追加 0 分隔
        encodeChanges(subfingerprints[0], normalBits, exceptionalBits);

        for (int i = 1; i < subfingerprints.length; i++) {
            int xor = subfingerprints[i] ^ subfingerprints[i - 1];
            encodeChanges(xor, normalBits, exceptionalBits);
        }

        // 打包
        int normalBytes = (normalBits.size() * NORMAL_BITS + 7) / 8;
        int exceptionalBytes = (exceptionalBits.size() * EXCEPTIONAL_BITS + 7) / 8;

        byte[] result = new byte[4 + normalBytes + exceptionalBytes];

        // Header
        result[0] = (byte) ALGORITHM_ID;
        result[1] = (byte) ((subfingerprints.length >> 16) & 0xFF);
        result[2] = (byte) ((subfingerprints.length >> 8) & 0xFF);
        result[3] = (byte) (subfingerprints.length & 0xFF);

        // Pack normal bits
        int bytePos = 4;
        int bitPos = 0;
        int currentByte = 0;
        for (int val : normalBits) {
            for (int b = 0; b < NORMAL_BITS; b++) {
                if (((val >> b) & 1) != 0) {
                    currentByte |= (1 << bitPos);
                }
                bitPos++;
                if (bitPos == 8) {
                    result[bytePos++] = (byte) currentByte;
                    currentByte = 0;
                    bitPos = 0;
                }
            }
        }
        if (bitPos > 0) {
            result[bytePos++] = (byte) currentByte;
        }

        // Pack exceptional bits
        currentByte = 0;
        bitPos = 0;
        for (int val : exceptionalBits) {
            for (int b = 0; b < EXCEPTIONAL_BITS; b++) {
                if (((val >> b) & 1) != 0) {
                    currentByte |= (1 << bitPos);
                }
                bitPos++;
                if (bitPos == 8) {
                    result[bytePos++] = (byte) currentByte;
                    currentByte = 0;
                    bitPos = 0;
                }
            }
        }
        if (bitPos > 0) {
            result[bytePos] = (byte) currentByte;
        }

        return result;
    }

    /** 编码单帧 32-bit 值中置位的位置差。
     * 与官方 Chromaprint ProcessSubfingerprint 一致：
     * bit 从 1 开始计数，lastBit 从 0 开始，帧尾追加 0 分隔符。 */
    private static void encodeChanges(int value,
                                       List<Integer> normalBits,
                                       List<Integer> exceptionalBits) {
        int bit = 1;
        int lastBit = 0;
        int x = value;
        while (x != 0) {
            if ((x & 1) != 0) {
                int diff = bit - lastBit;
                if (diff >= NORMAL_MASK) {
                    normalBits.add(NORMAL_MASK);
                    exceptionalBits.add(diff - NORMAL_MASK);
                } else {
                    normalBits.add(diff);
                }
                lastBit = bit;
            }
            x >>>= 1;
            bit++;
        }
        normalBits.add(0);
    }
}
