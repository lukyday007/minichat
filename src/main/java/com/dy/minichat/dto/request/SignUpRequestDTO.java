package com.dy.minichat.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SignUpRequestDTO {
    private String email; // 또는 email
    private String password;
    private String name;
}