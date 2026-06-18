package com.gjl.music.auth.dto;


public record RegisterRequest(String username, String password, String email, String displayName) {
}
