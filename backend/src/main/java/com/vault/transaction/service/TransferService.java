package com.vault.transaction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vault.account.entity.Account;
import com.vault.account.entity.AccountStatus;
import com.vault.account.entity.AccountType;
import com.vault.account.repository.AccountRepository;
import com.vault.account.service.AccountService;
import com.vault.auth.entity.User;
import com.vault.exception.AccountFrozenException;
import com.vault.exception.InsufficientBalanceException;
import com.vault.exception.InvalidTransactionPinException;
import com.vault.exception.TwoFactorAuthenticationException;
import com.vault.security.TotpUtil;
import com.vault.transaction.TransactionEvent;
import com.vault.transaction.dto.DepositRequest;
import com.vault.transaction.dto.TransactionResponse;
import com.vault.transaction.dto.TransferRequest;
import com.vault.transaction.dto.WithdrawRequest;
import com.vault.transaction.entity.Transaction;
import com.vault.transaction.entity.TransactionStatus;
import com.vault.transaction.entity.TransactionType;
import com.vault.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final PasswordEncoder passwordEncoder;
    private final TotpUtil totpUtil;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final TransferTxExecutor transferTxExecutor;

    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final String DISTRIBUTED_LOCK_PREFIX = "transfer_lock:";
    private static final long LOCK_TTL_SECONDS = 10;
    
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Transactional(readOnly = true)
    public TransactionResponse checkIdempotency(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return null;
        }
        String key = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        String val = redisTemplate.opsForValue().get(key);
        if (val != null) {
            try {
                log.info("Idempotency hit for key: {}", idempotencyKey);
                return objectMapper.readValue(val, TransactionResponse.class);
            } catch (Exception e) {
                log.error("Failed to deserialize idempotency response", e);
            }
        }
        return null;
    }

    private void cacheIdempotency(String idempotencyKey, TransactionResponse response) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return;
        }
        String key = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        try {
            String val = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, val, 24, TimeUnit.HOURS);
            log.info("Cached idempotency result for key: {}", idempotencyKey);
        } catch (Exception e) {
            log.error("Failed to serialize idempotency response", e);
        }
    }

    public TransactionResponse transfer(User user, TransferRequest request, String idempotencyKey) {
        // 1. Check Idempotency
        TransactionResponse cached = checkIdempotency(idempotencyKey);
        if (cached != null) return cached;

        // 2. Validate PIN
        if (!passwordEncoder.matches(request.getTransactionPin(), user.getTransactionPinHash())) {
            throw new InvalidTransactionPinException("Incorrect transaction PIN");
        }

        // 3. Verify 2FA
        if (user.isTwoFaEnabled()) {
            if (request.getTotpCode() == null || !totpUtil.verifyCode(user.getTotpSecret(), request.getTotpCode())) {
                throw new TwoFactorAuthenticationException("Invalid or missing 2FA code");
            }
        }

        // 4. Fetch accounts (un-locked for metadata verification first)
        Account fromAccount = accountRepository.findById(request.getFromAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Source account not found"));

        if (!fromAccount.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Source account does not belong to user");
        }

        if (fromAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountFrozenException("Source account is " + fromAccount.getStatus());
        }

        if (fromAccount.getAccountType() == AccountType.FIXED_DEPOSIT) {
            throw new IllegalArgumentException("Cannot transfer funds directly from a Fixed Deposit account");
        }

        // 5. Check daily transfer limit
        LocalDateTime startOfToday = LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT);
        BigDecimal todayTransfersSum = transactionRepository.sumSuccessfulTransfersToday(fromAccount, startOfToday);
        if (todayTransfersSum == null) {
            todayTransfersSum = BigDecimal.ZERO;
        }
        BigDecimal remainingLimit = fromAccount.getDailyTransferLimit().subtract(todayTransfersSum);
        if (remainingLimit.compareTo(request.getAmount()) < 0) {
            throw new IllegalArgumentException("Daily transfer limit exceeded. Remaining limit for today is INR " + remainingLimit);
        }

        // 6. Redis distributed lock on sender account
        String lockKey = DISTRIBUTED_LOCK_PREFIX + fromAccount.getId().toString();
        String lockValue = UUID.randomUUID().toString();
        boolean lockAcquired = Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, LOCK_TTL_SECONDS, TimeUnit.SECONDS)
        );

        if (!lockAcquired) {
            throw new RuntimeException("Another transaction is currently processing on this account. Please try again.");
        }

        try {
            // 7. Execute transactional balance update
            Transaction transaction = transferTxExecutor.executeTransferTx(fromAccount, request, idempotencyKey);

            TransactionResponse response = TransactionResponse.fromEntity(transaction);

            // 8. Cache result for idempotency
            cacheIdempotency(idempotencyKey, response);

            // 9. Invalidate Balance Caches
            accountService.invalidateBalanceCache(fromAccount.getId());
            if (transaction.getToAccount() != null) {
                accountService.invalidateBalanceCache(transaction.getToAccount().getId());
            }

            // 10. Publish Application Event
            eventPublisher.publishEvent(new TransactionEvent(this, transaction, user.getId()));

            return response;
        } finally {
            // Release lock
            String currentLockVal = redisTemplate.opsForValue().get(lockKey);
            if (lockValue.equals(currentLockVal)) {
                redisTemplate.delete(lockKey);
            }
        }
    }


    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public TransactionResponse deposit(User user, DepositRequest request, String idempotencyKey) {
        TransactionResponse cached = checkIdempotency(idempotencyKey);
        if (cached != null) return cached;

        BigDecimal amount = request.getAmount().setScale(4, RoundingMode.HALF_UP);
        Account account = accountRepository.findByIdForUpdate(request.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        if (!account.getUser().getId().equals(user.getId()) && !user.getRole().name().equals("ADMIN")) {
            throw new IllegalArgumentException("Account does not belong to user");
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountFrozenException("Account is frozen or closed");
        }

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        String refNum = "DEP-" + UUID.randomUUID().toString().substring(0, 18).toUpperCase();
        Transaction transaction = Transaction.builder()
                .referenceNumber(refNum)
                .idempotencyKey(idempotencyKey)
                .toAccount(account)
                .amount(amount)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.SUCCESS)
                .description("Deposit: " + request.getDescription())
                .initiatedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();

        transactionRepository.save(transaction);

        TransactionResponse response = TransactionResponse.fromEntity(transaction);
        cacheIdempotency(idempotencyKey, response);
        accountService.invalidateBalanceCache(account.getId());
        eventPublisher.publishEvent(new TransactionEvent(this, transaction, user.getId()));

        return response;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public TransactionResponse withdraw(User user, WithdrawRequest request, String idempotencyKey) {
        TransactionResponse cached = checkIdempotency(idempotencyKey);
        if (cached != null) return cached;

        if (!passwordEncoder.matches(request.getTransactionPin(), user.getTransactionPinHash())) {
            throw new InvalidTransactionPinException("Incorrect transaction PIN");
        }

        BigDecimal amount = request.getAmount().setScale(4, RoundingMode.HALF_UP);
        Account account = accountRepository.findByIdForUpdate(request.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Account does not belong to user");
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountFrozenException("Account is frozen or closed");
        }

        if (account.getAccountType() == AccountType.FIXED_DEPOSIT) {
            throw new IllegalArgumentException("Cannot withdraw directly from Fixed Deposit account before maturity");
        }

        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance");
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        String refNum = "WTH-" + UUID.randomUUID().toString().substring(0, 18).toUpperCase();
        Transaction transaction = Transaction.builder()
                .referenceNumber(refNum)
                .idempotencyKey(idempotencyKey)
                .fromAccount(account)
                .amount(amount)
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.SUCCESS)
                .description("Withdrawal: " + request.getDescription())
                .initiatedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();

        transactionRepository.save(transaction);

        TransactionResponse response = TransactionResponse.fromEntity(transaction);
        cacheIdempotency(idempotencyKey, response);
        accountService.invalidateBalanceCache(account.getId());
        eventPublisher.publishEvent(new TransactionEvent(this, transaction, user.getId()));

        return response;
    }
}
