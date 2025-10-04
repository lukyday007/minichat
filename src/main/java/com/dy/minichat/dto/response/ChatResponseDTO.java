package com.dy.minichat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ChatResponseDTO {
    private Long id;
    private String title;
    private LocalDateTime createdAt;
}