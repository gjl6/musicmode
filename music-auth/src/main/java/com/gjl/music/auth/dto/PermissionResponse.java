package com.gjl.music.auth.dto;

import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class PermissionResponse {

    private Long id;
    private String permissionName;
    private String permissionCode;
    private String description;

    private long roleCount;
}
