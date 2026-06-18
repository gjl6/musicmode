package com.gjl.music.model;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Artist extends MetadataItem {
    private String artistName;
    private String artistCover;
    private String introduction;
    private Integer gender;
    private String country;
    private String sortArtistName;
    private String enrichSource;
    private Integer albumCount;
    private Integer songCount;
}
