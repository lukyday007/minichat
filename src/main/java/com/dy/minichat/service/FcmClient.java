package com.dy.minichat.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmClient {
    public void sendMessage(String token, String title, String body, Map<String, String> data) {
        if (token == null || token.isEmpty()) {
            log.warn("FCM 토큰이 비어있어 메시지 전송을 스킵합니다.");
            return;
        }

        // 1. 메시지 빌더를 초기화합니다.
        Message.Builder messageBuilder = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build());

        // 2. 데이터 페이로드가 있다면 메시지에 추가합니다.
        if (data != null && !data.isEmpty()) {
            messageBuilder.putAllData(data);
        }

        // 3. 메시지를 빌드하고 전송합니다.
        try {
            FirebaseMessaging.getInstance().send(messageBuilder.build());
            log.info("FCM 메시지 전송 성공 token={}", token);
        } catch (FirebaseMessagingException e) {
            log.error("FCM 메시지 전송 실패 token={}", token, e);
        }
    }}