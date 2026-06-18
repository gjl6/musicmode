package com.gjl.music.auth.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.Import;


@AutoConfiguration
@AutoConfigureBefore(name = "org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration")
@Import({SecurityConfig.class})
public class AuthAutoConfiguration {
}
