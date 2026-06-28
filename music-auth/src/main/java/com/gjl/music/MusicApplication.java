package com.gjl.music;

import com.gjl.music.auth.config.H2TcpServerContextInitializer;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Music Pipeline 后端主应用 —— 启动入口。
 *
 * <p>H2 TCP Server 由 {@link H2TcpServerContextInitializer} 在
 * Spring 上下文刷新前启动（通过编程式注册，保证在 DataSource 初始化前就绪）。</p>
 *
 * <p>启动安全性由 {@code SecurityConfig.authInitializedMarker} Bean 保障：
 * 如果 JWT secret 未配置，该 Bean 创建失败 → Spring 上下文加载失败 → 应用退出。</p>
 */
@SpringBootApplication(exclude = MybatisAutoConfiguration.class)
@EnableScheduling
public class MusicApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MusicApplication.class);
        app.addInitializers(new H2TcpServerContextInitializer());
        app.run(args);
    }

}
