package com.dy.minichat.dto.response;

import com.dy.minichat.entity.ChatStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class UserChatResponseDTO {
    private Long chatId;
    private ChatStatus status;
    private String title;
    private String lastMessageContent;
    private LocalDateTime lastMessageTimestamp;
}
