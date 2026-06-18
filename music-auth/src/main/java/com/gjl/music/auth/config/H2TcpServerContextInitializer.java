package com.gjl.music.auth.config;

import org.h2.tools.Server;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.sql.SQLException;


public class H2TcpServerContextInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        try {
            Server server = Server.createTcpServer(
                    "-tcp", "-tcpAllowOthers", "-tcpPort", "9092", "-ifNotExists"
            ).start();

                        while (!server.isRunning(true)) {
                Thread.sleep(100);
            }

                        context.getBeanFactory().registerSingleton("h2TcpServer",
                    (DisposableBean) server::stop);

            System.out.println("H2 TCP Server started on port 9092 (by music-auth)");
        } catch (SQLException | InterruptedException e) {
            throw new RuntimeException("H2 TCP Server start failed", e);
        }
    }
}
