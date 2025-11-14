package com.dy.minichat.controller;

import com.dy.minichat.dto.request.FcmTokenRequestDTO;
import com.dy.minichat.service.FcmTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/minichat/fcm")
@RequiredArgsConstructor
public class FcmTokenController {

    private final FcmTokenService fcmTokenService;

    // 클라이언트로부터 FCM 토큰을 등록받는 API
    @PostMapping("/token")
    public ResponseEntity<Void> registerFcmToken(
            @AuthenticationPrincipal Long userId,
            @RequestBody FcmTokenRequestDTO request    ) {

        // [수정] payload.get("userId") 대신 인증된 userId 사용
        fcmTokenService.registerToken(userId, request.getToken());        return ResponseEntity.ok().build();
    }
}