package com.dy.minichat.controller;

import com.dy.minichat.dto.request.UserRequestDTO;
import com.dy.minichat.global.model.BaseResponseBody;
import com.dy.minichat.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/minichat")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    // == 유저 생성 API == //
    @PostMapping("/user")
    public ResponseEntity<BaseResponseBody> createUser(
            @RequestBody UserRequestDTO request
    ) {
        userService.createUser(request);
        return ResponseEntity.status(201).body(BaseResponseBody.of(201, "유저 등록 성공."));
    }

}