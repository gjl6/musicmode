package com.gjl.music.module.song.dedup.strategy;

import com.gjl.music.editor.mapper.SongManageMapper;
import com.gjl.music.module.song.dedup.DuplicateGroup;
import com.gjl.music.infra.pipeline.NodeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.*;

/**
 * 基于 Chromaprint 声学指纹相似度的重复检测。
 * DB 优先缓存；缺失则 ctx.fork(FINGERPRINT_GEN) 按需生成并回写 DB。
 *
 * <p>两阶段方案：
 * <ol>
 *   <li>流式构建全局倒排索引 —— 每文件取 24 个采样点子指纹。</li>
 *   <li>筛选候选对 → 精确比对 → Union-Find 聚类。</li>
 * </ol>
 */
@Slf4j
@Component
public class FingerprintDedupStrategy implements DedupStrategy {

    private static final double DEFAULT_THRESHOLD = 0.80;
    private static final int MIN_COMMON = 3;
    private static final int SKETCH_SAMPLES = 24;
    private static final int HEAD_COUNT = 8;
    private static final int MID_COUNT = 8;
    private static final int TAIL_COUNT = 8;
    private static final int BATCH_SIZE = 500;

    private final SongManageMapper songManageMapper;

    public FingerprintDedupStrategy(SongManageMapper songManageMapper) {
        this.songManageMapper = songManageMapper;
    }

    @Override public String name() { return "fingerprint"; }
    @Override public String label() { return "声学指纹"; }
    @Override public boolean isSlow() { return true; }

