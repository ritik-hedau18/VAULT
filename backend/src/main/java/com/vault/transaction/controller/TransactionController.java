package com.vault.transaction.controller;

import com.vault.account.entity.Account;
import com.vault.account.service.AccountService;
import com.vault.auth.entity.User;
import com.vault.transaction.dto.DepositRequest;
import com.vault.transaction.dto.TransactionResponse;
import com.vault.transaction.dto.TransferRequest;
import com.vault.transaction.dto.WithdrawRequest;
import com.vault.transaction.entity.Transaction;
import com.vault.transaction.repository.TransactionRepository;
import com.vault.transaction.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransferService transferService;
    private final AccountService accountService;
    private final TransactionRepository transactionRepository;

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @AuthenticationPrincipal User user,
            @RequestHeader("idempotency-key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        
        TransactionResponse response = transferService.transfer(user, request, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @AuthenticationPrincipal User user,
            @RequestHeader("idempotency-key") String idempotencyKey,
            @Valid @RequestBody DepositRequest request) {
        
        TransactionResponse response = transferService.deposit(user, request, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(
            @AuthenticationPrincipal User user,
            @RequestHeader("idempotency-key") String idempotencyKey,
            @Valid @RequestBody WithdrawRequest request) {
        
        TransactionResponse response = transferService.withdraw(user, request, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    public ResponseEntity<Page<TransactionResponse>> getHistory(
            @AuthenticationPrincipal User user,
            @RequestParam UUID accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        // Validate ownership first
        Account account = accountService.getAccountById(accountId, user);
        
        Page<TransactionResponse> history = transactionRepository.findTransactionHistory(
                account,
                PageRequest.of(page, size, Sort.by("initiatedAt").descending())
        ).map(TransactionResponse::fromEntity);
        
        return ResponseEntity.ok(history);
    }

    @GetMapping("/{refNo}")
    public ResponseEntity<TransactionResponse> getByReferenceNumber(
            @AuthenticationPrincipal User user,
            @PathVariable String refNo) {
        
        Transaction transaction = transactionRepository.findByReferenceNumber(refNo)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        
        // Ownership check: user must be admin or own either the fromAccount or toAccount
        boolean isAdmin = user.getRole().name().equals("ADMIN");
        boolean ownsFrom = transaction.getFromAccount() != null && transaction.getFromAccount().getUser().getId().equals(user.getId());
        boolean ownsTo = transaction.getToAccount() != null && transaction.getToAccount().getUser().getId().equals(user.getId());
        
        if (!isAdmin && !ownsFrom && !ownsTo) {
            throw new AccessDeniedException("Access denied to view this transaction");
        }
        
        return ResponseEntity.ok(TransactionResponse.fromEntity(transaction));
    }

    @GetMapping("/today-usage")
    public ResponseEntity<java.util.Map<String, Object>> getTodayUsage(
            @AuthenticationPrincipal User user,
            @RequestParam UUID accountId) {
        
        Account account = accountService.getAccountById(accountId, user);
        java.time.LocalDateTime startOfToday = java.time.LocalDate.now().atStartOfDay();
        java.math.BigDecimal usage = transactionRepository.sumSuccessfulTransfersToday(account, startOfToday);
        if (usage == null) {
            usage = java.math.BigDecimal.ZERO;
        }
        
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("usage", usage);
        return ResponseEntity.ok(response);
    }
}
