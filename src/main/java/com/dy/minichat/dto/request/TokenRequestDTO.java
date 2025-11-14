package com.dy.minichat.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TokenRequestDTO {
    private String refreshToken;
}