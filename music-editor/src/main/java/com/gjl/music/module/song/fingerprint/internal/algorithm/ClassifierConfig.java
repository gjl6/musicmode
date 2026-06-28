package com.gjl.music.module.song.fingerprint.internal.algorithm;

/**
 * Chromaprint TEST2 算法的 16 个分类器硬编码参数。
 * 每个分类器 = 一个 Filter（6 种类型之一）+ 3 个量化阈值。
 * 阈值将 Filter 输出量化为 2-bit Gray code（0-3）。
 */
final class ClassifierConfig {

    private ClassifierConfig() {}

    /** Filter 类型 */
    record Filter(int type, int y, int height, int width) {}

    /** 分类器 = Filter + 3 个阈值 */
    record Classifier(Filter filter, double t1, double t2, double t3) {}

    static final Classifier[] CLASSIFIERS = {
        // 0: Filter0 — 全区域能量
        new Classifier(new Filter(0, 4, 3, 15),  1.98215,   2.35817,   2.63523),
        // 1: Filter4 — 三水平带 (中间 6/12 vs 外围)
        new Classifier(new Filter(4, 4, 6, 15), -1.03809,  -0.651211, -0.282167),
        // 2: Filter1 — 上半区 vs 下半区
        new Classifier(new Filter(1, 0, 4, 16), -0.298702,  0.119262,  0.558497),
        // 3: Filter3 — 对角区域
        new Classifier(new Filter(3, 8, 2, 12), -0.105439,  0.0153946, 0.135898),
        // 4: Filter3 — 对角区域
        new Classifier(new Filter(3, 4, 4, 8),  -0.142891,  0.0258736, 0.200632),
        // 5: Filter4 — 三水平带
        new Classifier(new Filter(4, 0, 3, 5),  -0.826319, -0.590612, -0.368214),
        // 6: Filter1 — 上半区 vs 下半区
        new Classifier(new Filter(1, 2, 2, 9),  -0.557409, -0.233035,  0.0534525),
        // 7: Filter2 — 右半区 vs 左半区
        new Classifier(new Filter(2, 7, 3, 4),  -0.0646826, 0.00620476, 0.0784847),
        // 8: Filter2 — 右半区 vs 左半区
        new Classifier(new Filter(2, 6, 2, 16), -0.192387, -0.029699,   0.215855),
        // 9: Filter2 — 右半区 vs 左半区
        new Classifier(new Filter(2, 1, 3, 2),  -0.0397818,-0.00568076, 0.0292026),
        // 10: Filter5 — 三垂直带
        new Classifier(new Filter(5, 10, 1, 15), -0.53823,  -0.369934,  -0.190235),
        // 11: Filter3 — 对角区域
        new Classifier(new Filter(3, 6, 2, 10), -0.124877,  0.0296483,  0.139239),
        // 12: Filter2 — 右半区 vs 左半区
        new Classifier(new Filter(2, 1, 1, 14), -0.101475,  0.0225617,  0.231971),
        // 13: Filter3 — 对角区域
        new Classifier(new Filter(3, 5, 6, 4),  -0.0799915,-0.00729616, 0.063262),
        // 14: Filter1 — 上半区 vs 下半区
        new Classifier(new Filter(1, 9, 2, 12), -0.272556,  0.019424,   0.302559),
        // 15: Filter3 — 对角区域
        new Classifier(new Filter(3, 4, 2, 14), -0.164292, -0.0321188,  0.0846339),
    };
}
