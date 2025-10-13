package com.dy.minichat.service;

import com.dy.minichat.config.id.UserIdGenerator;
import com.dy.minichat.dto.request.UserRequestDTO;
import com.dy.minichat.entity.User;
import com.dy.minichat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserIdGenerator userIdGenerator;

    // == 유저 API == //
    @Transactional
    public User createUser(UserRequestDTO dto) {
        User user = new User();
        user.setId(userIdGenerator.generate());
        // user: snowflake id generate
        user.setName(dto.getName());
        return userRepository.save(user);
    }
}