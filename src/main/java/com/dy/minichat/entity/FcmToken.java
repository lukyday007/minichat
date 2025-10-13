package com.dy.minichat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FcmToken {
    @Id
    private Long userId;

    @Column(nullable = false)
    private String token;

    public FcmToken updateToken(String newToken) {
        this.token = newToken;
        return this;
    }
}