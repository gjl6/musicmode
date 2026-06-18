package com.gjl.music.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;


@Data
public class CreateRoleRequest {

    @NotBlank(message = "角色名不能为空")
    @Size(min = 2, max = 64, message = "角色名长度为2-64位")
    private String roleName;

    @NotBlank(message = "角色码不能为空")
    @Pattern(regexp = "^ROLE_[A-Z_]+$", message = "角色码格式为 ROLE_XXX")
    @Size(min = 4, max = 64, message = "角色码长度为4-64位")
    private String roleCode;

    private String description;
}
