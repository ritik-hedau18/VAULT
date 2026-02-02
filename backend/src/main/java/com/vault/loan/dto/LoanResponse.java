package com.vault.loan.dto;

import com.vault.loan.entity.Loan;
import com.vault.loan.entity.LoanStatus;
import com.vault.loan.entity.LoanType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class LoanResponse {
    private UUID id;
    private UUID userId;
    private UUID accountId;
    private String accountNumber; // Masked representation of associated account
    private LoanType loanType;
    private BigDecimal principal;
    private BigDecimal interestRate;
    private Integer tenureMonths;
    private BigDecimal emiAmount;
    private BigDecimal outstandingAmount;
    private LoanStatus status;
    private LocalDate nextDueDate;
    private LocalDateTime createdAt;

    public static LoanResponse fromEntity(Loan loan) {
        if (loan == null) return null;
        return LoanResponse.builder()
                .id(loan.getId())
                .userId(loan.getUser().getId())
                .accountId(loan.getAccount().getId())
                .accountNumber(maskAccountNumber(loan.getAccount().getAccountNumber()))
                .loanType(loan.getLoanType())
                .principal(loan.getPrincipal())
                .interestRate(loan.getInterestRate())
                .tenureMonths(loan.getTenureMonths())
                .emiAmount(loan.getEmiAmount())
                .outstandingAmount(loan.getOutstandingAmount())
                .status(loan.getStatus())
                .nextDueDate(loan.getNextDueDate())
                .createdAt(loan.getCreatedAt())
                .build();
    }

    private static String maskAccountNumber(String rawAccountNumber) {
        if (rawAccountNumber == null) return null;
        if (rawAccountNumber.length() <= 4) return "****";
        return "XXXX XXXX XXXX " + rawAccountNumber.substring(rawAccountNumber.length() - 4);
    }
}
