package com.vault.transaction.dto;

import com.vault.transaction.entity.Transaction;
import com.vault.transaction.entity.TransactionStatus;
import com.vault.transaction.entity.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TransactionResponse {
    private UUID id;
    private String referenceNumber;
    private BigDecimal amount;
    private TransactionType type;
    private TransactionStatus status;
    private String description;
    private LocalDateTime initiatedAt;
    private LocalDateTime completedAt;
    private String fromAccountNumber; // Masked
    private String toAccountNumber;   // Masked

    public static TransactionResponse fromEntity(Transaction transaction) {
        if (transaction == null) return null;
        return TransactionResponse.builder()
                .id(transaction.getId())
                .referenceNumber(transaction.getReferenceNumber())
                .amount(transaction.getAmount())
                .type(transaction.getType())
                .status(transaction.getStatus())
                .description(transaction.getDescription())
                .initiatedAt(transaction.getInitiatedAt())
                .completedAt(transaction.getCompletedAt())
                .fromAccountNumber(transaction.getFromAccount() != null ? mask(transaction.getFromAccount().getAccountNumber()) : null)
                .toAccountNumber(transaction.getToAccount() != null ? mask(transaction.getToAccount().getAccountNumber()) : null)
                .build();
    }

    private static String mask(String accNo) {
        if (accNo == null) return null;
        if (accNo.length() <= 4) return "****";
        return "XXXX XXXX XXXX " + accNo.substring(accNo.length() - 4);
    }
}
