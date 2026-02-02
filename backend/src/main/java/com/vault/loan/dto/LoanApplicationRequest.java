package com.vault.loan.dto;

import com.vault.loan.entity.LoanType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class LoanApplicationRequest {

    @NotNull(message = "Disbursement account ID is required")
    private UUID accountId;

    @NotNull(message = "Loan type is required")
    private LoanType loanType;

    @NotNull(message = "Principal amount is required")
    @DecimalMin(value = "1000.00", message = "Principal must be at least INR 1,000.00")
    private BigDecimal principal;

    @NotNull(message = "Annual interest rate is required")
    @DecimalMin(value = "1.00", message = "Interest rate must be at least 1.00%")
    private BigDecimal interestRate;

    @NotNull(message = "Tenure in months is required")
    @Min(value = 3, message = "Tenure must be at least 3 months")
    private Integer tenureMonths;
}
