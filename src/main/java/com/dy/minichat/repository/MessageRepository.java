package com.dy.minichat.repository;

import com.dy.minichat.entity.Message;
import com.dy.minichat.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /*
        문제의 N + 1
        List<Message> findByChatIdAndCreatedAtAfterOrderByCreatedAtAsc(Long chatId, LocalDateTime joinTimestamp);
    */
    // 메시지 조회 시 user 정보를 함께 가져오도록 수정
    @Query("select m from Message m left join fetch m.user where m.chat.id = :chatId and m.createdAt > :joinTimestamp order by m.createdAt asc")
    List<Message> findByChatIdAndCreatedAtAfterOrderByCreatedAtAscWithUser(
            @Param("chatId") Long chatId,
            @Param("joinTimestamp") LocalDateTime joinTimestamp);
}
