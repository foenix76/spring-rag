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
     * 특정 유저의 최근 대화 N개를 시간 순서대로 조회합니다 (과거 -> 현재).
     * 최신 N개를 가져오기 위해 id 역순으로 먼저 뽑고, 그걸 다시 정순으로 정렬합니다.
     */
    @Query(value = "SELECT * FROM (SELECT * FROM test.chat_messages WHERE user_id = :userId ORDER BY id DESC LIMIT :limit) sub ORDER BY id ASC", nativeQuery = true)
    List<ChatMessage> findRecentMessagesAsc(@Param("userId") String userId, @Param("limit") int limit);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM ChatMessage c WHERE c.userId = :userId")
    void deleteByUserId(@Param("userId") String userId);
}
