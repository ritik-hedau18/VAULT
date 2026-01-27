package com.vault.audit.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Document(collection = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    private String logId;

    private UUID userId;
    private String action; // LOGIN | TRANSFER | ACCOUNT_FREEZE | ADMIN_ACTION
    private String ipAddress;
    private String deviceInfo;
    private Map<String, Object> metadata;
    private LocalDateTime timestamp;
    private String status; // SUCCESS | FAILURE
}
