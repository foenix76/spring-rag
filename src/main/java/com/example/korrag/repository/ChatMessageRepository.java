package com.example.korrag.repository;

import com.example.korrag.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query(value = "SELECT * FROM (SELECT * FROM test.chat_messages WHERE user_id = :userId ORDER BY id DESC LIMIT :limit) sub ORDER BY id ASC", nativeQuery = true)
    List<ChatMessage> findRecentMessagesAsc(@Param("userId") String userId, @Param("limit") int limit);

    /**
     * 특정 유저의 가장 최근 요약본(SUMMARY)을 조회합니다.
     */
    java.util.Optional<ChatMessage> findFirstByUserIdAndRoleOrderByIdDesc(String userId, String role);

    /**
     * 특정 ID(요약본 ID) 이후로 쌓인 메시지 개수를 카운트합니다.
     */
    long countByUserIdAndIdGreaterThan(String userId, Long id);

    /**
     * 유저 전체 메시지 개수 (요약본 없을 때용)
     */
    long countByUserId(String userId);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ChatMessage c WHERE c.userId = :userId AND c.role = :role")
    void deleteByUserIdAndRole(@Param("userId") String userId, @Param("role") String role);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ChatMessage c WHERE c.userId = :userId")
    void deleteByUserId(@Param("userId") String userId);
}

