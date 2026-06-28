package com.gjl.music.module.song.fingerprint.module;

import com.gjl.music.module.song.fingerprint.internal.algorithm.ChromaprintResult;

import java.nio.file.Path;

/** 声纹模块对外能力接口 */
public interface FingerprintPipeline {

    /** 为单个音频文件生成指纹。失败返回空结果 */
    ChromaprintResult fingerprint(Path audioFile);

    /** 检查音频解码是否可用 */
    boolean isAvailable();
}
