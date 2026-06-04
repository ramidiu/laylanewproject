package com.remitz.modules.notification.service;

import com.remitz.modules.notification.dto.*;
import com.remitz.modules.notification.entity.SupportTicketAttachmentEntity;
import com.remitz.modules.notification.entity.SupportTicketEntity;
import com.remitz.modules.notification.entity.SupportTicketEntity.*;
import com.remitz.modules.notification.entity.SupportTicketMessageEntity;
import com.remitz.modules.notification.repository.SupportTicketAttachmentRepository;
import com.remitz.modules.notification.repository.SupportTicketMessageRepository;
import com.remitz.modules.notification.repository.SupportTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportTicketService {

    private static final String ATTACHMENT_BASE_PATH = "/tmp/remitz/support-attachments";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Random RANDOM = new Random();

    private final SupportTicketRepository ticketRepository;
    private final SupportTicketMessageRepository messageRepository;
    private final SupportTicketAttachmentRepository attachmentRepository;

    @Transactional
    public TicketResponse createTicket(String userId, String email, String name,
                                        CreateTicketRequest req, List<MultipartFile> files) {
        String ticketNumber = generateTicketNumber();

        SupportTicketEntity ticket = SupportTicketEntity.builder()
                .ticketNumber(ticketNumber)
                .userId(userId)
                .userEmail(email)
                .userName(name)
                .subject(req.getSubject())
                .issueType(req.getIssueType())
                .priority(req.getPriority() != null ? req.getPriority() : Priority.MEDIUM)
                .status(TicketStatus.OPEN)
                .build();

        ticket = ticketRepository.save(ticket);

        SupportTicketMessageEntity message = SupportTicketMessageEntity.builder()
                .ticket(ticket)
                .senderType(SenderType.CUSTOMER)
                .senderId(userId)
                .senderName(name)
                .message(req.getMessage())
                .build();

        message = messageRepository.save(message);

        List<SupportTicketAttachmentEntity> attachments = saveAttachments(ticket, message, files);

        return toTicketResponse(ticket, List.of(toMessageResponse(message, attachments)), 1L);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getMyTickets(String userId) {
        List<SupportTicketEntity> tickets = ticketRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return tickets.stream().map(ticket -> {
            long msgCount = messageRepository.countByTicketId(ticket.getId());
            List<SupportTicketMessageEntity> messages = messageRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());
            String latestMsg = messages.isEmpty() ? null : messages.get(messages.size() - 1).getMessage();
            return toTicketResponseSummary(ticket, latestMsg, msgCount);
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicketDetail(Long ticketId, String userId) {
        SupportTicketEntity ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        if (!ticket.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied to this ticket");
        }

        return buildDetailedTicketResponse(ticket);
    }

    @Transactional
    public TicketMessageResponse replyToTicket(Long ticketId, String senderId, String senderName,
                                                String senderType, String messageText,
                                                List<MultipartFile> files) {
        SupportTicketEntity ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        SenderType type = SenderType.valueOf(senderType);

        SupportTicketMessageEntity message = SupportTicketMessageEntity.builder()
                .ticket(ticket)
                .senderType(type)
                .senderId(senderId)
                .senderName(senderName)
                .message(messageText)
                .build();

        message = messageRepository.save(message);

        // Update ticket status based on sender
        if (type == SenderType.AGENT && ticket.getStatus() == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
        } else if (type == SenderType.AGENT) {
            ticket.setStatus(TicketStatus.AWAITING_CUSTOMER);
        } else if (type == SenderType.CUSTOMER && ticket.getStatus() == TicketStatus.AWAITING_CUSTOMER) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
        }
        ticketRepository.save(ticket);

        List<SupportTicketAttachmentEntity> attachments = saveAttachments(ticket, message, files);

        return toMessageResponse(message, attachments);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getAllTickets(String status, String priority) {
        List<SupportTicketEntity> tickets;

        if (status != null && priority != null) {
            tickets = ticketRepository.findByStatusAndPriorityOrderByCreatedAtDesc(
                    TicketStatus.valueOf(status), Priority.valueOf(priority));
        } else if (status != null) {
            tickets = ticketRepository.findByStatusOrderByCreatedAtDesc(TicketStatus.valueOf(status));
        } else if (priority != null) {
            tickets = ticketRepository.findByPriorityOrderByCreatedAtDesc(Priority.valueOf(priority));
        } else {
            tickets = ticketRepository.findAllByOrderByCreatedAtDesc();
        }

        return tickets.stream().map(ticket -> {
            long msgCount = messageRepository.countByTicketId(ticket.getId());
            List<SupportTicketMessageEntity> messages = messageRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());
            String latestMsg = messages.isEmpty() ? null : messages.get(messages.size() - 1).getMessage();
            return toTicketResponseSummary(ticket, latestMsg, msgCount);
        }).collect(Collectors.toList());
    }

    @Transactional
    public TicketResponse updateTicketStatus(Long ticketId, UpdateTicketStatusRequest req) {
        SupportTicketEntity ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        ticket.setStatus(req.getStatus());
        if (req.getAssignedTo() != null) {
            ticket.setAssignedTo(req.getAssignedTo());
        }

        if (req.getStatus() == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(LocalDateTime.now());
        } else if (req.getStatus() == TicketStatus.CLOSED) {
            ticket.setClosedAt(LocalDateTime.now());
        }

        ticket = ticketRepository.save(ticket);

        // Add system message about status change
        SupportTicketMessageEntity sysMsg = SupportTicketMessageEntity.builder()
                .ticket(ticket)
                .senderType(SenderType.SYSTEM)
                .senderName("System")
                .message("Ticket status changed to " + req.getStatus().name())
                .build();
        messageRepository.save(sysMsg);

        return buildDetailedTicketResponse(ticket);
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicketDetailAdmin(Long ticketId) {
        SupportTicketEntity ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        return buildDetailedTicketResponse(ticket);
    }

    public SupportTicketAttachmentEntity getAttachment(Long attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Attachment not found"));
    }

    // --- Private helpers ---

    private String generateTicketNumber() {
        String dateStr = LocalDateTime.now().format(DATE_FORMAT);
        String randomPart = String.format("%04d", RANDOM.nextInt(10000));
        return "TKT-" + dateStr + "-" + randomPart;
    }

    private List<SupportTicketAttachmentEntity> saveAttachments(SupportTicketEntity ticket,
                                                                  SupportTicketMessageEntity message,
                                                                  List<MultipartFile> files) {
        List<SupportTicketAttachmentEntity> attachments = new ArrayList<>();
        if (files == null || files.isEmpty()) return attachments;

        Path ticketDir = Paths.get(ATTACHMENT_BASE_PATH, ticket.getId().toString());
        try {
            Files.createDirectories(ticketDir);
        } catch (IOException e) {
            log.error("Failed to create attachment directory: {}", ticketDir, e);
            throw new RuntimeException("Failed to store attachments");
        }

        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            try {
                String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
                Path filePath = ticketDir.resolve(fileName);
                file.transferTo(filePath.toFile());

                SupportTicketAttachmentEntity attachment = SupportTicketAttachmentEntity.builder()
                        .ticket(ticket)
                        .message(message)
                        .fileName(file.getOriginalFilename())
                        .filePath(filePath.toString())
                        .fileSize(file.getSize())
                        .contentType(file.getContentType())
                        .build();

                attachments.add(attachmentRepository.save(attachment));
            } catch (IOException e) {
                log.error("Failed to save attachment: {}", file.getOriginalFilename(), e);
            }
        }
        return attachments;
    }

    private TicketResponse buildDetailedTicketResponse(SupportTicketEntity ticket) {
        List<SupportTicketMessageEntity> messages = messageRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());
        List<TicketMessageResponse> messageResponses = messages.stream().map(msg -> {
            List<SupportTicketAttachmentEntity> msgAttachments = attachmentRepository.findByMessageId(msg.getId());
            return toMessageResponse(msg, msgAttachments);
        }).collect(Collectors.toList());

        TicketResponse response = toTicketResponse(ticket, messageResponses, messages.size());
        List<SupportTicketAttachmentEntity> allAttachments = attachmentRepository.findByTicketId(ticket.getId());
        response.setAttachments(allAttachments.stream().map(this::toAttachmentResponse).collect(Collectors.toList()));
        return response;
    }

    private TicketResponse toTicketResponse(SupportTicketEntity ticket,
                                             List<TicketMessageResponse> messages, long messageCount) {
        String latestMsg = messages.isEmpty() ? null : messages.get(messages.size() - 1).getMessage();
        return TicketResponse.builder()
                .id(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .userId(ticket.getUserId())
                .userEmail(ticket.getUserEmail())
                .userName(ticket.getUserName())
                .subject(ticket.getSubject())
                .issueType(ticket.getIssueType())
                .priority(ticket.getPriority())
                .status(ticket.getStatus())
                .assignedTo(ticket.getAssignedTo())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .resolvedAt(ticket.getResolvedAt())
                .closedAt(ticket.getClosedAt())
                .latestMessage(latestMsg)
                .messageCount(messageCount)
                .messages(messages)
                .build();
    }

    private TicketResponse toTicketResponseSummary(SupportTicketEntity ticket, String latestMessage, long messageCount) {
        return TicketResponse.builder()
                .id(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .userId(ticket.getUserId())
                .userEmail(ticket.getUserEmail())
                .userName(ticket.getUserName())
                .subject(ticket.getSubject())
                .issueType(ticket.getIssueType())
                .priority(ticket.getPriority())
                .status(ticket.getStatus())
                .assignedTo(ticket.getAssignedTo())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .resolvedAt(ticket.getResolvedAt())
                .closedAt(ticket.getClosedAt())
                .latestMessage(latestMessage)
                .messageCount(messageCount)
                .build();
    }

    private TicketMessageResponse toMessageResponse(SupportTicketMessageEntity msg,
                                                     List<SupportTicketAttachmentEntity> attachments) {
        return TicketMessageResponse.builder()
                .id(msg.getId())
                .senderType(msg.getSenderType())
                .senderId(msg.getSenderId())
                .senderName(msg.getSenderName())
                .message(msg.getMessage())
                .createdAt(msg.getCreatedAt())
                .attachments(attachments.stream().map(this::toAttachmentResponse).collect(Collectors.toList()))
                .build();
    }

    private TicketAttachmentResponse toAttachmentResponse(SupportTicketAttachmentEntity att) {
        return TicketAttachmentResponse.builder()
                .id(att.getId())
                .fileName(att.getFileName())
                .fileSize(att.getFileSize())
                .contentType(att.getContentType())
                .url("/api/support/attachments/" + att.getId() + "/file")
                .uploadedAt(att.getUploadedAt())
                .build();
    }
}
