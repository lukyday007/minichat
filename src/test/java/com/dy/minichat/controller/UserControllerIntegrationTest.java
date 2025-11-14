package com.dy.minichat.controller;

import com.dy.minichat.config.id.UserIdGenerator;
import com.dy.minichat.dto.request.LoginRequestDTO;
import com.dy.minichat.dto.request.SignUpRequestDTO;
import com.dy.minichat.dto.request.TokenRequestDTO;
import com.dy.minichat.dto.response.LoginResponseDTO;
import com.dy.minichat.entity.User;
import com.dy.minichat.global.model.BaseResponseBody;
import com.dy.minichat.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
@ActiveProfiles("test")
class UserControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserIdGenerator userIdGenerator;

    // (공통) 헬퍼 메서드
    private User saveTestUser(String email, String password) {
        String encodedPassword = passwordEncoder.encode(password);
        User user = new User();
        user.setId(userIdGenerator.generate());
        user.setEmail(email);
        user.setPassword(encodedPassword);
        user.setName("TestUser");
        return userRepository.saveAndFlush(user);
    }


    @Test
    @DisplayName("[API] 회원가입 성공 (TestRestTemplate)")
    void signUpSuccess() throws Exception {
        // given
        // 1. 클라이언트가 보낼 요청 DTO 준비
        SignUpRequestDTO requestDTO = new SignUpRequestDTO("new@user.com", "pass123", "NewUser");

        // when
        // 2. "/signup" API에 실제 POST 요청
        ResponseEntity<BaseResponseBody> response = restTemplate.postForEntity(
                "/minichat/user/auth/signup", // 요청 URL
                requestDTO,                   // 요청 Body (자동으로 JSON 변환됨)
                BaseResponseBody.class        // 응답 DTO 타입
        );

        // then
        // 3. HTTP 응답 검증
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED); // 201
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(201);
        assertThat(response.getBody().getMessage()).isEqualTo("회원가입 성공");

        // 4. (중요) 실제 DB에 데이터가 저장되었는지 검증
        User foundUser = (User) userRepository.findByEmail("new@user.com").orElse(null);
        assertThat(foundUser).isNotNull();
        assertThat(foundUser.getName()).isEqualTo("NewUser");
        assertThat(passwordEncoder.matches("pass123", foundUser.getPassword())).isTrue();
    }

    @Test
    @DisplayName("[API] 로그인 성공 (TestRestTemplate)")
    void loginSuccess() throws Exception {
        // given
        // 1. 로그인을 위해 DB에 유저를 미리 저장
        saveTestUser("login@user.com", "pass123");

        // 2. 클라이언트 요청 DTO 준비
        LoginRequestDTO requestDTO = new LoginRequestDTO("login@user.com", "pass123");

        // when & then
        // 3. /login API에 실제 POST 요청
        ResponseEntity<LoginResponseDTO> response = restTemplate.postForEntity(
                "/minichat/user/auth/login",
                requestDTO,
                LoginResponseDTO.class
        );

        // 4. HTTP 응답 검증
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK); // 200
        // 5. 응답 JSON 검증
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUserId()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isNotEmpty();
        assertThat(response.getBody().getRefreshToken()).isNotEmpty();
    }

    @Test
    @DisplayName("[API] 토큰 재발급 성공 (TestRestTemplate)")
    void reissueSuccess() throws Exception {
        // given
        // 1. DB에 유저 저장
        User savedUser = saveTestUser("reissue@user.com", "pass123");

        // 2. (선행) /login API를 호출하여 유효한 Refresh Token을 먼저 받아옴
        LoginRequestDTO loginDTO = new LoginRequestDTO("reissue@user.com", "pass123");
        ResponseEntity<LoginResponseDTO> loginResponse = restTemplate.postForEntity(
                "/minichat/user/auth/login",
                loginDTO,
                LoginResponseDTO.class
        );
        String oldRefreshToken = loginResponse.getBody().getRefreshToken();

        // 3. 재발급 요청 DTO 준비
        TokenRequestDTO reissueRequestDTO = new TokenRequestDTO();

        // TestRestTemplate은 DTO 대신 HttpEntity<String>을 보낼 수 있습니다.
        org.springframework.http.HttpEntity<String> requestEntity =
                new org.springframework.http.HttpEntity<>("{\"refreshToken\":\"" + oldRefreshToken + "\"}",
                        new org.springframework.http.HttpHeaders() {{
                            setContentType(MediaType.APPLICATION_JSON);
                        }});

        // 4. (선택) 1초 대기 (JWT의 iat가 달라지게 하여 새 토큰임을 보장)
        Thread.sleep(1000);

        // when & then
        // 5. /reissue API 요청
        ResponseEntity<LoginResponseDTO> reissueResponse = restTemplate.postForEntity(
                "/minichat/user/auth/reissue",
                requestEntity, // DTO 대신 수동으로 만든 JSON 문자열 전송
                LoginResponseDTO.class
        );

        // 6. HTTP 응답 검증
        assertThat(reissueResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 7. 응답 JSON 검증
        LoginResponseDTO responseBody = reissueResponse.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.getUserId()).isEqualTo(savedUser.getId());
        assertThat(responseBody.getAccessToken()).isNotEmpty();
        // 8. 새 토큰이 발급되었는지 (다른 토큰인지) 검증
        assertThat(responseBody.getRefreshToken()).isNotEqualTo(oldRefreshToken);
    }
}