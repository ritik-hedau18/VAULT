package com.vault.loan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmiScheduleResponse {
    private int installmentNo;
    private BigDecimal emi;
    private BigDecimal interest;
    private BigDecimal principalPaid;
    private BigDecimal remainingBalance;
    private LocalDate dueDate;
}
