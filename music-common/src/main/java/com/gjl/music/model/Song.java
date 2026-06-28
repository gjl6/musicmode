package com.gjl.music.model;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Song extends MetadataItem {
    private String title;
    private String filePath;
    private String fileName;
    private String fileFormat;
    private long fileSize;
    private String fileHash;
    private String fingerprint;
    private Integer duration;
    private String coverPath;
    private String year;
    private Integer bitrate;
    private Integer sampleRate;
    private Integer channels;
    private Integer bitsPerSample;
    private String language;
    private Integer artistId;
    private Integer albumId;
    private Integer tagId;
    private Integer lyricId;
    private Integer trackNumber;
    private Integer discNumber;
    private Double trackGain;
    private Double trackPeak;
    private String composer;
    private String lyricist;
    private String arranger;
    private String producer;
    private String extraTags;
    /** 艺术家名 — 非 DB 字段，由 JOIN 查询填充（Subsonic API 用） */
    private String artistName;
    /** 专辑名 — 非 DB 字段，由 JOIN 查询填充（Subsonic API 用） */
    private String albumName;
    /** 文件最后修改时间戳（毫秒），用于增量扫描修改检测 */
    private Long fileMtime;
    /** 排序键 — 中文转拼音大写，用于字母索引 + 排序 */
    private String sortTitle;
}
