package com.dy.minichat.service;

import com.dy.minichat.entity.Message;
import com.dy.minichat.event.UserChatUpdateEvent;
import com.dy.minichat.kafka.producer.UserChatUpdateProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserChatUpdateService {
    private final UserChatUpdateProducer userChatUpdateProducer;

    @Transactional
    public void updateUserChatOnNewMessage(Long chatId, Message lastMessage) {
        UserChatUpdateEvent event = UserChatUpdateEvent.builder()
                .chatId(chatId)
                .lastMessageId(lastMessage.getId())
                .timestamp(lastMessage.getCreatedAt())
                .build();

        userChatUpdateProducer.sendUserChatUpdateEvent(event);
    }
}