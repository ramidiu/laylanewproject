package com.remitz.modules.notification.repository;

import com.remitz.common.enums.NotificationStatus;
import com.remitz.modules.notification.entity.NotificationLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLogEntity, Long> {

    Page<NotificationLogEntity> findByUserId(Long userId, Pageable pageable);

    List<NotificationLogEntity> findByStatus(NotificationStatus status);

    Page<NotificationLogEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
