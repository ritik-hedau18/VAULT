package com.vault.loan.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "loan_interest_rates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanInterestRate {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "loan_type", nullable = false)
    private LoanType loanType;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;
}
