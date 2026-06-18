package com.gjl.music.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;


@Data
public class AssignPermissionsRequest {

    @NotNull(message = "权限ID列表不能为null")
    private List<Long> permissionIds;
}
