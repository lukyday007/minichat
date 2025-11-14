package com.dy.minichat.service;

import com.dy.minichat.component.JwtTokenProvider;
import com.dy.minichat.config.id.UserIdGenerator;
import com.dy.minichat.dto.request.LoginRequestDTO;
import com.dy.minichat.dto.request.SignUpRequestDTO;
import com.dy.minichat.dto.response.LoginResponseDTO;
import com.dy.minichat.entity.User;
import com.dy.minichat.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class) // Mockito 사용을 위한 어노테이션
class UserServiceTest {

    @InjectMocks // 테스트 대상 (SUT). Mock 객체들을 주입받습니다.
    private UserService userService;

    // -----------------------------------------------------------------
    // 의존성들은 모두 @Mock으로 선언 (가짜 객체)
    // -----------------------------------------------------------------
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserIdGenerator userIdGenerator;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;


    @Test
    @DisplayName("회원가입 성공")
    void signUpSuccess() {
        // given (Arrange)
        SignUpRequestDTO dto = new SignUpRequestDTO("test@email.com", "password123", "TestUser");
        User savedUser = new User();
        savedUser.setId(1L);
        savedUser.setEmail("test@email.com");
        savedUser.setPassword("encodedPassword"); // 인코딩된 패스워드
        savedUser.setName("TestUser");

        // Mock 객체들의 행동 정의
        given(userRepository.existsByEmail(dto.getEmail())).willReturn(false); // 1. 이메일이 존재하지 않음
        given(passwordEncoder.encode(dto.getPassword())).willReturn("encodedPassword"); // 2. 패스워드 인코딩 결과
        given(userIdGenerator.generate()).willReturn(1L); // 3. ID 생성 결과
        given(userRepository.save(any(User.class))).willReturn(savedUser); // 4. save 결과

        // when (Act)
        User result = userService.signUp(dto);

        // then (Assert)
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo(dto.getEmail());
        assertThat(result.getPassword()).isEqualTo("encodedPassword");

        // Mock 객체들이 의도한 대로 호출되었는지 검증
        then(userRepository).should().existsByEmail(dto.getEmail());
        then(passwordEncoder).should().encode(dto.getPassword());
        then(userIdGenerator).should().generate();
        then(userRepository).should().save(any(User.class));
    }

    @Test
    @DisplayName("회원가입 실패 - 중복된 이메일")
    void signUpFail_EmailAlreadyExists() {
        // given
        SignUpRequestDTO dto = new SignUpRequestDTO("test@email.com", "password123", "TestUser");

        // Mock: 이미 이메일이 존재한다고 반환
        given(userRepository.existsByEmail(dto.getEmail())).willReturn(true);

        // when & then
        // 예외가 발생하는지 검증
        assertThatThrownBy(() -> userService.signUp(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 사용 중인 ID입니다.");

        // 예외가 발생했으므로, save나 encode는 호출되지 않아야 함
        then(passwordEncoder).should(never()).encode(anyString());
        then(userRepository).should(never()).save(any(User.class));
    }

    @Test
    @DisplayName("로그인 성공")
    void loginSuccess() {
        // given
        LoginRequestDTO dto = new LoginRequestDTO("test@email.com", "password123");

        User foundUser = new User();
        foundUser.setId(1L);
        foundUser.setEmail(dto.getEmail());
        foundUser.setPassword("encodedPassword");

        given(userRepository.findByEmail(dto.getEmail())).willReturn(Optional.of(foundUser)); // 1. 유저 찾음
        given(passwordEncoder.matches(dto.getPassword(), foundUser.getPassword())).willReturn(true); // 2. 비밀번호 일치
        given(jwtTokenProvider.generateAccessToken(1L)).willReturn("newAccessToken"); // 3. 토큰 발급
        given(jwtTokenProvider.generateRefreshToken(1L)).willReturn("newRefreshToken");

        // when
        LoginResponseDTO response = userService.login(dto);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("newAccessToken");
        assertThat(response.getRefreshToken()).isEqualTo("newRefreshToken");
        assertThat(response.getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("로그인 실패 - 존재하지 않는 이메일")
    void loginFail_UserNotFound() {
        // given
        LoginRequestDTO dto = new LoginRequestDTO("test@email.com", "password123");

        // Mock: 유저를 찾지 못함
        given(userRepository.findByEmail(dto.getEmail())).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.login(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 이메일입니다.");
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 불일치")
    void loginFail_PasswordMismatch() {
        // given
        LoginRequestDTO dto = new LoginRequestDTO("test@email.com", "password123");

        User foundUser = new User();
        foundUser.setId(1L);
        foundUser.setEmail(dto.getEmail());
        foundUser.setPassword("encodedPassword");

        given(userRepository.findByEmail(dto.getEmail())).willReturn(Optional.of(foundUser));
        given(passwordEncoder.matches(dto.getPassword(), foundUser.getPassword())).willReturn(false); // 2. 비밀번호 불일치

        // when & then
        assertThatThrownBy(() -> userService.login(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("비밀번호가 일치하지 않습니다.");
    }

    @Test
    @DisplayName("토큰 재발급 성공")
    void reissueSuccess() {
        // given
        String oldRefreshToken = "oldRefreshToken";
        Long userId = 1L;
        User user = new User();
        user.setId(userId);

        given(jwtTokenProvider.validateToken(oldRefreshToken)).willReturn(true); // 1. 토큰 유효
        given(jwtTokenProvider.getUserIdFromToken(oldRefreshToken)).willReturn(userId); // 2. ID 추출
        given(userRepository.findById(userId)).willReturn(Optional.of(user)); // 3. 유저 존재
        given(jwtTokenProvider.generateAccessToken(userId)).willReturn("newAccessToken"); // 4. 새 토큰 발급
        given(jwtTokenProvider.generateRefreshToken(userId)).willReturn("newRefreshToken");

        // when
        LoginResponseDTO response = userService.reissue(oldRefreshToken);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("newAccessToken");
        assertThat(response.getRefreshToken()).isEqualTo("newRefreshToken");
    }

    @Test
    @DisplayName("토큰 재발급 실패 - 유효하지 않은 토큰")
    void reissueFail_InvalidToken() {
        // given
        String invalidRefreshToken = "invalidToken";

        // Mock: 토큰 검증 실패
        given(jwtTokenProvider.validateToken(invalidRefreshToken)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> userService.reissue(invalidRefreshToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 Refresh Token");
    }
}