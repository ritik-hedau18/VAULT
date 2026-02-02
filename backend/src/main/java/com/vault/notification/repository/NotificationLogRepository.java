package com.vault.notification.repository;

import com.vault.notification.entity.NotificationLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationLogRepository extends MongoRepository<NotificationLog, String> {
    List<NotificationLog> findByUserId(UUID userId);
}
