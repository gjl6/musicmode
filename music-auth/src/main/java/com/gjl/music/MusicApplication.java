package com.gjl.music;

import com.gjl.music.auth.config.H2TcpServerContextInitializer;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication(exclude = MybatisAutoConfiguration.class)
@EnableScheduling
public class MusicApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MusicApplication.class);
        app.addInitializers(new H2TcpServerContextInitializer());
        app.run(args);
    }

}
