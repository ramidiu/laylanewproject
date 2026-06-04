package com.remitz.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccessLogRepository extends JpaRepository<AccessLog, Long> {

    Page<AccessLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AccessLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<AccessLog> findByServiceNameOrderByCreatedAtDesc(String serviceName, Pageable pageable);
}
