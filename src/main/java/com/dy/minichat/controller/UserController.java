package com.dy.minichat.controller;

import com.dy.minichat.dto.request.LoginRequestDTO;
import com.dy.minichat.dto.request.SignUpRequestDTO;
import com.dy.minichat.dto.request.TokenRequestDTO;
import com.dy.minichat.dto.response.LoginResponseDTO;
import com.dy.minichat.global.model.BaseResponseBody;
import com.dy.minichat.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/minichat/user/auth")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    // == 회원가입 API == //
    @PostMapping("/signup")
    public ResponseEntity<BaseResponseBody> signUp(
            @RequestBody SignUpRequestDTO request
    ) {
        userService.signUp(request);
        return ResponseEntity.status(201).body(BaseResponseBody.of(201, "회원가입 성공"));
    }


    // == 로그인 (AT + RT 발급) API == //
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(
            @RequestBody LoginRequestDTO request
    ) {
        // AT와 RT가 모두 담긴 DTO를 반환
        LoginResponseDTO tokenDTO = userService.login(request);

        // 6. AT와 RT가 모두 담긴 DTO를 Body로 그대로 반환
        return ResponseEntity.ok(tokenDTO);
    }


    // == Access Token 재발급 (RT 필요) API == //
    @PostMapping("/reissue")
    public ResponseEntity<LoginResponseDTO> reissue(
            @RequestBody TokenRequestDTO request
    ) {
        LoginResponseDTO tokenDTO = userService.reissue(request.getRefreshToken());
        return ResponseEntity.ok(tokenDTO);
    }
}