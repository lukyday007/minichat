package com.dy.minichat.repository;

import com.dy.minichat.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // N+1 방지 및 페이징 적용
    Page<Message> findByChatIdAndCreatedAtAfter(
            Long chatId,
            LocalDateTime joinTimestamp,
            Pageable pageable
    );
}
