package com.remitz.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {

    Page<LoginHistory> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<LoginHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<LoginHistory> findByUserEmailAndEventTypeAndCreatedAtAfter(
            String email, String eventType, LocalDateTime after);

    @Query("SELECT l.eventType, COUNT(l) FROM LoginHistory l WHERE l.createdAt > :since GROUP BY l.eventType")
    List<Object[]> countByEventTypeSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(l) FROM LoginHistory l WHERE l.eventType = 'LOGIN_FAILED' AND l.userEmail = :email AND l.createdAt > :since")
    long countFailedLoginsSince(@Param("email") String email, @Param("since") LocalDateTime since);
}
