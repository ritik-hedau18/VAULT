package com.vault.scheduler;

import com.vault.account.entity.Account;
import com.vault.account.entity.AccountStatus;
import com.vault.account.entity.AccountType;
import com.vault.account.repository.AccountRepository;
import com.vault.account.service.AccountService;
import com.vault.transaction.entity.Transaction;
import com.vault.transaction.entity.TransactionStatus;
import com.vault.transaction.entity.TransactionType;
import com.vault.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DailyInterestJob extends QuartzJobBean {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AccountService accountService;

    @Override
    @Transactional
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        log.info("Executing Daily Interest Credit Job...");
        
        List<Account> savingsAccounts = accountRepository.findAll().stream()
                .filter(a -> a.getAccountType() == AccountType.SAVINGS && a.getStatus() == AccountStatus.ACTIVE)
                .toList();

        BigDecimal daysInYear = BigDecimal.valueOf(365L);
        BigDecimal percentDivisor = BigDecimal.valueOf(100L);

        for (Account account : savingsAccounts) {
            BigDecimal balance = account.getBalance();
            BigDecimal annualRate = account.getInterestRate();

            if (annualRate.compareTo(BigDecimal.ZERO) <= 0 || balance.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // Daily rate = annualRate / 365 / 100
            BigDecimal dailyRate = annualRate.divide(daysInYear, 8, RoundingMode.HALF_UP)
                    .divide(percentDivisor, 8, RoundingMode.HALF_UP);

            BigDecimal interest = balance.multiply(dailyRate).setScale(4, RoundingMode.HALF_UP);

            if (interest.compareTo(BigDecimal.ZERO) > 0) {
                account.setBalance(balance.add(interest));
                accountRepository.save(account);

                // Log interest transaction
                String refNum = "INT-" + UUID.randomUUID().toString().substring(0, 18).toUpperCase();
                Transaction transaction = Transaction.builder()
                        .referenceNumber(refNum)
                        .toAccount(account)
                        .amount(interest)
                        .type(TransactionType.DEPOSIT)
                        .status(TransactionStatus.SUCCESS)
                        .description("Daily Interest Credit at " + annualRate + "% p.a.")
                        .initiatedAt(LocalDateTime.now())
                        .completedAt(LocalDateTime.now())
                        .build();

                transactionRepository.save(transaction);
                accountService.invalidateBalanceCache(account.getId());
                
                log.info("Credited Daily Interest INR {} to account {}", interest, account.getId());
            }
        }
        log.info("Completed Daily Interest Credit Job");
    }
}
