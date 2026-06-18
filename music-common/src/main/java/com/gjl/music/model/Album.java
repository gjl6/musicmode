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
}
