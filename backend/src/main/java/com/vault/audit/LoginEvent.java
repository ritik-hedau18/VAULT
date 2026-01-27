package com.vault.audit;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Map;
import java.util.UUID;

@Getter
public class LoginEvent extends ApplicationEvent {

    private final UUID userId;
    private final String email;
    private final String action; // LOGIN | LOGOUT
    private final String ipAddress;
    private final String deviceInfo;
    private final String status; // SUCCESS | FAILURE
    private final Map<String, Object> metadata;

    public LoginEvent(Object source, UUID userId, String email, String action, String ipAddress, String deviceInfo, String status, Map<String, Object> metadata) {
        super(source);
        this.userId = userId;
        this.email = email;
        this.action = action;
        this.ipAddress = ipAddress;
        this.deviceInfo = deviceInfo;
        this.status = status;
        this.metadata = metadata;
    }
}
