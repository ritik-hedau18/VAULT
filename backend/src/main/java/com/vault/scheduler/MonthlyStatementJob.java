package com.vault.scheduler;

import com.vault.account.entity.Account;
import com.vault.account.repository.AccountRepository;
import com.vault.notification.NotificationService;
import com.vault.statement.entity.MonthlyStatement;
import com.vault.statement.repository.MonthlyStatementRepository;
import com.vault.transaction.entity.Transaction;
import com.vault.transaction.entity.TransactionStatus;
import com.vault.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MonthlyStatementJob extends QuartzJobBean {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final MonthlyStatementRepository statementRepository;
    private final NotificationService notificationService;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        log.info("Executing Monthly Statement Compilation Job...");

        // Determine last month
        LocalDate today = LocalDate.now();
        YearMonth lastMonthYearMonth = YearMonth.from(today).minusMonths(1);
        String monthString = lastMonthYearMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        LocalDate startDay = lastMonthYearMonth.atDay(1);
        LocalDate endDay = lastMonthYearMonth.atEndOfMonth();

        LocalDateTime rangeStart = LocalDateTime.of(startDay, LocalTime.MIDNIGHT);
        LocalDateTime rangeEnd = LocalDateTime.of(endDay, LocalTime.MAX);

        List<Account> accounts = accountRepository.findAll();

        for (Account account : accounts) {
            try {
                // Fetch transactions for this account during last month
                List<Transaction> txns = transactionRepository.findAll().stream()
                        .filter(t -> t.getStatus() == TransactionStatus.SUCCESS)
                        .filter(t -> (t.getFromAccount() != null && t.getFromAccount().getId().equals(account.getId())) ||
                                     (t.getToAccount() != null && t.getToAccount().getId().equals(account.getId())))
                        .filter(t -> t.getInitiatedAt().isAfter(rangeStart) && t.getInitiatedAt().isBefore(rangeEnd))
                        .toList();

                BigDecimal totalCredits = BigDecimal.ZERO;
                BigDecimal totalDebits = BigDecimal.ZERO;
                List<MonthlyStatement.TransactionItem> items = new ArrayList<>();

                for (Transaction txn : txns) {
                    boolean isCredit = txn.getToAccount() != null && txn.getToAccount().getId().equals(account.getId());
                    if (isCredit) {
                        totalCredits = totalCredits.add(txn.getAmount());
                    } else {
                        totalDebits = totalDebits.add(txn.getAmount());
                    }

                    items.add(MonthlyStatement.TransactionItem.builder()
                            .transactionId(txn.getId().toString())
                            .amount(txn.getAmount().toString())
                            .type(txn.getType().name() + (isCredit ? "_CREDIT" : "_DEBIT"))
                            .date(txn.getInitiatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                            .description(txn.getDescription())
                            .build());
                }

                BigDecimal closingBalance = account.getBalance();
                // Opening Balance = Closing - Credits + Debits
                BigDecimal openingBalance = closingBalance.subtract(totalCredits).add(totalDebits);

                MonthlyStatement statement = MonthlyStatement.builder()
                        .accountId(account.getId())
                        .userId(account.getUser().getId())
                        .month(monthString)
                        .openingBalance(openingBalance.toString())
                        .closingBalance(closingBalance.toString())
                        .totalCredits(totalCredits.toString())
                        .totalDebits(totalDebits.toString())
                        .transactions(items)
                        .generatedAt(LocalDateTime.now())
                        .build();

                statementRepository.save(statement);
                log.info("Saved monthly statement for accountId: {}, month: {}", account.getId(), monthString);

                // Notify User
                String subject = "VAULT - Monthly Statement Available (" + monthString + ")";
                String body = String.format(
                        "Dear %s,\n\n" +
                        "Your monthly banking statement for %s is now available for account ending in %s.\n\n" +
                        "Opening Balance: INR %s\n" +
                        "Total Credits: INR %s\n" +
                        "Total Debits: INR %s\n" +
                        "Closing Balance: INR %s\n\n" +
                        "Please login to the VAULT dashboard to view and download your full transaction statement.\n\n" +
                        "Regards,\nVAULT Notifications Service",
                        account.getUser().getFullName(), monthString,
                        account.getAccountNumber().substring(account.getAccountNumber().length() - 4),
                        openingBalance, totalCredits, totalDebits, closingBalance
                );

                notificationService.sendEmailNotification(account.getUser(), subject, body);

            } catch (Exception e) {
                log.error("Failed to generate statement for account: {}", account.getId(), e);
            }
        }
        log.info("Completed Monthly Statement Compilation Job");
    }
}
