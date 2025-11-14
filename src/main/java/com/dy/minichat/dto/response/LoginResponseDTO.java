package com.dy.minichat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Builder
@AllArgsConstructor
public class LoginResponseDTO {
    private String accessToken;
    private String refreshToken;  // ★ 새로 추가
    private Long userId;
}