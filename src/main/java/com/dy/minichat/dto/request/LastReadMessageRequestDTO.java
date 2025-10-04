package com.dy.minichat.dto.request;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
public class LastReadMessageRequestDTO {
    private Long lastMessageId;
}