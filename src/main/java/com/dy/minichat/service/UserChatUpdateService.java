package com.dy.minichat.service;

import com.dy.minichat.entity.Message;
import com.dy.minichat.entity.UserChat;
import com.dy.minichat.repository.UserChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserChatUpdateService {
    private final UserChatRepository userChatRepository;

    /*
    1. 비동기
    2. 레디스
    3. 카프카
    4. 스레드플
    5. batchupdate
    update
    resp
    update
    resp * 1000
    batch update

    p.s redis pipeline
    get
    resp
    get
    resp
    get
    get
    get
    resp
    resp
    resp

        별도 스레드 비동기 실행

    */

    @Async
    @Transactional
    public void updateUserChatOnNewMessage(Long chatId, Message lastMessage) {
        userChatRepository.updateAllLastMessageByChatId(
                chatId,
                lastMessage,
                lastMessage.getCreatedAt()
        );
    }
}