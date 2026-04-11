package com.example.korrag.repository;

import com.example.korrag.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 특정 유저의 최근 대화 N개를 시간 역순으로 조회합니다.
     */
    @Query(value = "SELECT * FROM test.chat_messages WHERE user_id = :userId ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    List<ChatMessage> findRecentMessages(@Param("userId") String userId, @Param("limit") int limit);

    /**
     * 특정 유저의 최근 대화 N개를 시간 순서대로 조회합니다 (슬라이딩 윈도우용).
     */
    default List<ChatMessage> findRecentMessagesAsc(String userId, int limit) {
        List<ChatMessage> messages = findRecentMessages(userId, limit);
        return messages.stream()
                .sorted((m1, m2) -> m1.getId().compareTo(m2.getId()))
                .toList();
    }

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM ChatMessage c WHERE c.userId = :userId")
    void deleteByUserId(@Param("userId") String userId);
}
