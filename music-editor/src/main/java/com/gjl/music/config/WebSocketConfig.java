package com.gjl.music.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** WebSocket + STOMP 配置 —— 用于向前端实时推送 Pipeline 进度 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 服务端推送前缀：客户端订阅 /topic/pipelines/{id}/progress
        registry.enableSimpleBroker("/topic");
        // 客户端发送前缀：/app (当前阶段预留)
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 前端连接端点，启用 SockJS 降级兼容
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }
}
