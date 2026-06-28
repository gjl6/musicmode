package com.gjl.music.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求。
 */
@Data
public class ChangePasswordRequest {

    /** 管理员重置时可不提供，用户自行修改时需要 */
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 128, message = "密码长度为6-128位")
    private String newPassword;
}
