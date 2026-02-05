package com.vault.loan.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class LoanServiceTest {

    @InjectMocks
    private LoanService loanService;

    @Test
    public void testEmiCalculation_PersonalLoan() {
        // Principal: 100,000 INR
        // Interest Rate: 12% p.a.
        // Tenure: 12 months
        // Expected Monthly EMI: 8,884.8789
        BigDecimal principal = new BigDecimal("100000.0000");
        BigDecimal annualRate = new BigDecimal("12.00");
        int tenureMonths = 12;

        BigDecimal emi = loanService.calculateEMI(principal, annualRate, tenureMonths);

        // EMI Formula calculation:
        // r = 12 / 12 / 100 = 0.01
        // EMI = 100,000 * 0.01 * (1.01)^12 / ((1.01)^12 - 1)
        // (1.01)^12 = 1.12682503
        // EMI = 1000 * 1.12682503 / 0.12682503 = 8884.8789
        BigDecimal expected = new BigDecimal("8884.8789").setScale(4, RoundingMode.HALF_UP);
        
        assertEquals(expected, emi);
    }

    @Test
    public void testEmiCalculation_HomeLoan() {
        // Principal: 5,000,000 INR
        // Interest Rate: 8.5% p.a.
        // Tenure: 240 months (20 years)
        // Expected Monthly EMI: 43,391.1764
        BigDecimal principal = new BigDecimal("5000000.0000");
        BigDecimal annualRate = new BigDecimal("8.50");
        int tenureMonths = 240;

        BigDecimal emi = loanService.calculateEMI(principal, annualRate, tenureMonths);

        BigDecimal expected = new BigDecimal("43391.1617").setScale(4, RoundingMode.HALF_UP);
        
        assertEquals(expected, emi);
    }

    @Test
    public void testEmiCalculation_ZeroInterest() {
        // Principal: 120,000 INR
        // Interest Rate: 0% p.a.
        // Tenure: 12 months
        // Expected Monthly EMI: 10,000.0000
        BigDecimal principal = new BigDecimal("120000.0000");
        BigDecimal annualRate = BigDecimal.ZERO;
        int tenureMonths = 12;

        BigDecimal emi = loanService.calculateEMI(principal, annualRate, tenureMonths);

        BigDecimal expected = new BigDecimal("10000.0000").setScale(4, RoundingMode.HALF_UP);
        
        assertEquals(expected, emi);
    }
}
