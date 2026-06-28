package com.gjl.music.model;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/** 所有元数据实体的基类，提供 id、创建时间、更新时间 */
@Getter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@EqualsAndHashCode(exclude = {"id", "createTime", "updateTime"})
public abstract class MetadataItem {
    private String id = UUID.randomUUID().toString();
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
