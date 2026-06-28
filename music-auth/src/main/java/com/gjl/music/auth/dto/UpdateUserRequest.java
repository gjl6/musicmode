package com.gjl.music.auth.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;

import java.util.List;

/**
 * 更新用户请求。
 */
@Data
public class UpdateUserRequest {

    private String displayName;

    @Email(message = "邮箱格式不正确")
    private String email;

    /** 状态：1=正常，0=禁用 */
    private Integer status;

    /** 角色 ID 列表（如果提供则替换所有角色） */
    private List<Long> roleIds;
}
