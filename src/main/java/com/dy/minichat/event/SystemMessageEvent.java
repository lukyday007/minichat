package com.dy.minichat.event;

import com.dy.minichat.entity.Message;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class SystemMessageEvent {
    private final Long chatId;
    private final Message systemMessage;
}
