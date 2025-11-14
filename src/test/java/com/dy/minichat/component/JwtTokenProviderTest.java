package com.dy.minichat.component;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        String base64Key = "T7JR6jkSh5ycDLtDRsvNm+r1Z+2qeeh4k8db8+zRUfar9DkOUq/QMJw3Cb+XxromuMsOVtBaTMKHYfXjVxEGww==";
        jwtTokenProvider = new JwtTokenProvider(base64Key);
    }

    @Test
    void accessTokenTest() {
        // given
        Long userId = 123L;

        // when
        String token = jwtTokenProvider.generateAccessToken(userId);

        System.out.println("Access Token: " + token);

        // then
        assertTrue(jwtTokenProvider.validateToken(token), "토큰 검증 실패");
        assertEquals(userId, jwtTokenProvider.getUserIdFromToken(token), "UserId 추출 실패");
    }

    @Test
    void refreshTokenTest() {
        // given
        Long userId = 456L;

        // when
        String token = jwtTokenProvider.generateRefreshToken(userId);

        System.out.println("refresh Token: " + token);

        // then
        assertTrue(jwtTokenProvider.validateToken(token), "토큰 검증 실패");
        assertEquals(userId, jwtTokenProvider.getUserIdFromToken(token), "UserId 추출 실패");
    }

    @Test
    void validTokenTest() { // 1. 테스트 이름 변경 (권장)
        // given
        Long userId = 123L; // 유효한 토큰을 생성하기 위한 ID

        // 2. 가짜 문자열 대신, provider를 사용해 '진짜' 토큰을 생성
        String validToken = jwtTokenProvider.generateAccessToken(userId);

        // when & then
        // 3. 'false'가 아닌 'true'가 나와야 성공
        assertTrue(jwtTokenProvider.validateToken(validToken), "유효한 토큰이 검증 실패함");
    }

    @Test void invalidTokenTest() {
        // given
        String invalidToken = "not.a.valid.jwt";

        System.out.println("-------> invalidTokenTest");
        // when & then
        assertFalse(jwtTokenProvider.validateToken(invalidToken), "잘못된 토큰이 검증됨"); }
}