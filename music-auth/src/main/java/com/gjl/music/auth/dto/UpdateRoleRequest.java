package com.gjl.music.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;


@Data
public class UpdateRoleRequest {

    @Size(min = 2, max = 64, message = "角色名长度为2-64位")
    private String roleName;

    private String description;
}
