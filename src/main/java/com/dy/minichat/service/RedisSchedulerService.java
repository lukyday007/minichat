package com.dy.minichat.service;

import com.dy.minichat.repository.UserChatJdbcRepository;
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

    private final UserChatJdbcRepository userChatJdbcRepository;

    @Qualifier("redisTemplateForLong")
    private final RedisTemplate<String, Long> redisTemplateForLong;
    @Qualifier("redisTemplateForString")
    private final RedisTemplate<String, String> redisTemplateForString;

    private static final String DIRTY_SET_KEY = "lastRead:dirty_keys";
    private static final int BATCH_SIZE = 1000; // 만개는 위험함

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void syncLastReadMessagesToDB() {
        log.info("🕒 [Scheduler] Syncing lastReadMessage from Redis to DB...");
        /*
            // Dirty set에서 모든 키 가져오기
            Set<String> dirtyKeys = redisTemplateForString.opsForSet().members(DIRTY_SET_KEY);
            => 문제 상황: 트래픽이 몰리거나 스케줄러가 잠시 멈춰서 Dirty Set에 100만 개의 키가 쌓이면, 이 코드는 100만 개의 문자열을 메모리에 로드하려다 **OutOfMemoryError(OOM)**로 인해 서버가 다운
         */

        List<String> dirtyKeys = redisTemplateForString.opsForSet().pop(DIRTY_SET_KEY, BATCH_SIZE);
        if (dirtyKeys == null || dirtyKeys.isEmpty()) {
            log.info("⚪ [Scheduler] No dirty keys found.");
            return;
        }

        List<UserChatJdbcRepository.UserChatUpdate> batchList = new ArrayList<>();

        for (String key : dirtyKeys) {
            try {
                Long lastMessageId = redisTemplateForLong.opsForValue().get(key);
                if (lastMessageId == null) continue;

                String[] parts = key.split(":");
                if (parts.length < 6) continue;

                Long userId = Long.parseLong(parts[2]);
                Long chatId = Long.parseLong(parts[4]);

                batchList.add(new UserChatJdbcRepository.UserChatUpdate(
                        userId, chatId, lastMessageId, key
                ));

            } catch (Exception e) {
                log.error("Failed to parse Redis key={}", key, e);
            }

            // 배치 크기 도달 시 DB에 반영
            if (batchList.size() >= BATCH_SIZE) {
                executeBatch(batchList);
                batchList.clear();
            }
        }

        // 남은 배치 처리
        if (!batchList.isEmpty()) {
            executeBatch(batchList);
        }

        log.info("✅ Redis → DB sync complete.");
    }

    private void executeBatch(List<UserChatJdbcRepository.UserChatUpdate> batch) {
        try {
            userChatJdbcRepository.batchUpdateLastRead(batch);

            // 성공한 키 Dirty Set에서 제거
            String[] keysToRemoveFromCache = batch.stream()
                    .map(UserChatJdbcRepository.UserChatUpdate::getDirtyKey)
                    .toArray(String[]::new);

            redisTemplateForLong.delete(List.of(keysToRemoveFromCache));

            log.info("📦 Batch of {} keys synced to DB.", batch.size());

        } catch (Exception e) {
            log.error("❌ Batch DB update failed, will retry next schedule.", e);

            // 실패 시 Dirty Set 유지 → 다음 스케줄러에서 재시도
            String[] keysToReAdd = batch.stream()
                    .map(UserChatJdbcRepository.UserChatUpdate::getDirtyKey)
                    .toArray(String[]::new);
            redisTemplateForString.opsForSet().add(DIRTY_SET_KEY, keysToReAdd);

            // 만약 정합성이 정말 중요한 부분이면
            // 카프카로 실패 이벤트 발행 * 2,3번 -> 이래도 실패? -> 로그 찍어서 손수 해결
        }
    }
}