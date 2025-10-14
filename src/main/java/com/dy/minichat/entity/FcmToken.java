package com.dy.minichat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

@Entity
@Getter
@Setter
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