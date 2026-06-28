package com.gjl.music.model;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Album extends MetadataItem {
    private String albumName;
    private AlbumType albumType;
    private String albumCover;
    private String introduction;
    private Integer albumYear;
    private String company;
    private String language;
    private Integer tagId;
    private String sortAlbumName;
    private Integer artistId;
    private Integer songCount;
    private String enrichSource;

    /** 艺术家名称（联查 artist 表填充，不持久化到 album 表） */
    private String artistName;
}
