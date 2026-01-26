package com.vault.account.dto;

import com.vault.account.entity.Account;
import com.vault.account.entity.AccountStatus;
import com.vault.account.entity.AccountType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AccountResponse {
    private UUID id;
    private UUID userId;
    private String accountNumber; // Masked representation
    private AccountType accountType;
    private BigDecimal balance;
    private String currency;
    private BigDecimal dailyTransferLimit;
    private AccountStatus status;
    private BigDecimal interestRate;
    private LocalDate maturityDate;
    private String kycDocReference;
    private LocalDateTime createdAt;

    public static AccountResponse fromEntity(Account account) {
        if (account == null) return null;
        return AccountResponse.builder()
                .id(account.getId())
                .userId(account.getUser().getId())
                .accountNumber(maskAccountNumber(account.getAccountNumber()))
                .accountType(account.getAccountType())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .dailyTransferLimit(account.getDailyTransferLimit())
                .status(account.getStatus())
                .interestRate(account.getInterestRate())
                .maturityDate(account.getMaturityDate())
                .kycDocReference(account.getKycDocReference())
                .createdAt(account.getCreatedAt())
                .build();
    }

    private static String maskAccountNumber(String rawAccountNumber) {
        if (rawAccountNumber == null) return null;
        if (rawAccountNumber.length() <= 4) {
            return "****";
        }
        return "XXXX XXXX XXXX " + rawAccountNumber.substring(rawAccountNumber.length() - 4);
    }
}