    @Override
    public List<DuplicateGroup> detect(List<Path> filePaths, Map<String, Object> options, NodeContext ctx) {
        double threshold = DEFAULT_THRESHOLD;
        int minCommon = MIN_COMMON;
        if (options != null) {
            if (options.get("threshold") instanceof Number t) {
                threshold = Math.max(0.60, Math.min(0.95, t.doubleValue()));
            }
            if (options.get("minCommon") instanceof Number m) {
                minCommon = Math.max(2, m.intValue());
            }
        }

        if (filePaths.isEmpty()) {
            log.info("指纹去重: 无文件");
            return List.of();
        }

        String[] filePathsArr = new String[filePaths.size()];
        String[] fileNamesArr = new String[filePaths.size()];
        String[] fingerprints = new String[filePaths.size()];
        int n = 0;

        for (int offset = 0; offset < filePaths.size(); offset += BATCH_SIZE) {
            int end = Math.min(offset + BATCH_SIZE, filePaths.size());
            List<Path> batch = filePaths.subList(offset, end);
            List<String> batchPaths = batch.stream().map(Path::toString).toList();

            // 批量查 DB 缓存
            Map<String, String> cachedFps = new LinkedHashMap<>();
            for (Map<String, Object> row : songManageMapper.findFingerprintsByPaths(batchPaths)) {
                String p = (String) row.get("FILEPATH");
                String f = (String) row.get("FINGERPRINT");
                if (p != null && f != null) cachedFps.put(p, f);
            }

            // 收集缺指纹的文件，批量 invoke fingerprint 模块（GapFillingModule 内部并行处理）
            Map<String, Path> needsFp = new LinkedHashMap<>();
            for (Path p : batch) {
                String filePath = p.toString();
                String fp = cachedFps.get(filePath);
                if (fp == null || fp.isBlank()) {
                    needsFp.put(filePath, p);
                }
            }

            if (!needsFp.isEmpty()) {
                try {

                    ctx.invoke("fingerprint", needsFp);
                    log.debug("指纹去重: 批量生成 {} 个文件的指纹", needsFp.size());
                } catch (Exception e) {
                    log.warn("按需生成指纹失败: {} 个文件 - {}", needsFp.size(), e.getMessage());
                }

                // fingerprint 模块内部已持久化到 DB，回读刚生成的指纹
                List<String> needsFpPaths = new ArrayList<>(needsFp.keySet());
                for (Map<String, Object> row : songManageMapper.findFingerprintsByPaths(needsFpPaths)) {
                    String p = (String) row.get("FILEPATH");
                    String f = (String) row.get("FINGERPRINT");
                    if (p != null && f != null) cachedFps.put(p, f);
                }
            }

            // 填充指纹数组
            for (Path p : batch) {
                String filePath = p.toString();
                String fileName = p.getFileName().toString();
                String fp = cachedFps.get(filePath);
                if (fp != null && !fp.isBlank()) {
                    filePathsArr[n] = filePath;
                    fileNamesArr[n] = fileName;
                    fingerprints[n] = fp;
                    n++;
                }
            }
        }

        if (n < 2) {
            log.info("指纹去重: 有效指纹不足 ({})", n);
            return List.of();
        }
        log.info("指纹去重: {} 个有效指纹 (共 {} 个文件)", n, filePaths.size());

        // Phase 1: 构建全局 sketch 倒排索引 + pairCounts
        List<Map<Integer, IntList>> indexPositions = new ArrayList<>(SKETCH_SAMPLES);
        for (int i = 0; i < SKETCH_SAMPLES; i++) {
            indexPositions.add(new HashMap<>());
        }
        Map<Long, Integer> pairCounts = new HashMap<>();

        for (int fileId = 0; fileId < n; fileId++) {
            int[] decoded = ChromaprintCodec.decode(fingerprints[fileId]);
            if (decoded == null || decoded.length == 0) continue;

            int len = decoded.length;
            for (int pos = 0; pos < SKETCH_SAMPLES; pos++) {
                int actualPos = sketchPosition(pos, len);
                if (actualPos < 0 || actualPos >= len) continue;
                int value = decoded[actualPos];

                Map<Integer, IntList> posIndex = indexPositions.get(pos);
                IntList existing = posIndex.get(value);
                if (existing != null) {
                    for (int j = 0; j < existing.size(); j++) {
                        int otherId = existing.get(j);
                        long pairKey = pairKey(otherId, fileId);
                        pairCounts.merge(pairKey, 1, Integer::sum);
                    }
                }
                posIndex.computeIfAbsent(value, k -> new IntList()).add(fileId);
            }
        }
        log.info("指纹去重 Phase1: {} 个有效指纹, 候选对={}", n, pairCounts.size());

        // Phase 2: 筛选候选 + 精确比对
        Set<Integer> candidateIds = new HashSet<>();
        List<int[]> candidates = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : pairCounts.entrySet()) {
            if (entry.getValue() < minCommon) continue;
            long key = entry.getKey();
            int a = (int) (key >>> 32);
            int b = (int) key;
            candidateIds.add(a);
            candidateIds.add(b);
            candidates.add(new int[]{a, b});
        }

        if (candidates.isEmpty()) {
            log.info("指纹去重: 无候选对达到 minCommon={}", minCommon);
            return List.of();
        }

        // Union-Find
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        for (int[] pair : candidates) {
            int a = pair[0], b = pair[1];
            if (find(parent, a) == find(parent, b)) continue;

            double sim = ChromaprintCodec.compare(fingerprints[a], fingerprints[b]);
            if (sim >= threshold) {
                union(parent, a, b);
            }
        }

