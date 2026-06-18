package com.gjl.music.model;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Style extends MetadataItem {
    private String styleName;
    private String sortStyleName;
    private String description;
    private String styleImage;
    private Integer songCount;
}
