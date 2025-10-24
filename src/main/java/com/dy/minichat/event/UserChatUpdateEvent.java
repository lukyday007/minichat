package com.dy.minichat.event;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserChatUpdateEvent {
    private Long chatId;
    private Long lastMessageId;
    private LocalDateTime timestamp;
}