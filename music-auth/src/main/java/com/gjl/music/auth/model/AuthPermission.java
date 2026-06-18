package com.gjl.music.auth.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@NoArgsConstructor
public class AuthPermission {

    private Long id;


    private String permissionName;


    private String permissionCode;


    private String description;

    private LocalDateTime createTime;
}
