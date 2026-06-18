package com.gjl.music.auth.security;

import java.util.Set;


public final class PermissionConstants {

    private PermissionConstants() {
            }


    public static final String USER_MANAGE = "user:manage";


    public static final String MUSIC_BROWSE = "music:browse";


    public static final String MUSIC_EDIT = "music:edit";


    public static final String MUSIC_DELETE = "music:delete";


    public static final String PIPELINE_MANAGE = "pipeline:manage";


    public static final String CONFIG_MANAGE = "config:manage";


    public static final Set<String> PROTECTED_ROLES = Set.of("ROLE_ADMIN", "ROLE_USER");


    public static final Set<String> PROTECTED_PERMISSIONS = Set.of(
            USER_MANAGE,
            MUSIC_BROWSE,
            MUSIC_EDIT,
            MUSIC_DELETE,
            PIPELINE_MANAGE,
            CONFIG_MANAGE
    );


    public static final Set<String> ALL_PERMISSIONS = PROTECTED_PERMISSIONS;
}
