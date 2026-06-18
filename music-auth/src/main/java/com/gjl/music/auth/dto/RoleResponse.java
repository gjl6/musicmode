package com.gjl.music.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;


@Data
@Builder
public class RoleResponse {

    private Long id;
    private String roleName;
    private String roleCode;
    private String description;
    private LocalDateTime createTime;

    private List<String> permissions;

    private int permissionCount;

    private long userCount;
}