        Map<Integer, List<Integer>> groups = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            groups.computeIfAbsent(root, k -> new ArrayList<>()).add(i);
        }

        List<DuplicateGroup> results = new ArrayList<>();
        for (List<Integer> group : groups.values()) {
            if (group.size() > 1) {
                List<String> paths = group.stream().map(i -> filePathsArr[i]).toList();
                List<String> names = group.stream().map(i -> fileNamesArr[i]).toList();
                results.add(new DuplicateGroup(DuplicateGroup.DuplicateType.FINGERPRINT, paths, names));
            }
        }

        log.info("指纹去重完成: {} 个重复组", results.size());
        return results;
    }

    private static int sketchPosition(int idx, int fpLen) {
        if (idx < HEAD_COUNT) return idx;
        if (idx < HEAD_COUNT + MID_COUNT) {
            int mid = fpLen / 2;
            return mid - MID_COUNT / 2 + (idx - HEAD_COUNT);
        }
        return fpLen - TAIL_COUNT + (idx - HEAD_COUNT - MID_COUNT);
    }

    static long pairKey(int a, int b) {
        return a < b ? ((long) a << 32) | (b & 0xFFFF_FFFFL)
                     : ((long) b << 32) | (a & 0xFFFF_FFFFL);
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) parent[ra] = rb;
    }

    private static class IntList {
        int[] data = new int[4];
        int size;

        void add(int v) {
            if (size == data.length) data = Arrays.copyOf(data, size * 2);
            data[size++] = v;
        }

        int get(int i) { return data[i]; }
        int size() { return size; }
    }

    static final class ChromaprintCodec {

        private ChromaprintCodec() {}

        /**
         * 比较两个 Chromaprint 指纹的相似度（滑动窗口 + minLen 归一化）。
         *
         * <p>将较短指纹在较长指纹上滑动（窗口 ≤ 10 个位置 ≈ 3.7 秒），
         * 取最优对齐位置的匹配率。容忍同一首歌不同编码版本间的小幅时间偏移。
         * 分母用 minLen（短指纹长度），衡量"短音频被长音频包含的程度"。
         */
        static double compare(String fp1, String fp2) {
            int[] a = decode(fp1);
            int[] b = decode(fp2);
            if (a == null || b == null || a.length == 0 || b.length == 0) return 0.0;

            int[] shorter, longer;
            if (a.length <= b.length) { shorter = a; longer = b; }
            else                       { shorter = b; longer = a; }

            int minLen = shorter.length;
            int maxOffset = Math.min(10, longer.length - minLen);
            double best = 0.0;

            for (int offset = 0; offset <= maxOffset; offset++) {
                int matches = 0;
                for (int i = 0; i < minLen; i++) {
                    int sv = shorter[i], lv = longer[i + offset];
                    // 两方都静音(0) 或 共享至少一个频率位 → 匹配
                    if ((sv == 0 && lv == 0) || (sv & lv) != 0) matches++;
                }
                double sim = (double) matches / minLen;
                if (sim > best) best = sim;
                // 早停：已经接近完美匹配
                if (best >= 0.98) break;
            }
            return Math.min(1.0, best);
        }

        static int[] decode(String compressed) {
            if (compressed == null || compressed.isBlank()) return null;
            try {
                byte[] data = Base64.getUrlDecoder().decode(compressed);
                if (data.length < 4) return null;

                int length = ((data[1] & 0xFF) << 16) | ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
                if (length == 0) return new int[0];

                BitReader br = new BitReader(data, 4);
                int[] result = new int[length];
                int idx = 0;
                int value = 0;
                int lastBit = 0;

                while (idx < length) {
                    int diff = br.read(3);
                    if (diff == 0) {
                        result[idx++] = value;
                        value = 0;
                        lastBit = 0;
                        if (idx >= length) break;
                        continue;
                    }
                    if (diff == 7) diff += br.read(5);
                    lastBit += diff;
                    value |= (1 << (lastBit - 1));
                }
                return result;
            } catch (Exception e) {
                return null;
            }
        }

        private static class BitReader {
            private final byte[] data;
            private int bytePos;
            private int bitPos;

            BitReader(byte[] data, int startByte) {
                this.data = data;
                this.bytePos = startByte;
                this.bitPos = 0;
            }

            int read(int numBits) {
                int result = 0;
                for (int i = 0; i < numBits; i++) {
                    if (bytePos >= data.length) return 0;
                    if (((data[bytePos] >> bitPos) & 1) != 0) result |= (1 << i);
                    bitPos++;
                    if (bitPos == 8) { bitPos = 0; bytePos++; }
                }
                return result;
            }
        }
    }
}
