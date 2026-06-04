package com.remitz.modules.notification.repository;

import com.remitz.modules.notification.entity.SupportTicketAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportTicketAttachmentRepository extends JpaRepository<SupportTicketAttachmentEntity, Long> {

    List<SupportTicketAttachmentEntity> findByTicketId(Long ticketId);

    List<SupportTicketAttachmentEntity> findByMessageId(Long messageId);
}
