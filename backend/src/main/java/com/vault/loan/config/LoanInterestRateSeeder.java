package com.vault.loan.config;

import com.vault.loan.entity.LoanInterestRate;
import com.vault.loan.entity.LoanType;
import com.vault.loan.repository.LoanInterestRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoanInterestRateSeeder implements CommandLineRunner {

    private final LoanInterestRateRepository repository;

    @Override
    public void run(String... args) {
        for (LoanType type : LoanType.values()) {
            if (!repository.existsById(type)) {
                BigDecimal defaultRate = switch (type) {
                    case PERSONAL -> BigDecimal.valueOf(12.50);
                    case HOME -> BigDecimal.valueOf(8.50);
                    case CAR -> BigDecimal.valueOf(9.50);
                };
                repository.save(LoanInterestRate.builder()
                        .loanType(type)
                        .interestRate(defaultRate)
                        .build());
                log.info("Seeded default interest rate for {} ({}%)", type, defaultRate);
            }
        }
    }
}
