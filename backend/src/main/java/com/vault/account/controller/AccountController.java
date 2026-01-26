package com.vault.account.controller;

import com.vault.account.dto.AccountResponse;
import com.vault.account.dto.CreateAccountRequest;
import com.vault.account.entity.Account;
import com.vault.account.service.AccountService;
import com.vault.auth.entity.User;
import com.vault.transaction.dto.TransactionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateAccountRequest request) {
        Account account = accountService.createAccount(user, request);
        return new ResponseEntity<>(AccountResponse.fromEntity(account), HttpStatus.CREATED);
    }

    @GetMapping("/my")
    public ResponseEntity<List<AccountResponse>> getMyAccounts(@AuthenticationPrincipal User user) {
        List<AccountResponse> accounts = accountService.getMyAccounts(user).stream()
                .map(AccountResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccountById(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        Account account = accountService.getAccountById(id, user);
        return ResponseEntity.ok(AccountResponse.fromEntity(account));
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<Map<String, Object>> getBalance(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        BigDecimal balance = accountService.getCachedBalance(id, user);
        Map<String, Object> response = new HashMap<>();
        response.put("accountId", id);
        response.put("balance", balance);
        response.put("currency", "INR");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/mini-statement")
    public ResponseEntity<List<TransactionResponse>> getMiniStatement(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        List<TransactionResponse> statement = accountService.getMiniStatement(id, user).stream()
                .map(TransactionResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(statement);
    }
}
