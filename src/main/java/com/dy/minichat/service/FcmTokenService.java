package com.dy.minichat.service;

import com.dy.minichat.entity.FcmToken;
import com.dy.minichat.repository.FcmTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 간단한 임시 메모리 기반 토큰 저장소
 * 실제 환경에서는 Redis나 MySQL 테이블에 user_id <-> token 매핑 저장
 */
@Service
@RequiredArgsConstructor
public class FcmTokenService {

    @Qualifier("redisTemplateForString")
    private final RedisTemplate<String, String> redisTemplateForString;

    private final FcmTokenRepository fcmTokenRepository;
    private static final String FCM_TOKEN_KEY = "fcm_tokens";
    private static final long TOKEN_TTL_SECONDS = 60L * 60 * 24 * 30; // 30일

    /*
        토큰 같은 정보는 디비에 영구적으로 저장이 되어있어야 하긴 함 -> mysql
        유저가 오랜만에 접속해도 토큰이 있어야 함 -> 레디스가 과연 이 데이터를 계속 저장할 수 있을까?
                                            -> 가능? -> 캐시 아님!
                                            -> 디비로 따로 저장
    */

    /*
      FCM 토큰 등록
      - Redis와 MySQL 둘 다 저장
      - Redis에는 TTL 설정
    */
    public void registerToken(Long userId, String token) {
        redisTemplateForString.opsForHash().put(FCM_TOKEN_KEY, String.valueOf(userId), token);
        redisTemplateForString.expire(FCM_TOKEN_KEY, TOKEN_TTL_SECONDS, TimeUnit.SECONDS);

        FcmToken fcmToken = fcmTokenRepository.findByUserId(userId)
                .map(existing -> existing.updateToken(token))
                .orElseGet(() -> new FcmToken(userId, token));

        fcmTokenRepository.save(fcmToken);
    }

    /*
     FCM 토큰 조회
     - Redis 우선 조회
     - Redis에 없으면 DB에서 조회 후 다시 Redis에 캐싱
     */
    public String getTokenByUserId(Long userId) {
        String token = (String) redisTemplateForString.opsForHash().get(FCM_TOKEN_KEY, String.valueOf(userId));

        if (token == null) {
            FcmToken fcmToken = fcmTokenRepository.findByUserId(userId).orElse(null);
            if (fcmToken != null) {
                token = fcmToken.getToken();
                redisTemplateForString.opsForHash().put(FCM_TOKEN_KEY, String.valueOf(userId), token);
                redisTemplateForString.expire(FCM_TOKEN_KEY, TOKEN_TTL_SECONDS, TimeUnit.SECONDS);
            }
        }
        return token;
    }

    /*
      FCM 토큰 삭제
      - Redis와 MySQL에서 모두 삭제
    */
    public void removeToken(Long userId) {
        redisTemplateForString.opsForHash().delete(FCM_TOKEN_KEY, String.valueOf(userId));

        fcmTokenRepository.deleteByUserId(userId);
    }
}