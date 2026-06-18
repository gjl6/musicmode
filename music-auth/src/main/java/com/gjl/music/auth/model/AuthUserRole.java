package com.gjl.music.auth.model;

import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class AuthUserRole {

    private Long userId;
    private Long roleId;
}
