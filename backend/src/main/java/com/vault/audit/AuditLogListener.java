package com.vault.audit;

import com.vault.audit.entity.AuditLog;
import com.vault.audit.repository.AuditLogRepository;
import com.vault.transaction.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogListener {

    private final AuditLogRepository auditLogRepository;

    @Async
    @EventListener
    public void onLoginEvent(LoginEvent event) {
        log.info("Processing login audit log for user: {}", event.getEmail());
        try {
            AuditLog auditLog = AuditLog.builder()
                    .userId(event.getUserId())
                    .action(event.getAction())
                    .ipAddress(event.getIpAddress())
                    .deviceInfo(event.getDeviceInfo())
                    .metadata(event.getMetadata())
                    .timestamp(LocalDateTime.now())
                    .status(event.getStatus())
                    .build();

            auditLogRepository.save(auditLog);
            log.info("Successfully saved login audit log to MongoDB");
        } catch (Exception e) {
            log.error("Failed to save login audit log to MongoDB", e);
        }
    }

    @Async
    @EventListener
    public void onTransactionEvent(TransactionEvent event) {
        log.info("Processing transaction audit log for ref: {}", event.getReferenceNumber());
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("referenceNumber", event.getReferenceNumber());
            metadata.put("amount", event.getAmount());
            metadata.put("fromAccountNumber", event.getFromAccountNumber());
            metadata.put("toAccountNumber", event.getToAccountNumber());
            metadata.put("description", event.getDescription());

            AuditLog auditLog = AuditLog.builder()
                    .userId(event.getUserId())
                    .action(event.getType())
                    .ipAddress("N/A")
                    .deviceInfo("SYSTEM_EVENT")
                    .metadata(metadata)
                    .timestamp(LocalDateTime.now())
                    .status(event.getStatus())
                    .build();

            auditLogRepository.save(auditLog);
            log.info("Successfully saved transaction audit log to MongoDB");
        } catch (Exception e) {
            log.error("Failed to save transaction audit log to MongoDB", e);
        }
    }
}
