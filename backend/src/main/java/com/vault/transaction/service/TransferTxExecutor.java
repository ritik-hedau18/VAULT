package com.vault.transaction.service;

import com.vault.account.entity.Account;
import com.vault.account.entity.AccountStatus;
import com.vault.account.repository.AccountRepository;
import com.vault.exception.InsufficientBalanceException;
import com.vault.transaction.dto.TransferRequest;
import com.vault.transaction.entity.Transaction;
import com.vault.transaction.entity.TransactionStatus;
import com.vault.transaction.entity.TransactionType;
import com.vault.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferTxExecutor {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Transaction executeTransferTx(Account unLockedFromAcc, TransferRequest request, String idempotencyKey) {
        BigDecimal amount = request.getAmount().setScale(4, RoundingMode.HALF_UP);
        Account fromAccount;
        Account toAccount = null;

        String refNum = "TXN-" + UUID.randomUUID().toString().substring(0, 18).toUpperCase();

        if (request.isInterBank()) {
            // Interbank: Lock fromAccount only
            fromAccount = accountRepository.findByIdForUpdate(unLockedFromAcc.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Source account not found"));

            if (fromAccount.getBalance().compareTo(amount) < 0) {
                throw new InsufficientBalanceException("Insufficient balance");
            }

            // Debit fromAccount
            fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
            accountRepository.save(fromAccount);

            // Create pending interbank transaction
            Transaction transaction = Transaction.builder()
                    .referenceNumber(refNum)
                    .idempotencyKey(idempotencyKey)
                    .fromAccount(fromAccount)
                    .toAccount(null)
                    .amount(amount)
                    .type(TransactionType.TRANSFER)
                    .status(TransactionStatus.PENDING)
                    .description("Inter-bank to " + request.getToAccountNumber() + " (Bank: " + request.getBankCode() + "): " + request.getDescription())
                    .initiatedAt(LocalDateTime.now())
                    .build();

            return transactionRepository.save(transaction);
        } else {
            // Same bank: Lock both accounts to prevent deadlocks (smaller UUID locked first)
            Account targetAcc = accountRepository.findByAccountNumber(request.getToAccountNumber())
                    .orElseThrow(() -> new IllegalArgumentException("Destination account not found in this bank"));

            if (targetAcc.getStatus() == AccountStatus.CLOSED) {
                throw new IllegalArgumentException("Destination account is closed");
            }

            UUID firstId = unLockedFromAcc.getId();
            UUID secondId = targetAcc.getId();
            if (firstId.compareTo(secondId) < 0) {
                fromAccount = accountRepository.findByIdForUpdate(firstId)
                        .orElseThrow(() -> new IllegalArgumentException("Source account not found"));
                toAccount = accountRepository.findByIdForUpdate(secondId)
                        .orElseThrow(() -> new IllegalArgumentException("Destination account not found"));
            } else {
                toAccount = accountRepository.findByIdForUpdate(secondId)
                        .orElseThrow(() -> new IllegalArgumentException("Destination account not found"));
                fromAccount = accountRepository.findByIdForUpdate(firstId)
                        .orElseThrow(() -> new IllegalArgumentException("Source account not found"));
            }

            if (fromAccount.getBalance().compareTo(amount) < 0) {
                throw new InsufficientBalanceException("Insufficient balance");
            }

            // Debit & Credit
            fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
            toAccount.setBalance(toAccount.getBalance().add(amount));

            accountRepository.save(fromAccount);
            accountRepository.save(toAccount);

            Transaction transaction = Transaction.builder()
                    .referenceNumber(refNum)
                    .idempotencyKey(idempotencyKey)
                    .fromAccount(fromAccount)
                    .toAccount(toAccount)
                    .amount(amount)
                    .type(TransactionType.TRANSFER)
                    .status(TransactionStatus.SUCCESS)
                    .description("Transfer: " + request.getDescription())
                    .initiatedAt(LocalDateTime.now())
                    .completedAt(LocalDateTime.now())
                    .build();

            return transactionRepository.save(transaction);
        }
    }
}
