package com.gjl.music.auth.config;

import org.h2.tools.Server;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.sql.SQLException;

/**
 * H2 TCP Server 启动器 —— 由 music-auth 在最早期接管。
 *
 * <p>作为 {@link ApplicationContextInitializer}，在 Spring context refresh
 * <b>之前</b>执行，早于 DataSource 创建。保证数据库服务先于一切就绪。</p>
 *
 * <h3>生命周期</h3>
 * <ol>
 *   <li>SpringApplication.run() → prepareContext() 阶段调用 {@link #initialize}</li>
 *   <li>启动 H2 TCP Server（端口 9092）并阻塞等待就绪</li>
 *   <li>注册为 {@link DisposableBean}，Spring 关闭时自动调用 server.stop()</li>
 * </ol>
 */
public class H2TcpServerContextInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        try {
            Server server = Server.createTcpServer(
                    "-tcp", "-tcpAllowOthers", "-tcpPort", "9092", "-ifNotExists"
            ).start();

            // 等待服务真正就绪
            while (!server.isRunning(true)) {
                Thread.sleep(100);
            }

            // 注册为 DisposableBean，Spring 关闭时自动调用 server.stop()，防止数据库文件损坏
            context.getBeanFactory().registerSingleton("h2TcpServer",
                    (DisposableBean) server::stop);

            System.out.println("H2 TCP Server started on port 9092 (by music-auth)");
        } catch (SQLException | InterruptedException e) {
            throw new RuntimeException("H2 TCP Server start failed", e);
        }
    }
}
