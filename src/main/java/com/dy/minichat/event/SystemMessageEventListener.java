package com.dy.minichat.event;

import com.dy.minichat.handler.WebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SystemMessageEventListener {
    private final WebSocketHandler webSocketHandler;

    @EventListener
    public void handleSystemMessage(SystemMessageEvent event) {
        log.info("시스템 메시지 이벤트 수신. 채팅방 ID: {}", event.getChatId());
        // WebSocketHandler의 방송 메소드 호출
        // webSocketHandler.broadcastSystemMessage(event.getChatId(), event.getSystemMessage());
    }
}