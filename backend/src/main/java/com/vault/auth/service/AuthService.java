package com.vault.auth.service;

import com.vault.auth.dto.*;
import com.vault.auth.entity.RefreshToken;
import com.vault.auth.entity.User;
import com.vault.auth.entity.UserRole;
import com.vault.auth.entity.UserStatus;
import com.vault.auth.repository.RefreshTokenRepository;
import com.vault.auth.repository.UserRepository;
import com.vault.audit.LoginEvent;
import com.vault.exception.AccountLockedException;
import com.vault.exception.TwoFactorAuthenticationException;
import com.vault.security.JwtTokenProvider;
import com.vault.security.TotpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TotpUtil totpUtil;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    private static final String LOCKOUT_KEY_PREFIX = "login_attempts:";
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MINUTES = 30;
    private static final String OTP_KEY_PREFIX = "otp:";

    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new IllegalArgumentException("Phone number is already registered");
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .transactionPinHash(passwordEncoder.encode(request.getTransactionPin()))
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();

        return userRepository.save(user);
    }

    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress, String deviceInfo) {
        String email = request.getEmail();
        checkLockout(email);

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            incrementFailedAttempts(email);
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userOpt.get();

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new AccountLockedException("Account suspended. Please contact administrator.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            incrementFailedAttempts(email);
            // Double check: if it just triggered lockout, update DB
            int attempts = getFailedAttempts(email);
            if (attempts >= MAX_ATTEMPTS) {
                user.setStatus(UserStatus.LOCKED);
                userRepository.save(user);
                throw new AccountLockedException("Account locked due to 5 failed login attempts. Try again in 30 minutes.");
            }
            throw new BadCredentialsException("Invalid email or password");
        }

        // Check if account status is locked but Redis lock expired, or if DB status is locked but we are successful.
        // We will unlock on successful login
        if (user.getStatus() == UserStatus.LOCKED) {
            user.setStatus(UserStatus.ACTIVE);
            userRepository.save(user);
        }

        // Verify 2FA if enabled
        if (user.isTwoFaEnabled()) {
            if (request.getTotpCode() == null || request.getTotpCode().trim().isEmpty()) {
                // Return login response indicating 2FA required (no tokens returned)
                return LoginResponse.builder()
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .role(user.getRole().name())
                        .twoFaEnabled(true)
                        .twoFaRequired(true)
                        .build();
            }

            boolean isTotpValid = totpUtil.verifyCode(user.getTotpSecret(), request.getTotpCode());
            if (!isTotpValid) {
                incrementFailedAttempts(email);
                throw new TwoFactorAuthenticationException("Invalid 2FA code");
            }
        }

        // Authentication Success: Clear lockout counter
        clearFailedAttempts(email);

        // Generate Tokens
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshTokenStr = jwtTokenProvider.generateRefreshToken(user);

        // Save Refresh Token
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(refreshTokenStr)
                .deviceInfo(deviceInfo)
                .expiry(LocalDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(refreshToken);

        // Publish async success login event
        eventPublisher.publishEvent(new LoginEvent(this, user.getId(), user.getEmail(), "LOGIN", ipAddress, deviceInfo, "SUCCESS", new HashMap<>()));

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .twoFaEnabled(user.isTwoFaEnabled())
                .twoFaRequired(false)
                .build();
    }

    public Setup2FAResponse setup2FA(User user) {
        String secret = totpUtil.generateSecretKey();
        String qrCodeUrl = totpUtil.getQrCodeUrl(secret, user.getEmail());

        // Cache the secret in Redis for 5 minutes
        String redisKey = OTP_KEY_PREFIX + user.getId().toString();
        redisTemplate.opsForValue().set(redisKey, secret, 5, TimeUnit.MINUTES);

        return Setup2FAResponse.builder()
                .secretKey(secret)
                .qrCodeUrl(qrCodeUrl)
                .build();
    }

    @Transactional
    public void verify2FA(User user, String code) {
        String redisKey = OTP_KEY_PREFIX + user.getId().toString();
        String tempSecret = redisTemplate.opsForValue().get(redisKey);

        if (tempSecret == null) {
            throw new IllegalArgumentException("2FA setup session expired. Please initialize 2FA setup again.");
        }

        boolean isValid = totpUtil.verifyCode(tempSecret, code);
        if (!isValid) {
            throw new IllegalArgumentException("Invalid TOTP verification code. Setup failed.");
        }

        // Save secret to database and enable 2FA
        user.setTotpSecret(tempSecret);
        user.setTwoFaEnabled(true);
        userRepository.save(user);

        // Clean up Redis
        redisTemplate.delete(redisKey);
    }

    @Transactional
    public TokenRefreshResponse refresh(TokenRefreshRequest request) {
        String tokenStr = request.getRefreshToken();
        RefreshToken refreshToken = refreshTokenRepository.findByToken(tokenStr)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (refreshToken.isRevoked() || refreshToken.getExpiry().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Expired or revoked refresh token");
        }

        User user = refreshToken.getUser();

        // Rotate: Revoke the old refresh token
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        // Generate new set of tokens
        String newAccessToken = jwtTokenProvider.generateAccessToken(user);
        String newRefreshTokenStr = jwtTokenProvider.generateRefreshToken(user);

        // Save new refresh token
        RefreshToken newRefreshToken = RefreshToken.builder()
                .user(user)
                .token(newRefreshTokenStr)
                .deviceInfo(refreshToken.getDeviceInfo())
                .expiry(LocalDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(newRefreshToken);

        return TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshTokenStr)
                .build();
    }

    @Transactional
    public void logout(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }
        String jwt = authHeader.substring(7);
        try {
            String email = jwtTokenProvider.getUsernameFromToken(jwt);
            Optional<User> userOpt = userRepository.findByEmail(email);

            if (userOpt.isPresent()) {
                User user = userOpt.get();
                // Delete all refresh tokens for the user
                refreshTokenRepository.deleteByUser(user);
                
                // Add JWT to Redis Blacklist
                long exp = jwtTokenProvider.getExpirationDateFromToken(jwt).getTime();
                long now = System.currentTimeMillis();
                long remainingMs = Math.max(0, exp - now);
                
                redisTemplate.opsForValue().set("blacklist:token:" + jwt, "revoked", remainingMs, TimeUnit.MILLISECONDS);

                // Publish logout audit event
                eventPublisher.publishEvent(new LoginEvent(this, user.getId(), user.getEmail(), "LOGOUT", "N/A", "N/A", "SUCCESS", new HashMap<>()));
            }
        } catch (Exception e) {
            log.error("Failed to execute logout", e);
        }
    }

    // Lockout Helpers
    private void checkLockout(String email) {
        String key = LOCKOUT_KEY_PREFIX + email;
        String val = redisTemplate.opsForValue().get(key);
        if (val != null) {
            int attempts = Integer.parseInt(val);
            if (attempts >= MAX_ATTEMPTS) {
                throw new AccountLockedException("Account locked due to 5 failed login attempts. Try again in 30 minutes.");
            }
        }
    }

    private void incrementFailedAttempts(String email) {
        String key = LOCKOUT_KEY_PREFIX + email;
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, LOCKOUT_DURATION_MINUTES, TimeUnit.MINUTES);
        }
    }

    private int getFailedAttempts(String email) {
        String key = LOCKOUT_KEY_PREFIX + email;
        String val = redisTemplate.opsForValue().get(key);
        return val == null ? 0 : Integer.parseInt(val);
    }

    private void clearFailedAttempts(String email) {
        String key = LOCKOUT_KEY_PREFIX + email;
        redisTemplate.delete(key);
    }
}
