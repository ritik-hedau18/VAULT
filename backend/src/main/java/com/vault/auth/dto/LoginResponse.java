package com.vault.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private String email;
    private String fullName;
    private String role;
    private boolean twoFaEnabled;
    private boolean twoFaRequired; // True if user has 2FA enabled, but didn't provide totpCode yet.
}
