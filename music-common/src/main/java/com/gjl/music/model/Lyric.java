package com.gjl.music.model;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Lyric extends MetadataItem {
    private String content;
    private String lrcPath;
    private LyricType type;
    private String lyricHash;
    private Long songId;
}
