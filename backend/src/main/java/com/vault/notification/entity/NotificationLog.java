package com.vault.notification.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.UUID;

@Document(collection = "notifications_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLog {

    @Id
    private String notificationId;

    private UUID userId;
    private String channel; // EMAIL | SMS
    private String type;    // OTP | TRANSACTION | STATEMENT | ALERT
    private String status;  // SENT | FAILED
    private LocalDateTime sentAt;
}
