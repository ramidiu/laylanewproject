package com.remitz.modules.notification.repository;

import com.remitz.modules.notification.entity.UserDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDeviceEntity, Long> {

    List<UserDeviceEntity> findByUserIdAndIsActiveTrue(Long userId);

    Optional<UserDeviceEntity> findByDeviceToken(String token);
}
