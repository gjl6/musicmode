package com.gjl.music.auth.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@NoArgsConstructor
public class AuthRole {

    private Long id;


    private String roleName;


    private String roleCode;


    private String description;

    private LocalDateTime createTime;
}
