package com.vault.notification;

import com.vault.auth.entity.User;
import com.vault.auth.repository.UserRepository;
import com.vault.notification.entity.NotificationLog;
import com.vault.notification.repository.NotificationLogRepository;
import com.vault.transaction.TransactionEvent;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final JavaMailSender mailSender;
    private final NotificationLogRepository notificationLogRepository;
    private final UserRepository userRepository;

    @Value("${app.twilio.account-sid}")
    private String twilioAccountSid;

    @Value("${app.twilio.auth-token}")
    private String twilioAuthToken;

    @Value("${app.twilio.phone-number}")
    private String twilioFromPhone;

    @Value("${spring.mail.username}")
    private String mailFrom;

    @Async
    @EventListener
    public void onTransactionEvent(TransactionEvent event) {
        log.info("Processing notifications for transaction: {}", event.getReferenceNumber());
        
        User user = userRepository.findById(event.getUserId()).orElse(null);
        if (user == null) {
            log.error("User not found for transaction notification: {}", event.getUserId());
            return;
        }

        String emailSubject = "VAULT Transaction Alert - " + event.getType();
        String maskedFromAcc = mask(event.getFromAccountNumber());
        String maskedToAcc = mask(event.getToAccountNumber());

        String emailBody = String.format(
                "Dear %s,\n\n" +
                "A transaction has occurred on your VAULT account.\n\n" +
                "Reference Number: %s\n" +
                "Type: %s\n" +
                "Amount: INR %s\n" +
                "Sender Account: %s\n" +
                "Recipient Account: %s\n" +
                "Status: %s\n" +
                "Description: %s\n\n" +
                "Thank you for banking with VAULT.\n\n" +
                "Regards,\nVAULT Core Banking Operations",
                user.getFullName(), event.getReferenceNumber(), event.getType(), event.getAmount().toString(),
                maskedFromAcc, maskedToAcc, event.getStatus(), event.getDescription()
        );

        String smsBody = String.format(
                "VAULT Alert: Transaction of INR %s (%s) was %s. Ref: %s.",
                event.getAmount().toString(), event.getType(), event.getStatus(), event.getReferenceNumber()
        );

        // Send Email
        sendEmailNotification(user, emailSubject, emailBody);

        // Send SMS
        sendSmsNotification(user, smsBody);
    }

    public void sendEmailNotification(User user, String subject, String body) {
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(mailFrom);
            mailMessage.setTo(user.getEmail());
            mailMessage.setSubject(subject);
            mailMessage.setText(body);

            // Avoid attempting connection to real SMTP during local test environment if credentials are default.
            if ("your-email@gmail.com".equals(mailFrom) || "mockpassword".equals(System.getProperty("mail.password"))) {
                log.info("[MOCK EMAIL] Sent email to {}: {}\nBody:\n{}", user.getEmail(), subject, body);
                saveNotificationLog(user, "EMAIL", "TRANSACTION", "SENT");
            } else {
                mailSender.send(mailMessage);
                log.info("Email sent successfully to {}", user.getEmail());
                saveNotificationLog(user, "EMAIL", "TRANSACTION", "SENT");
            }
        } catch (Exception e) {
            log.error("Failed to send email notification to {}", user.getEmail(), e);
            saveNotificationLog(user, "EMAIL", "TRANSACTION", "FAILED");
        }
    }

    public void sendSmsNotification(User user, String body) {
        try {
            // Check if mock credentials are set
            if (twilioAccountSid.startsWith("AC0000000000") || "mock_auth_token_value".equals(twilioAuthToken)) {
                log.info("[MOCK SMS] Sent SMS to {}: {}", user.getPhone(), body);
                saveNotificationLog(user, "SMS", "TRANSACTION", "SENT");
            } else {
                Twilio.init(twilioAccountSid, twilioAuthToken);
                Message.creator(
                        new PhoneNumber(user.getPhone()),
                        new PhoneNumber(twilioFromPhone),
                        body
                ).create();
                log.info("SMS sent successfully to {}", user.getPhone());
                saveNotificationLog(user, "SMS", "TRANSACTION", "SENT");
            }
        } catch (Exception e) {
            log.error("Failed to send SMS notification to {}", user.getPhone(), e);
            saveNotificationLog(user, "SMS", "TRANSACTION", "FAILED");
        }
    }

    private void saveNotificationLog(User user, String channel, String type, String status) {
        try {
            NotificationLog notificationLog = NotificationLog.builder()
                    .userId(user.getId())
                    .channel(channel)
                    .type(type)
                    .status(status)
                    .sentAt(LocalDateTime.now())
                    .build();
            notificationLogRepository.save(notificationLog);
        } catch (Exception e) {
            log.error("Failed to save notification log to MongoDB", e);
        }
    }

    private String mask(String accNo) {
        if (accNo == null || "CASH/ATM".equals(accNo)) return accNo;
        if (accNo.length() <= 4) return "****";
        return "XXXX XXXX XXXX " + accNo.substring(accNo.length() - 4);
    }
}
