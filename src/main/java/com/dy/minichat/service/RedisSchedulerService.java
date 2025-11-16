package com.dy.minichat.service;

import com.dy.minichat.entity.Message;
import com.dy.minichat.repository.MessageRepository;
import com.dy.minichat.repository.UserChatJdbcRepository;
import com.dy.minichat.repository.UserChatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSchedulerService {

    private final UserChatRepository userChatRepository;
    private final MessageRepository messageRepository; // '읽음' 상태를 Message 객체로 set하기 위해

    @Qualifier("redisTemplateForLong")
    private final RedisTemplate<String, Long> redisTemplateForLong;
    @Qualifier("redisTemplateForString")
    private final RedisTemplate<String, String> redisTemplateForString;

    private static final String DIRTY_SET_KEY = "lastRead:dirty_keys";
    private static final int BATCH_SIZE = 1000; // 만개는 위험함

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void syncLastReadMessagesToDB() {
        log.info("🕒 [Scheduler] Syncing lastReadMessage from Redis to DB (N+1 LOOP)...");

        List<String> dirtyKeys = redisTemplateForString.opsForSet().pop(DIRTY_SET_KEY, BATCH_SIZE);
        if (dirtyKeys == null || dirtyKeys.isEmpty()) {
            log.info("⚪ [Scheduler] No dirty keys found.");
            return;
        }

        List<String> keysToRemoveFromCache = new ArrayList<>();
        List<String> keysToReAdd = new ArrayList<>();

        // [개선 전 N+1 쓰기 로직]
        for (String key : dirtyKeys) {
            try {
                Long lastMessageId = redisTemplateForLong.opsForValue().get(key);
                if (lastMessageId == null) continue;

                String[] parts = key.split(":");
                if (parts.length < 6) continue;

                Long userId = Long.parseLong(parts[2]);
                Long chatId = Long.parseLong(parts[4]);

                // 1. N번의 SELECT (Message 엔티티를 가져오기 위해)
                Message lastMessage = messageRepository.findById(lastMessageId)
                        .orElseThrow(() -> new IllegalArgumentException("Message not found"));

                // 2. N번의 UPDATE (JPA가 @Modifying 쿼리 실행)
                userChatRepository.updateLastReadMessageConditionally(
                        userId,
                        chatId,
                        lastMessage,
                        lastMessageId
                );

                keysToRemoveFromCache.add(key);

            } catch (Exception e) {
                log.error("❌ Failed to parse/update Redis key={} (N+1 Loop)", key, e);
                keysToReAdd.add(key);
            }
        }

        // 성공한 키 Redis에서 제거
        if (!keysToRemoveFromCache.isEmpty()) {
            redisTemplateForLong.delete(keysToRemoveFromCache);
        }
        // 실패한 키 Dirty Set에 다시 추가
        if (!keysToReAdd.isEmpty()) {
            redisTemplateForString.opsForSet().add(DIRTY_SET_KEY, keysToReAdd.toArray(new String[0]));
        }

        log.info("✅ Redis → DB sync complete (JPA N+1 Loop). Processed: {}", dirtyKeys.size());
    }
}