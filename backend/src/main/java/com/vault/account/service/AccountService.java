package com.vault.account.service;

import com.vault.account.dto.CreateAccountRequest;
import com.vault.account.entity.Account;
import com.vault.account.entity.AccountStatus;
import com.vault.account.entity.AccountType;
import com.vault.account.repository.AccountRepository;
import com.vault.auth.entity.User;
import com.vault.auth.entity.UserRole;
import com.vault.transaction.entity.Transaction;
import com.vault.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String BALANCE_CACHE_KEY_PREFIX = "account_balance:";
    private static final long CACHE_TTL_SECONDS = 60;

    @Transactional
    public Account createAccount(User user, CreateAccountRequest request) {
        String accountNumber = generateUniqueAccountNumber();

        BigDecimal dailyLimit = BigDecimal.ZERO;
        BigDecimal interestRate = BigDecimal.ZERO;
        LocalDate maturityDate = null;

        if (request.getAccountType() == AccountType.SAVINGS) {
            dailyLimit = new BigDecimal("50000.0000");
            interestRate = new BigDecimal("3.50");
        } else if (request.getAccountType() == AccountType.CURRENT) {
            dailyLimit = new BigDecimal("200000.0000");
            interestRate = BigDecimal.ZERO;
        } else if (request.getAccountType() == AccountType.FIXED_DEPOSIT) {
            dailyLimit = BigDecimal.ZERO; // No transfers allowed directly
            interestRate = new BigDecimal("7.00");
            int months = request.getFixedDepositMaturityMonths() != null ? request.getFixedDepositMaturityMonths() : 12;
            maturityDate = LocalDate.now().plusMonths(months);
        }

        Account account = Account.builder()
                .user(user)
                .accountNumber(accountNumber)
                .accountType(request.getAccountType())
                .balance(request.getInitialDeposit())
                .dailyTransferLimit(dailyLimit)
                .interestRate(interestRate)
                .maturityDate(maturityDate)
                .kycDocReference(request.getKycDocReference())
                .status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        Account savedAccount = accountRepository.save(account);
        invalidateBalanceCache(savedAccount.getId());
        return savedAccount;
    }

    public List<Account> getMyAccounts(User user) {
        return accountRepository.findByUserId(user.getId());
    }

    public Account getAccountById(UUID id, User user) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        if (!user.getRole().equals(UserRole.ADMIN) && !account.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Access denied to view this account");
        }
        return account;
    }

    public BigDecimal getCachedBalance(UUID accountId, User user) {
        // Enforce owner check
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        if (!user.getRole().equals(UserRole.ADMIN) && !account.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Access denied to view balance of this account");
        }

        String cacheKey = BALANCE_CACHE_KEY_PREFIX + accountId.toString();
        String cachedVal = redisTemplate.opsForValue().get(cacheKey);

        if (cachedVal != null) {
            log.info("Balance Cache Hit for accountId: {}", accountId);
            return new BigDecimal(cachedVal);
        }

        log.info("Balance Cache Miss for accountId: {}. Fetching from DB", accountId);
        BigDecimal balance = account.getBalance();
        
        // Save to cache
        redisTemplate.opsForValue().set(cacheKey, balance.toString(), CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        return balance;
    }

    public void invalidateBalanceCache(UUID accountId) {
        String cacheKey = BALANCE_CACHE_KEY_PREFIX + accountId.toString();
        redisTemplate.delete(cacheKey);
        log.info("Invalidated balance cache for accountId: {}", accountId);
    }

    private String generateUniqueAccountNumber() {
        Random random = new Random();
        String accNum;
        do {
            // Generate a 12-digit number starting with '1000'
            long number = 100000000000L + (long)(random.nextDouble() * 899999999999L);
            accNum = String.valueOf(number);
        } while (accountRepository.findByAccountNumber(accNum).isPresent());
        return accNum;
    }

    public List<Transaction> getMiniStatement(UUID accountId, User user) {
        Account account = getAccountById(accountId, user);
        return transactionRepository.findMiniStatement(account, PageRequest.of(0, 10));
    }
}
