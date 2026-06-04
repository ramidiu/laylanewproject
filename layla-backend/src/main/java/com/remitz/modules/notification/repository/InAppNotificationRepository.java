package com.remitz.modules.notification.repository;

import com.remitz.modules.notification.entity.InAppNotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InAppNotificationRepository extends JpaRepository<InAppNotificationEntity, Long> {

    Page<InAppNotificationEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<InAppNotificationEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByUserIdAndIsReadFalse(Long userId);

    List<InAppNotificationEntity> findByUserIdAndIsReadFalse(Long userId);

    @Modifying
    @Query("UPDATE InAppNotificationEntity n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP WHERE n.userId = :userId AND n.isRead = false")
    int markAllAsReadByUserId(@Param("userId") Long userId);

    @Query(value = "SELECT id FROM users WHERE status = 'ACTIVE'", nativeQuery = true)
    List<Long> findAllActiveUserIds();
}
