package com.vault.admin.controller;

import com.vault.account.dto.AccountResponse;
import com.vault.account.entity.Account;
import com.vault.account.entity.AccountStatus;
import com.vault.account.repository.AccountRepository;
import com.vault.account.service.AccountService;
import com.vault.auth.entity.User;
import com.vault.auth.repository.UserRepository;
import com.vault.loan.dto.LoanResponse;
import com.vault.loan.dto.UpdateInterestRateRequest;
import com.vault.loan.entity.Loan;
import com.vault.loan.entity.LoanInterestRate;
import com.vault.loan.service.LoanService;
import jakarta.validation.Valid;
import com.vault.transaction.dto.TransactionResponse;
import com.vault.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LoanService loanService;
    private final AccountService accountService;

    @GetMapping("/users")
    public ResponseEntity<Page<User>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<User> users = userRepository.findAll(PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(users);
    }

    @PutMapping("/accounts/{identifier}/freeze")
    @Transactional
    public ResponseEntity<AccountResponse> freezeAccount(@PathVariable String identifier) {
        Account account = findAccountByIdentifier(identifier);
        account.setStatus(AccountStatus.FROZEN);
        Account saved = accountRepository.save(account);
        accountService.invalidateBalanceCache(saved.getId());
        return ResponseEntity.ok(AccountResponse.fromEntity(saved));
    }

    @PutMapping("/accounts/{identifier}/unfreeze")
    @Transactional
    public ResponseEntity<AccountResponse> unfreezeAccount(@PathVariable String identifier) {
        Account account = findAccountByIdentifier(identifier);
        account.setStatus(AccountStatus.ACTIVE);
        Account saved = accountRepository.save(account);
        accountService.invalidateBalanceCache(saved.getId());
        return ResponseEntity.ok(AccountResponse.fromEntity(saved));
    }

    private Account findAccountByIdentifier(String identifier) {
        try {
            UUID id = UUID.fromString(identifier);
            return accountRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Account not found with ID: " + identifier));
        } catch (IllegalArgumentException e) {
            return accountRepository.findByAccountNumber(identifier)
                    .orElseThrow(() -> new IllegalArgumentException("Account not found with Account Number: " + identifier));
        }
    }

    @GetMapping("/transactions")
    public ResponseEntity<Page<TransactionResponse>> getAllTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<TransactionResponse> txns = transactionRepository.findAll(
                PageRequest.of(page, size, Sort.by("initiatedAt").descending())
        ).map(TransactionResponse::fromEntity);
        return ResponseEntity.ok(txns);
    }

    @PostMapping("/loans/{id}/approve")
    public ResponseEntity<LoanResponse> approveLoan(@PathVariable UUID id) {
        Loan loan = loanService.approveLoan(id);
        return ResponseEntity.ok(LoanResponse.fromEntity(loan));
    }

    @PostMapping("/loans/{id}/reject")
    public ResponseEntity<LoanResponse> rejectLoan(@PathVariable UUID id) {
        Loan loan = loanService.rejectLoan(id);
        return ResponseEntity.ok(LoanResponse.fromEntity(loan));
    }

    @GetMapping("/loans")
    public ResponseEntity<java.util.List<LoanResponse>> getAllLoans() {
        java.util.List<LoanResponse> loans = loanService.getAllLoansForAdmin().stream()
                .map(LoanResponse::fromEntity)
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(loans);
    }

    @GetMapping("/dashboard/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        long totalUsers = userRepository.count();
        long totalAccounts = accountRepository.count();
        BigDecimal totalBalance = accountRepository.sumAllBalances();
        if (totalBalance == null) {
            totalBalance = BigDecimal.ZERO;
        }

        LocalDateTime startOfToday = LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT);
        BigDecimal todayVolume = transactionRepository.sumAllTransactionVolumeToday(startOfToday);
        if (todayVolume == null) {
            todayVolume = BigDecimal.ZERO;
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", totalUsers);
        stats.put("totalAccounts", totalAccounts);
        stats.put("totalBalance", totalBalance);
        stats.put("dailyTransactionVolume", todayVolume);

        return ResponseEntity.ok(stats);
    }

    @PutMapping("/loans/interest-rates")
    public ResponseEntity<LoanInterestRate> updateInterestRate(@Valid @RequestBody UpdateInterestRateRequest request) {
        LoanInterestRate updated = loanService.updateInterestRate(request.getLoanType(), request.getInterestRate());
        return ResponseEntity.ok(updated);
    }
}
