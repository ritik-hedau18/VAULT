package com.vault.auth.controller;

import com.vault.auth.dto.*;
import com.vault.auth.entity.User;
import com.vault.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }

    @PostMapping("/register")
    public ResponseEntity<User> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        return new ResponseEntity<>(user, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest) {
        
        String ipAddress = getClientIp(httpServletRequest);
        String deviceInfo = httpServletRequest.getHeader("User-Agent");
        if (deviceInfo == null) {
            deviceInfo = "Unknown Device";
        }

        LoginResponse response = authService.login(request, ipAddress, deviceInfo);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/2fa/setup")
    public ResponseEntity<Setup2FAResponse> setup2FA(@AuthenticationPrincipal User user) {
        Setup2FAResponse response = authService.setup2FA(user);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<String> verify2FA(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody Verify2FARequest request) {
        authService.verify2FA(user, request.getCode());
        return ResponseEntity.ok("2FA verified and enabled successfully");
    }

    @PostMapping("/2fa/validate")
    public ResponseEntity<LoginResponse> validate2FA(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest) {
        // Validation during login is handled by passing credentials + OTP code
        String ipAddress = getClientIp(httpServletRequest);
        String deviceInfo = httpServletRequest.getHeader("User-Agent");
        if (deviceInfo == null) {
            deviceInfo = "Unknown Device";
        }

        LoginResponse response = authService.login(request, ipAddress, deviceInfo);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenRefreshResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        TokenRefreshResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        authService.logout(authHeader);
        return ResponseEntity.ok("Logged out successfully");
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
