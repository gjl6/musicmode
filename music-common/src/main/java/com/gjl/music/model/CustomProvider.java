package com.gjl.music.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;


@Getter
@Setter
public class CustomProvider {
    private Long id;
    private String name;
    private String label;
    private String icon;
    private String description;
    private boolean enabled;
    private String mode;
    private String configJson;
    private String sourceCode;
    private String metadataTags;
    private String version;
    private int sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
