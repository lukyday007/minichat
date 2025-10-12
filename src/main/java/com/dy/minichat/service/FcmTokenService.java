package com.dy.minichat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 간단한 임시 메모리 기반 토큰 저장소
 * 실제 환경에서는 Redis나 MySQL 테이블에 user_id <-> token 매핑 저장
 */
@Service
@RequiredArgsConstructor
public class FcmTokenService {
    private final RedisTemplate<String, String> redisTemplate;
    private static final String FCM_TOKEN_KEY = "fcm_tokens";

    /*
        토큰 같은 정보는 디비에 영구적으로 저장이 되어있어야 하긴 함 -> mysql
        유저가 오랜만에 접속해도 토큰이 있어야 함 -> 레디스가 과연 이 데이터를 계속 저장할 수 있을까?
                                            -> 가능? -> 캐시 아님!
                                            -> 디비로 따로 저장
    */

    /*
        ttl 설정하기!
    */
    public void registerToken(Long userId, String token) {
        // opsForHash() : Hash 타입의 데이터를 다루는 커맨드 모음
        // put(KEY, HASH_KEY, HASH_VALUE)
        redisTemplate.opsForHash().put(FCM_TOKEN_KEY, String.valueOf(userId), token);
    }

    public String getTokenByUserId(Long userId) {
        // get(KEY, HASH_KEY)
        return (String) redisTemplate.opsForHash().get(FCM_TOKEN_KEY, String.valueOf(userId));
    }

    public void removeToken(Long userId) {
        // delete(KEY, HASH_KEY)
        redisTemplate.opsForHash().delete(FCM_TOKEN_KEY, String.valueOf(userId));
    }
}