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
        // 해당 채팅방에 속한 모든 UserChat 엔티티를 조회
        List<UserChat> userChatsInRoom = userChatRepository.findAllByChatIdAndIsDeletedFalse(chatId);

        // 각 UserChat 엔티티의 필드를 새로운 메시지 정보로 업데이트
        for (UserChat userChat : userChatsInRoom) {
            userChat.setLastWrittenMessage(lastMessage);
            userChat.setLastMessageTimestamp(lastMessage.getCreatedAt());
        }

        // 안에서 batch update 하면 더 빨라짐
        // jdbc, jpa 등등 여러 방법이 있음

        // @Transactional 어노테이션에 의해 메서드가 종료될 때
        // 변경된 userChat 엔티티들이 DB에 자동으로 UPDATE 됩니다 (Dirty Checking).
        // userChatRepository.saveAll(userChatsInRoom); // 명시적으로 호출할 필요 없음
    }

}