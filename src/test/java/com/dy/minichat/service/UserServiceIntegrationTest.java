package com.dy.minichat.service;

import com.dy.minichat.component.JwtTokenProvider;
import com.dy.minichat.config.id.UserIdGenerator; // 1. import
import com.dy.minichat.dto.request.LoginRequestDTO;
import com.dy.minichat.dto.request.SignUpRequestDTO;
import com.dy.minichat.dto.response.LoginResponseDTO;
import com.dy.minichat.entity.User;
import com.dy.minichat.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class UserServiceIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired
    private UserIdGenerator userIdGenerator;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;


    // (공통) 테스트에서 사용할 유저를 미리 저장하는 헬퍼 메서드
    private User saveTestUser(String email, String password) {
        String encodedPassword = passwordEncoder.encode(password);
        User user = new User();

        // 3. 주입받은 generator로 generate() 메서드를 호출합니다.
        user.setId(userIdGenerator.generate());

        user.setEmail(email);
        user.setPassword(encodedPassword);
        user.setName("TestUser");
        return userRepository.saveAndFlush(user); // saveAndFlush로 즉시 DB 반영
    }


    @Test
    @DisplayName("시나리오 1: 회원가입 성공")
    void signUpSuccess() {
        // given
        SignUpRequestDTO dto = new SignUpRequestDTO("new@user.com", "pass123", "NewUser");

        // when
        // (참고) signUp 메서드 내부에서도 UserIdGenerator가 자동으로 주입되어 사용됩니다.
        User savedUser = userService.signUp(dto);

        // then
        assertThat(savedUser.getId()).isNotNull(); // ID가 생성되었는지 확인
        User foundUser = (User) userRepository.findByEmail("new@user.com").orElse(null);
        assertThat(foundUser).isNotNull();
        assertThat(foundUser.getId()).isEqualTo(savedUser.getId());
    }


    @Test
    @DisplayName("시나리오 2: 로그인 성공")
    void loginSuccess() {
        // given
        // 1. 헬퍼 메서드가 이제 실제 ID를 생성하여 유저를 저장합니다.
        User savedUser = saveTestUser("login@user.com", "password123");

        // 2. 로그인 요청 DTO
        LoginRequestDTO requestDTO = new LoginRequestDTO("login@user.com", "password123");

        // when
        LoginResponseDTO response = userService.login(requestDTO);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotEmpty();

        // 4. 생성된 ID와 로그인 응답의 ID가 일치하는지 확인
        assertThat(response.getUserId()).isEqualTo(savedUser.getId());
    }


    @Test
    @DisplayName("시나리오 3: 토큰 재발급(reissue) 성공")
    void reissueSuccess() {
        // given
        // 1. 테스트 유저를 저장합니다.
        User savedUser = saveTestUser("reissue@user.com", "pass123");

        // 2. 해당 유저의 유효한 Refresh Token을 직접 생성합니다.
        String oldRefreshToken = jwtTokenProvider.generateRefreshToken(savedUser.getId());

        // when
        // 3. 토큰 재발급을 요청합니다.
        LoginResponseDTO response = userService.reissue(oldRefreshToken);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(savedUser.getId());

        // 4. 새 Access Token과 Refresh Token이 발급되었는지 확인합니다.
        assertThat(response.getAccessToken()).isNotEmpty();
        assertThat(response.getRefreshToken()).isNotEmpty();

        // 5. (중요) 토큰이 '회전'되었는지 (이전 토큰과 다른지) 확인합니다.
        assertThat(jwtTokenProvider.validateToken(response.getAccessToken())).isTrue();
        assertThat(jwtTokenProvider.validateToken(response.getRefreshToken())).isTrue();    }


    @Test
    @DisplayName("시나리오 4: 유효하지 않은 토큰으로 재발급 실패")
    void reissueFail_InvalidToken() {
        // given
        String garbageToken = "this.is.invalid.token";

        // when & then
        // validateToken() 메서드에서 실패해야 합니다.
        assertThatThrownBy(() -> userService.reissue(garbageToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 Refresh Token");
    }


    @Test
    @DisplayName("시나리오 5: 존재하지 않는 유저의 토큰으로 재발급 실패")
    void reissueFail_UserNotFound() {
        // given
        // 1. 존재하지 않는 유저 ID (예: 999L)로 토큰을 임의로 생성
        // (주의: 실제 JwtTokenProvider 구현에 따라 이 토큰이
        //  validateToken()을 통과한다는 가정 하에 테스트합니다.)
        Long nonExistentUserId = 999L;
        String validTokenForDeletedUser = jwtTokenProvider.generateRefreshToken(nonExistentUserId);

        // (만약 validateToken()이 DB까지 확인한다면,
        //  "시나리오 3"처럼 유저를 생성했다가 DB에서 삭제하는 것이 더 정확합니다.)

        // when & then
        // userRepository.findById() 에서 실패해야 합니다.
        assertThatThrownBy(() -> userService.reissue(validTokenForDeletedUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용자를 찾을 수 없습니다.");
    }
}