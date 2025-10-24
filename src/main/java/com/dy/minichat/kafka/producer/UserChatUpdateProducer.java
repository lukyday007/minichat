package com.dy.minichat.kafka.producer;

import com.dy.minichat.event.UserChatUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserChatUpdateProducer {
    private final KafkaTemplate<String, UserChatUpdateEvent> kafkaTemplate;
    private static final String TOPIC = "user-chat-update";

    public void sendUserChatUpdateEvent(UserChatUpdateEvent event) {
        kafkaTemplate.send(TOPIC, event);
        log.info("[Kafka] UserChatUpdateEvent produced. ChatId={}", event.getChatId());
    }
}