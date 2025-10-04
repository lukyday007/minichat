package com.dy.minichat.repository;

import com.dy.minichat.entity.Chat;
import com.dy.minichat.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {
}
