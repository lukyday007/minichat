package com.dy.minichat.dto.response;

import com.dy.minichat.entity.MessageType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class MessageResponseDTO {
    private Long id;
    private Long senderId;
    private String senderName;
    private Long chatId;
    private String content;
    private MessageType type;
    private int unReadCnt;
}