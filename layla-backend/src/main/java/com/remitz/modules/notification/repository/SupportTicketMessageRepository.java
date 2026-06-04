package com.remitz.modules.notification.repository;

import com.remitz.modules.notification.entity.SupportTicketMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessageEntity, Long> {

    List<SupportTicketMessageEntity> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    long countByTicketId(Long ticketId);
}
