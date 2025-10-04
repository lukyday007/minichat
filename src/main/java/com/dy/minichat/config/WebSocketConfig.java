package com.dy.minichat.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@EnableWebSocket
@RequiredArgsConstructor
@Configuration
public class WebSocketConfig implements WebSocketConfigurer {
    private final WebSocketHandler webSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler, "/ws/minichat")
                .setAllowedOrigins("*")
                .addInterceptors(new UserIdHandshakeInterceptor());
    }

    // HandshakeInterceptor 구현 클래스
    private static class UserIdHandshakeInterceptor implements HandshakeInterceptor {
        @Override
        public boolean beforeHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                org.springframework.web.socket.WebSocketHandler wsHandler,
                Map<String, Object> attributes
        ) throws Exception {
            // 1. 여기서 우리채팅서버(카톡)을 쓸 수 있는 유저인지 토큰검증(제이더블유티)
            //    2. 검증이 안된경우 끊어버림...
            // 3. 검증이 된경우 -> 유저아이디 세팅 해놓고 --->

            if (request instanceof ServletServerHttpRequest) {
                ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
                // URL의 쿼리 파라미터에서 "userId" 값 읽기
                String userIdStr = servletRequest.getServletRequest().getParameter("userId");
                if (userIdStr != null && !userIdStr.isEmpty()) {
                    // 읽어온 userId 값을 WebSocket 세션의 attributes 맵에 저장
                    attributes.put("userId", Long.parseLong(userIdStr));
                }
            }
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   org.springframework.web.socket.WebSocketHandler wsHandler,
                                   Exception exception) {
            // Do nothing
        }
    }
}