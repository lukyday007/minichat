package com.dy.minichat.controller;

import com.dy.minichat.service.FcmTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/fcm")
@RequiredArgsConstructor
public class FcmTokenController {

    private final FcmTokenService fcmTokenService;

    // 클라이언트로부터 FCM 토큰을 등록받는 API
    @PostMapping("/token")
    public ResponseEntity<Void> registerFcmToken(@RequestBody Map<String, String> payload) {
        // 실제로는 Spring Security 등을 통해 인증된 사용자의 ID를 가져와야 함
        // 여기서는 예시로 payload에서 userId를 받는다고 가정
        Long userId = Long.parseLong(payload.get("userId"));
        String token = payload.get("token");

        fcmTokenService.registerToken(userId, token);
        return ResponseEntity.ok().build();
    }
}