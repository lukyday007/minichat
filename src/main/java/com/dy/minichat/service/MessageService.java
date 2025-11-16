package com.dy.minichat.service;

// import com.dy.minichat.component.TaskManager;
import com.dy.minichat.config.id.MessageIdGenerator;
import com.dy.minichat.dto.request.LastReadMessageRequestDTO;
import com.dy.minichat.dto.request.MessageRequestDTO;
import com.dy.minichat.dto.response.MessageResponseDTO;
import com.dy.minichat.entity.*;
import com.dy.minichat.repository.ChatRepository;
import com.dy.minichat.repository.MessageRepository;
import com.dy.minichat.repository.UserChatRepository;
import com.dy.minichat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {
    // private final TaskManager taskManager;

    private final UserChatUpdateService userChatUpdateService;

    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final UserChatRepository userChatRepository;

    private final MessageIdGenerator messageIdGenerator;

    // == 메세지 API == //
    @Transactional
    public void createMessage (MessageRequestDTO dto, long senderId, long chatId) {
        User sender = userRepository.findById(senderId).orElseThrow(() -> new IllegalArgumentException("유저 정보를 찾을 수 없습니다."));
        Chat chat = chatRepository.findById(chatId).orElseThrow(() -> new IllegalArgumentException("채팅방 참여 정보를 찾을 수 없습니다."));

        Message message = new Message();
        message.setId(messageIdGenerator.generate());
        message.setUser(sender);
        message.setChat(chat);
        message.setContent(dto.getContent());

        // save() 메서드 -> 저장된 Message 객체 반환
        // 이 savedMessage 객체는 id와 createdAt 값이 확실하게 보장됩니다
        Message savedMessage = messageRepository.saveAndFlush(message);
        // ID(snowflake)와 createdAt이 보장된 savedMessage 객체를 업데이트 서비스에 전달합니다.
        userChatUpdateService.updateUserChatOnNewMessage(chatId, savedMessage);
    }

    // 시스템 메시지 생성 및 DB 저장 [입장] //
    @Transactional
    public Message createSystemEntryMessage(Chat chat, List<User> users) {
        String userNames = users.stream()
                .map(User::getName)
                .collect(Collectors.joining(", "));

        Message systemMessage = new Message();
        systemMessage.setId(messageIdGenerator.generate());
        systemMessage.setUser(null); // 시스템 메시지는 유저 정보가 없음
        systemMessage.setChat(chat);
        systemMessage.setMessageType(MessageType.SYSTEM_ENTRY);
        systemMessage.setContent(userNames + "님이 입장했습니다.");

        return messageRepository.save(systemMessage);
    }

    // 시스템 메시지 생성 및 DB 저장 [퇴장] //
    @Transactional
    public Message createSystemLeaveMessage(Chat chat, User leavingUser) {
        Message systemMessage = new Message();
        systemMessage.setId(messageIdGenerator.generate());
        systemMessage.setUser(null);
        systemMessage.setChat(chat);
        systemMessage.setMessageType(MessageType.SYSTEM_LEAVE);
        systemMessage.setContent(leavingUser.getName() + "님이 나갔습니다.");

        return messageRepository.save(systemMessage);
    }

    @Qualifier("redisTemplateForString")
    private final RedisTemplate<String, String> redisTemplateForString;
    private final RedisScript<Long> lastReadUpdateScript;

    // == 메세지 읽음 상태 업데이트 API == //
    /*
     * [개선 전 O(1) DB UPDATE 버전] - JMeter 'Before' 테스트용
     * 메세지 읽음 상태 업데이트 API
     */
    @Transactional // ◀ @Modifying 쿼리를 호출하려면 트랜잭션이 필수
    public void updateLastReadMessage (LastReadMessageRequestDTO dto, Long curUserId, Long chatId) {

        // 1. DTO에서 Message 엔티티 조회 (DB 1회 SELECT)
        Message lastMessage = messageRepository.findById(dto.getLastMessageId())
                .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));

        // 2. DB에 조건부 업데이트 쿼리 실행 (DB 1회 UPDATE)
        userChatRepository.updateLastReadMessageConditionally(
                curUserId,
                chatId,
                lastMessage,
                lastMessage.getId()
        );
    }


    // == 메세지 목록  및 안 읽은 사람 수 반환 API == O ( M + N ) == //
    /**
     * [개선 전 O(N*M) 버전] - JMeter 'Before' 테스트용
     * 메시지 목록 및 안 읽은 사람 수 반환 (비효율적인 중첩 탐색)
     */
    // @Transactional(readOnly = true)
    public List<MessageResponseDTO> getMessageListWithUnreadCounts(Long chatId, Long userId) {

        UserChat userChat = userChatRepository.findReadVersionByUserIdAndChatIdAndIsDeletedFalse(userId, chatId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 참여 정보를 찾을 수 없습니다."));
        LocalDateTime joinTimestamp = userChat.getCreatedAt();

        // M개의 메시지 조회 (O(M))
        List<Message> messages = messageRepository.findByChatIdAndCreatedAtAfterOrderByCreatedAtAscWithUser(chatId, joinTimestamp);
        if (messages.isEmpty()) return new ArrayList<>();

        // N명의 참여자 정보 조회 (O(N))
        List<UserChat> participants = userChatRepository.findByChatIdAndIsDeletedFalseWithLastReadMessage(chatId);
        long totalParticipants = participants.size();

        // N명의 마지막 읽은 ID 조회 (O(N) - MGET 1회 + N번 순회)
        List<String> redisKeys = participants.stream()
                .map(p -> String.format("lastRead:user:%d:chat:%d", p.getUser().getId(), chatId))
                .toList();
        List<String> redisValues = redisTemplateForString.opsForValue().multiGet(redisKeys);

        // N명의 '마지막 읽은 ID' 리스트 생성 (DB Fallback 포함)
        List<Long> participantLastReadIds = new ArrayList<>();
        for (int i = 0; i < participants.size(); i++) {
            UserChat participant = participants.get(i);
            String redisValue = redisValues.get(i);
            Long lastReadId = null;

            if (redisValue != null) {
                lastReadId = Long.parseLong(redisValue);
            } else if (participant.getLastReadMessage() != null) {
                lastReadId = participant.getLastReadMessage().getId();
            }

            // [참고] '개선 전' 코드는 null (아직 한 번도 안 읽음)인 경우를 0L로 처리합니다.
            participantLastReadIds.add(lastReadId != null ? lastReadId : 0L);
        }

        // --- 2. O(N*M) 중첩 탐색 로직 ---
        List<MessageResponseDTO> resultList = new ArrayList<>();

        // O(M) - 바깥쪽 루프 (모든 메시지 순회)
        for (Message message : messages) {
            long messageId = message.getId();
            int readCount = 0; // "이 메시지를 읽은 사람 수"

            // O(N) - 안쪽 루프 (모든 참여자 순회)
            // "이 메시지(messageId)를 N명의 참여자 중 몇 명이 읽었는가?"
            for (Long lastReadId : participantLastReadIds) {

                // 이 참여자가 마지막으로 읽은 ID가 현재 메시지 ID보다 *같거나 크면*
                // 이 참여자는 현재 메시지를 *읽었다*.
                if (lastReadId >= messageId) {
                    readCount++;
                }
            }

            // 안 읽은 사람 수 = (전체 참여자 수) - (이 메시지를 읽은 사람 수)
            int unreadCnt = (int) (totalParticipants - readCount);

            // --- 3. DTO 변환 (동일) ---
            Long senderId;
            String senderName;
            if (message.getMessageType() == MessageType.TALK) {
                senderId = message.getUser().getId();
                senderName = message.getUser().getName();
            } else {
                senderId = 0L;
                senderName = "SYSTEM";
            }

            resultList.add(new MessageResponseDTO(
                    message.getId(),
                    senderId,
                    senderName,
                    message.getChat().getId(),
                    message.getContent(),
                    message.getMessageType(),
                    unreadCnt
            ));
        }

        return resultList;
    }
}