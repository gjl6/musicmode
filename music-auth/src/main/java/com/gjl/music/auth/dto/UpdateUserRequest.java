package com.gjl.music.auth.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;

import java.util.List;


@Data
public class UpdateUserRequest {

    private String displayName;

    @Email(message = "邮箱格式不正确")
    private String email;


    private Integer status;


    private List<Long> roleIds;
}
