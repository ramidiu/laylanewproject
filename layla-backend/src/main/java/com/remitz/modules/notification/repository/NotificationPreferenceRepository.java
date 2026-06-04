package com.remitz.modules.notification.repository;

import com.remitz.modules.notification.entity.NotificationPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreferenceEntity, Long> {

    Optional<NotificationPreferenceEntity> findByUserId(Long userId);

    List<NotificationPreferenceEntity> findByUserIdIn(java.util.Collection<Long> userIds);
}
