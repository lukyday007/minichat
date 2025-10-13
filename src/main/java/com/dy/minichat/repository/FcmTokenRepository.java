package com.dy.minichat.repository;

import com.dy.minichat.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {
    Optional<FcmToken> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}