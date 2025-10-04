package com.dy.minichat.repository;

import com.dy.minichat.entity.Message;
import com.dy.minichat.entity.User;
import com.dy.minichat.entity.UserChat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserChatRepository extends JpaRepository<UserChat, Long> {
    /*
        [ MessageService.updateLastReadMessage() ==> 효율적이지 못한 방식 ]
        // [기존] 쓰기/수정용: 동시성 제어를 위해 락을 유지
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        Optional<UserChat> findByUserIdAndChatId(Long curUserId, Long chatId);
    */

    /*
     * 마지막으로 읽은 메시지를 조건부로 업데이트합니다.
     * @return 업데이트된 행(row)의 수
     */
    @Modifying // UPDATE, DELETE 쿼리 실행 시 필요
    @Query("UPDATE UserChat uc " +
            "SET uc.lastReadMessage = :lastMessage " +
            "WHERE uc.user.id = :userId AND uc.chat.id = :chatId AND uc.isDeleted = false " +
            "AND (uc.lastReadMessage IS NULL OR uc.lastReadMessage.id < :lastMessageId)")
    int updateLastReadMessageConditionally(
            @Param("userId") Long userId,
            @Param("chatId") Long chatId,
            @Param("lastMessage") Message lastMessage,
            @Param("lastMessageId") Long lastMessageId);

    // leaveChatRoom에서 사용
    // find 할 때 메소드 명을 findActiveUserChat -> isDeletedFalse 보다 더 직관적
    Optional<UserChat> findByUserIdAndChatIdAndIsDeletedFalse(Long userId, Long chatId);

    // [추가] 단순 조회용: 락이 없는 메소드를 새로 추가
    Optional<UserChat> findReadVersionByUserIdAndChatIdAndIsDeletedFalse(Long userId, Long chatId);

    /*
        문제의 N + 1
        List<UserChat> findByChatId(Long chatId);
    */
    // 채팅방 참여자 조회 시 lastReadMessage를 함께 가져오도록 수정
    @Query("select uc from UserChat uc left join fetch uc.lastReadMessage where uc.chat.id = :chatId AND uc.isDeleted = false")
    List<UserChat> findByChatIdAndIsDeletedFalseWithLastReadMessage(@Param("chatId") Long chatId);

    /*
        문제의 N + 1
        List<UserChat> findAllByUserId(Long userId);
    */
    @Query("SELECT uc FROM UserChat uc " +
            "JOIN FETCH uc.chat c " +
            "LEFT JOIN FETCH uc.lastWrittenMessage m " + // 내용을 위해 fetch
            "WHERE uc.user.id = :userId AND uc.isDeleted = false " +
            "ORDER BY uc.lastMessageTimestamp DESC") // 정렬은 timestamp로
    List<UserChat> findAllByUserIdOrderByLastMessageTimestampDesc(@Param("userId") Long userId);

    /*
        UserChatUpdateService 에서 사용
     */
    List<UserChat> findAllByChatIdAndIsDeletedFalse(Long chatId);

    // [추가] 특정 채팅방에 속한 모든 사용자의 ID 목록만 조회 쿼리
    @Query("SELECT uc.user.id FROM UserChat uc WHERE uc.chat.id = :chatId AND uc.isDeleted = false")
    List<Long> findUserIdsByChatId(@Param("chatId") Long chatId);

}
