package com.vault.loan.dto;

import com.vault.loan.entity.LoanType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateInterestRateRequest {

    @NotNull(message = "Loan type is required")
    private LoanType loanType;

    @NotNull(message = "Interest rate is required")
    @DecimalMin(value = "1.00", message = "Interest rate must be at least 1.00%")
    private BigDecimal interestRate;
}
