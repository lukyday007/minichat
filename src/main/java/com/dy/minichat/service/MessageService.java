package com.dy.minichat.service;

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
        Message savedMessage = messageRepository.save(message);
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

    @Qualifier("redisTemplateForLong")
    private final RedisTemplate<String, Long> redisTemplateForLong;
    @Qualifier("redisTemplateForString")
    private final RedisTemplate<String, String> redisTemplateForString;
    private final RedisScript<Long> lastReadUpdateScript;

    // == 메세지 읽음 상태 업데이트 API == //
    public void updateLastReadMessage (LastReadMessageRequestDTO dto, Long curUserId, Long chatId) {

        String LAST_READ_KEY = "lastRead:user:%d:chat:%d";
        String DIRTY_SET_KEY = "lastRead:dirty_keys";

        Message lastMessage = messageRepository.findById(dto.getLastMessageId())
                .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));

        // [수정 1] findByUserIdAndChatId에 비관적 락이 적용되어 동시성 문제 방지
        // [수정 2] 비관적락의 비효율성으로 인한 추가 수정 -> 쿼리를 통한 최적화

        try {
            Long result = redisTemplateForString.execute(
                    lastReadUpdateScript,
                    List.of(LAST_READ_KEY, DIRTY_SET_KEY),
                    lastMessage.getId().toString()
            );

            if (result != null && result == 1L) {
                log.info("✅ [Lua-Atomic] user={}, chat={}, lastMessageId={}", curUserId, chatId, lastMessage.getId());
            } else {
                log.info("ℹ️ [Skipped] 기존 메시지보다 작거나 동일한 ID → 업데이트 안함");
            }

        } catch (Exception e) {
            log.error("❌ Redis Lua script failed for user={}, chat={}, error={}", curUserId, chatId, e.getMessage());
        }
    }
    // @Query(update userchat last_read_message_id = 11 ... last_read_message_id < 11)

    // @Query(update userchat last_read_message_id = 10 ... last_read_Message_id < 10)

    // update age = age +1 where month = 4;

    // == 메세지 목록  및 안 읽은 사람 수 반환 API == O ( M + N ) == //
    @Transactional(readOnly = true)
    public List<MessageResponseDTO> getMessageListWithUnreadCounts(Long chatId, Long userId) {

        UserChat userChat = userChatRepository.findReadVersionByUserIdAndChatIdAndIsDeletedFalse(userId, chatId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 참여 정보를 찾을 수 없습니다."));
        LocalDateTime joinTimestamp = userChat.getCreatedAt();

        // 조회된 joinTimestamp를 사용해 "보여줄 메시지만 필터링" -> 유저가 참가하고 난 이후의 메시지만 반환
        List<Message> messages = messageRepository.findByChatIdAndCreatedAtAfterOrderByCreatedAtAscWithUser(chatId, joinTimestamp);

        if (messages.isEmpty()) return new ArrayList<>();

        // 메세지 안읽은 사람 수 계산을 위한 참가자 정보 조회
        List<UserChat> participants = userChatRepository.findByChatIdAndIsDeletedFalseWithLastReadMessage(chatId);

        /*
            !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            앞에서 updateLastReadMessage 를 업데이트할 때 레디스 캐싱을 사용했으니,
            여기서도 그에 때른 레디스 조회 로직으로 수정해야함
            !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        */


        /*
            어떤 메시지를 몇 명이 마지막으로 읽었는지
            lastReadMessageId가 각각 (101, 103, 103, 105, 105)
            readCountMap : {101: 1L, 103: 2L, 105: 2L}
            => messageId 101 : 1명 , messageId 103 : 2명...
            ===> readCntMap를 그냥 client에 줘버리기 -> 더 효율적일수도 있음.
                클라이언트에서 핸들링하기 더 쉬움
        */
        /*
            [단계 1]
            // key: lastReadMessageId, value: 해당 ID까지 읽은 사람의 수
            Map<Long, Long> readCntMap = participants.stream()
                .filter(p -> p.getLastReadMessage() != null)
                .collect(Collectors.groupingBy(p -> p.getLastReadMessage().getId(), Collectors.counting()));
            [단계 2] - 레디스 전환
            Map<Long, Long> userLastReadMap = new HashMap<>();
            for (UserChat participant : participants) {
                Long participantId = participant.getUser().getId();
                String redisKey = String.format("lastRead:user:%d:chat:%d", participantId, chatId);
                String redisValue = redisTemplateForString.opsForValue().get(redisKey);

                Long lastReadId = null;
                if (redisValue != null) {
                    lastReadId = Long.parseLong(redisValue);
                } else if (participant.getLastReadMessage() != null) {
                    // fallback: Redis에 없을 경우 DB 값 사용
                    lastReadId = participant.getLastReadMessage().getId();
                }

                if (lastReadId != null) {
                    userLastReadMap.put(participantId, lastReadId);
                }
            }
        */

        // Redis에서 각 참여자의 lastReadMessageId 가져오기 => 레디스 N + 1 발생 : 레디스에서의 시간복잡도는 바로 네트워크에 영향이 가서 매우 잘 고려해야함 => MGET(pipeline 하위버전)!! 1번!!
        List<String> redisKeys = participants.stream()
                .map(p -> String.format("lastRead:user:%d:chat:%d", p.getUser().getId(), chatId))
                .toList();

        // MGET으로 일괄 조회
        // MGET 이 안될 때 redispipeline 사용
        List<String> redisValues = redisTemplateForString.opsForValue().multiGet(redisKeys);

        Map<Long, Long> userLastReadMap = new HashMap<>();
        for (int i = 0; i < participants.size(); i++) {
            UserChat participant = participants.get(i);
            String redisValue = redisValues.get(i);

            Long lastReadId = null;
            if (redisValue != null) {
                lastReadId = Long.parseLong(redisValue);
            } else if (participant.getLastReadMessage() != null) {
                // fallback
                lastReadId = participant.getLastReadMessage().getId();
            }

            if (lastReadId != null) {
                userLastReadMap.put(participant.getUser().getId(), lastReadId);
            }
        }

        // 4 messageId별 읽은 인원 수 집계
        Map<Long, Long> readCntMap = userLastReadMap.values().stream()
                .collect(Collectors.groupingBy(id -> id, Collectors.counting()));

        // 누적 읽은 사람 수
        List<MessageResponseDTO> resultList = new ArrayList<>();
        long totalParticipants = participants.size();
        long cumulativeReadCnt = 0; // 현재까지 '읽은' 사람의 누적 합계

        for (Message message : messages) {  // 시간순으로 정렬된 메시지
            long messageId = message.getId();

            if (readCntMap.containsKey(messageId))
                cumulativeReadCnt += readCntMap.get(messageId);

            long unreadCnt = totalParticipants - cumulativeReadCnt;

            Long senderId;
            String senderName;

            // 메시지 타입 확인
            //      일반 대화(TALK) 메시지 => 실제 유저 정보 사용
            if (message.getMessageType() == MessageType.TALK) {
                senderId = message.getUser().getId();
                senderName = message.getUser().getName();
            }
            //      시스템 메시지(SYSTEM_ENTRY, SYSTEM_LEAVE 등) => 약속된 시스템 ID 사용
            else {
                senderId = 0L; // 시스템 유저 ID
                senderName = "SYSTEM";
            }

            resultList.add(new MessageResponseDTO(
                    message.getId(),
                    senderId,
                    senderName,
                    message.getChat().getId(),
                    message.getContent(),
                    message.getMessageType(),
                    (int) unreadCnt
            ));
        }

        return resultList;
    }

}