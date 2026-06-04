package com.remitz.modules.notification.repository;

import com.remitz.modules.notification.entity.SupportTicketEntity;
import com.remitz.modules.notification.entity.SupportTicketEntity.Priority;
import com.remitz.modules.notification.entity.SupportTicketEntity.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicketEntity, Long> {

    List<SupportTicketEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    List<SupportTicketEntity> findByStatusOrderByCreatedAtDesc(TicketStatus status);

    List<SupportTicketEntity> findByStatusAndPriorityOrderByCreatedAtDesc(TicketStatus status, Priority priority);

    List<SupportTicketEntity> findByPriorityOrderByCreatedAtDesc(Priority priority);

    List<SupportTicketEntity> findAllByOrderByCreatedAtDesc();

    Optional<SupportTicketEntity> findByTicketNumber(String ticketNumber);
}
