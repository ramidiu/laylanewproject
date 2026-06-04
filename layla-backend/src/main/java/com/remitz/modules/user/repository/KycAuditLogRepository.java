package com.remitz.modules.user.repository;

import com.remitz.modules.user.entity.KycAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KycAuditLogRepository extends JpaRepository<KycAuditLogEntity, Long> {

    List<KycAuditLogEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
}
