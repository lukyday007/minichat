package com.dy.minichat.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FcmTokenRequestDTO {
    private String token; // FCM 토큰
}